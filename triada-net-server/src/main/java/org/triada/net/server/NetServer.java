package org.triada.net.server;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.triada.net.EchoExchange;
import org.triada.net.Exchange;
import org.triada.net.Limits;
import org.triada.net.Moderation;
import org.triada.net.NetLog;
import org.triada.net.NetStore;
import org.triada.net.Network;
import org.triada.net.RateLimiter;
import org.triada.net.ServerConnection;
import org.triada.net.Wire;

/**
 * A TRIADA-NET server (M17, M18): the {@link ServerConnection} protocol over
 * two transports, so desktop emulators can call over TCP and the web version
 * over WebSocket, with the guards a public server needs.
 * <ul>
 * <li><b>TCP</b>: a plain {@link ServerSocket}; every caller gets a virtual
 *     thread that reads its socket.</li>
 * <li><b>WebSocket</b>: Javalin at {@code /net}; every message is a chunk of
 *     the same byte stream. Only the configured web origins are let in.</li>
 * <li><b>Per address</b>: at most a few calls at once and a few new ones a
 *     minute (D-067). Behind a proxy, the WebSocket caller's address comes
 *     from {@code X-Forwarded-For}.</li>
 * <li><b>Ticks</b>: once a second every call is ticked, for its timeouts.</li>
 * <li><b>Sysop</b>: {@code /health}, and with an admin token the admin page
 *     {@code /admin} and its API (see {@link Moderation}).</li>
 * </ul>
 *
 * <pre>
 *   ./gradlew :triada-net-server:run                      the boards, settings from TRIADA_NET_* variables
 *   ./gradlew :triada-net-server:run --args="--echo"      the M17 echo exchange
 *   triada-net-server admin queue                          the sysop's command line (see {@link #main})
 * </pre>
 */
public final class NetServer implements AutoCloseable {

    public static final int DEFAULT_TCP_PORT = 2385;
    public static final int DEFAULT_WS_PORT = 2386;
    /** The WebSocket path. */
    public static final String WS_PATH = "/net";

    /**
     * The guards (M18).
     *
     * @param origins              web origins allowed to open a WebSocket; empty: all (development)
     * @param connectionsPerIp     calls one address may have at once
     * @param connectionsPerMinute new calls one address may make in a minute
     * @param trustProxy           take WebSocket callers' addresses from X-Forwarded-For (behind Caddy)
     * @param adminToken           the admin page's secret, or null for no admin page
     * @param moderation           what the admin page works on, or null
     */
    public record Options(Set<String> origins, int connectionsPerIp, int connectionsPerMinute, boolean trustProxy,
            String adminToken, Moderation moderation) {

        /** Only an origin check: what the M17 echo server needs. */
        public static Options open(Set<String> origins) {
            return new Options(origins, Integer.MAX_VALUE, Integer.MAX_VALUE, false, null, null);
        }
    }

    private final Exchange exchange;
    private final Options options;
    private final NetLog log;
    private final ServerSocket tcp;
    private final Javalin web;
    private final Map<String, ServerConnection> sockets = new ConcurrentHashMap<>();
    private final Set<ServerConnection> live = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private final RateLimiter limiter = new RateLimiter();
    private final ScheduledExecutorService ticker;
    private volatile boolean closed;

    /** An open server, as in M17: only the origin check, and a log on standard output. */
    public NetServer(Exchange exchange, int tcpPort, int wsPort, Set<String> allowedOrigins) throws IOException {
        this(exchange, tcpPort, wsPort, Options.open(allowedOrigins), new JsonLog(System.out));
    }

    /**
     * Starts listening at once. Port 0 means "any free port" (tests); ask
     * {@link #tcpPort()} and {@link #wsPort()} which one it got.
     */
    public NetServer(Exchange exchange, int tcpPort, int wsPort, Options options, NetLog log) throws IOException {
        this.exchange = exchange;
        this.options = options;
        this.log = log;
        tcp = new ServerSocket(tcpPort);
        Thread.ofPlatform().daemon().name("triada-net-accept").start(this::acceptLoop);
        web = Javalin.create(this::routes).start(wsPort);
        ticker = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon()
                .name("triada-net-tick").unstarted(r));
        ticker.scheduleAtFixedRate(this::tickAll, 1, 1, TimeUnit.SECONDS);
        log.event("start", "tcp", tcpPort(), "ws", wsPort());
    }

    public int tcpPort() {
        return tcp.getLocalPort();
    }

    public int wsPort() {
        return web.port();
    }

    // ---------------------------------------------------------------- calls

    /** True if {@code ip} may open one more call now; it then counts until {@link #release}. */
    private boolean admit(String ip) {
        AtomicInteger n = perIp.computeIfAbsent(ip, k -> new AtomicInteger());
        synchronized (n) {
            if (n.get() >= options.connectionsPerIp()
                    || !limiter.allow("connect:" + ip, options.connectionsPerMinute(), Duration.ofMinutes(1),
                            Instant.now())) {
                log.event("refused", "ip", ip, "reason", "too many calls");
                return false;
            }
            n.incrementAndGet();
            return true;
        }
    }

    private void release(String ip) {
        AtomicInteger n = perIp.get(ip);
        if (n != null) {
            n.decrementAndGet();
        }
    }

    private void tickAll() {
        for (ServerConnection c : live) {
            try {
                c.tick();
            } catch (RuntimeException e) {
                log.event("error", "where", "tick", "message", String.valueOf(e));
            }
        }
    }

    // ---------------------------------------------------------------- TCP

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = tcp.accept();
                Thread.ofVirtual().name("triada-net-caller").start(() -> serve(s));
            } catch (IOException e) {
                if (!closed) {
                    log.event("error", "where", "accept", "message", e.getMessage());
                }
            }
        }
    }

    private void serve(Socket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        try (socket) {
            OutputStream out = socket.getOutputStream();
            if (!admit(ip)) {
                out.write("ERROR too many calls from your address\n".getBytes(StandardCharsets.US_ASCII));
                return;
            }
            ServerConnection c = new ServerConnection(exchange, new Wire() {
                @Override
                public void send(byte[] bytes) {
                    synchronized (out) {
                        try {
                            out.write(bytes);
                            out.flush();
                        } catch (IOException e) {
                            close();
                        }
                    }
                }

                @Override
                public void close() {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // already gone
                    }
                }
            }, LocalDate.now(), ip);
            live.add(c);
            log.event("connect", "ip", ip, "via", "tcp");
            try {
                InputStream in = socket.getInputStream();
                byte[] buffer = new byte[1024];
                int n;
                while (!c.closed() && (n = in.read(buffer)) >= 0) {
                    c.receive(buffer, 0, n);
                }
            } catch (IOException e) {
                // the caller is gone
            } catch (RuntimeException e) {
                log.event("error", "where", "call", "ip", ip, "message", String.valueOf(e));
            } finally {
                c.hungUp();
                live.remove(c);
                release(ip);
            }
        } catch (IOException e) {
            // couldn't even answer
        }
    }

    // ---------------------------------------------------------------- WebSocket and HTTP

    private void routes(JavalinConfig config) {
        config.startup.showJavalinBanner = false;
        config.routes.get("/health", ctx -> ctx.result("OK"));
        config.routes.ws(WS_PATH, ws -> {
            ws.onConnect(this::wsConnect);
            ws.onBinaryMessage(ctx -> {
                ServerConnection c = sockets.get(ctx.sessionId());
                if (c != null) {
                    ByteBuffer data = ctx.data();
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    receive(c, bytes);
                }
            });
            ws.onMessage(ctx -> {
                ServerConnection c = sockets.get(ctx.sessionId());
                if (c != null) {
                    receive(c, ctx.message().getBytes(StandardCharsets.UTF_8));
                }
            });
            ws.onClose(ctx -> {
                ServerConnection c = sockets.remove(ctx.sessionId());
                if (c != null) {
                    c.hungUp();
                    live.remove(c);
                    release(ctx.attribute("ip"));
                }
            });
        });
        if (options.adminToken() != null && options.moderation() != null) {
            new AdminApi(options.moderation(), this::authorized).routes(config);
        }
    }

    private void receive(ServerConnection c, byte[] bytes) {
        try {
            c.receive(bytes);
        } catch (RuntimeException e) {
            log.event("error", "where", "call", "message", String.valueOf(e));
        }
    }

    private void wsConnect(WsContext ctx) {
        String origin = ctx.header("Origin");
        if (!options.origins().isEmpty() && (origin == null || !options.origins().contains(origin))) {
            log.event("refused", "origin", String.valueOf(origin), "reason", "origin not allowed");
            ctx.closeSession(1008, "origin not allowed");
            return;
        }
        String ip = address(ctx);
        if (!admit(ip)) {
            ctx.closeSession(1008, "too many calls from your address");
            return;
        }
        ctx.attribute("ip", ip);
        ServerConnection c = new ServerConnection(exchange, new Wire() {
            @Override
            public void send(byte[] bytes) {
                ctx.send(ByteBuffer.wrap(bytes));
            }

            @Override
            public void close() {
                ctx.closeSession();
            }
        }, LocalDate.now(), ip);
        sockets.put(ctx.sessionId(), c);
        live.add(c);
        log.event("connect", "ip", ip, "via", "websocket");
    }

    /** The caller's address: the proxy's X-Forwarded-For if we trust it, else the socket's. */
    private String address(WsContext ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        if (options.trustProxy() && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        SocketAddress a = ctx.session.getRemoteSocketAddress();
        return a instanceof InetSocketAddress i ? i.getAddress().getHostAddress() : String.valueOf(a);
    }

    /** True if the request carries the admin token; checked in constant time, wrong guesses limited per address. */
    private boolean authorized(Context ctx) {
        String ip = ctx.ip();
        if (limiter.count("admin-fail:" + ip, Duration.ofMinutes(10), Instant.now()) >= 5) {
            return false;
        }
        String header = ctx.header("Authorization");
        byte[] given = (header == null ? "" : header).getBytes(StandardCharsets.UTF_8);
        byte[] expected = ("Bearer " + options.adminToken()).getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(given, expected)) {
            return true;
        }
        limiter.record("admin-fail:" + ip, Instant.now());
        log.event("refused", "ip", ip, "reason", "wrong admin token");
        return false;
    }

    @Override
    public void close() {
        closed = true;
        ticker.shutdownNow();
        try {
            tcp.close();
        } catch (IOException e) {
            // closing anyway
        }
        web.stop();
    }

    // ---------------------------------------------------------------- command line

    /**
     * Runs the server, or a sysop command.
     *
     * <pre>
     *   triada-net-server [--echo] [--tcp-port N] [--ws-port N] [--db FILE] [--boards DIR] [--origin URL ...]
     *   triada-net-server admin [--db FILE] queue | approve ID | reject ID | reports | show ID | delete ID
     *                                       | ban HANDLE | unban HANDLE | ban-ip IP | unban-ip IP
     * </pre>
     */
    public static void main(String[] args) throws IOException, SQLException {
        PrintStream out = System.out;
        try {
            if (args.length > 0 && args[0].equals("admin")) {
                System.exit(AdminCommand.run(java.util.Arrays.copyOfRange(args, 1, args.length),
                        ServerConfig.fromEnv(System.getenv()), out, System.err));
                return;
            }
            ServerConfig config = ServerConfig.fromEnv(System.getenv()).withArgs(args);
            JsonLog log = new JsonLog(out);
            if (config.echo()) {
                NetServer s = new NetServer(new EchoExchange(), config.tcpPort(), config.wsPort(),
                        Options.open(config.origins()), log);
                System.err.println("TRIADA-NET echo server: tcp " + s.tcpPort() + ", websocket ws://localhost:"
                        + s.wsPort() + WS_PATH + " (dial " + EchoExchange.ECHO + ")");
                return;
            }
            NetStore store = new SqliteNetStore(config.database());
            Network network = new Network(Boards.load(config.boards()), store, Limits.DEFAULT, Clock.systemUTC(), log);
            Options options = new Options(config.origins(), config.connectionsPerIp(), config.connectionsPerMinute(),
                    config.trustProxy(), config.adminToken(), new Moderation(store, log, "admin page"));
            NetServer s = new NetServer(network, config.tcpPort(), config.wsPort(), options, log);
            List<String> numbers = network.boards().stream().map(b -> b.number() + " " + b.name()).toList();
            System.err.println("TRIADA-NET: tcp " + s.tcpPort() + ", websocket ws://localhost:" + s.wsPort() + WS_PATH
                    + (config.adminToken() != null ? ", admin page http://localhost:" + s.wsPort() + "/admin" : "")
                    + "\n  0-00-00 TRIADA-NET INFO\n  " + String.join("\n  ", numbers));
        } catch (IllegalArgumentException e) {
            System.err.println("triada-net-server: " + e.getMessage());
            System.exit(2);
        }
    }
}
