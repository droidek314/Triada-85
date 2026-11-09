package org.triada.net.server;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.triada.net.EchoExchange;
import org.triada.net.Exchange;
import org.triada.net.ServerConnection;
import org.triada.net.Wire;

/**
 * A TRIADA-NET server (M17): the {@link ServerConnection} protocol over two
 * transports, so desktop emulators can call over TCP and the web version
 * over WebSocket.
 * <ul>
 * <li><b>TCP</b>: a plain {@link ServerSocket}; every caller gets a virtual
 *     thread that reads its socket.</li>
 * <li><b>WebSocket</b>: Javalin at {@code /net}; every message is a chunk of
 *     the same byte stream. Browsers send the page's Origin, and only the
 *     configured origins are let in (none configured: all are).</li>
 * </ul>
 * In M17 it serves the {@link EchoExchange}; M18 gives it the boards.
 *
 * <pre>
 *   ./gradlew :triada-net-server:run --args="--tcp-port 2385 --ws-port 2386"
 * </pre>
 */
public final class NetServer implements AutoCloseable {

    public static final int DEFAULT_TCP_PORT = 2385;
    public static final int DEFAULT_WS_PORT = 2386;
    /** The WebSocket path. */
    public static final String WS_PATH = "/net";

    private static final Logger LOG = Logger.getLogger(NetServer.class.getName());

    private final Exchange exchange;
    private final Set<String> allowedOrigins;
    private final ServerSocket tcp;
    private final Javalin web;
    private final Map<String, ServerConnection> sockets = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * Starts listening at once. Port 0 means "any free port" (tests); ask
     * {@link #tcpPort()} and {@link #wsPort()} which one it got.
     */
    public NetServer(Exchange exchange, int tcpPort, int wsPort, Set<String> allowedOrigins) throws IOException {
        this.exchange = exchange;
        this.allowedOrigins = Set.copyOf(allowedOrigins);
        tcp = new ServerSocket(tcpPort);
        Thread.ofPlatform().daemon().name("triada-net-accept").start(this::acceptLoop);
        web = Javalin.create(config -> {
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
                        c.receive(bytes);
                    }
                });
                ws.onMessage(ctx -> {
                    ServerConnection c = sockets.get(ctx.sessionId());
                    if (c != null) {
                        c.receive(ctx.message().getBytes(StandardCharsets.UTF_8));
                    }
                });
                ws.onClose(ctx -> {
                    ServerConnection c = sockets.remove(ctx.sessionId());
                    if (c != null) {
                        c.hungUp();
                    }
                });
            });
        }).start(wsPort);
    }

    public int tcpPort() {
        return tcp.getLocalPort();
    }

    public int wsPort() {
        return web.port();
    }

    // ---------------------------------------------------------------- TCP

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = tcp.accept();
                Thread.ofVirtual().name("triada-net-caller").start(() -> serve(s));
            } catch (IOException e) {
                if (!closed) {
                    LOG.warning("accept failed: " + e.getMessage());
                }
            }
        }
    }

    private void serve(Socket socket) {
        LOG.info("call from " + socket.getRemoteSocketAddress() + " (tcp)");
        try (socket) {
            OutputStream out = socket.getOutputStream();
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
            }, LocalDate.now());
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int n;
            while (!c.closed() && (n = in.read(buffer)) >= 0) {
                c.receive(buffer, 0, n);
            }
            c.hungUp();
        } catch (IOException e) {
            // the caller is gone; nothing to clean up beyond the socket
        }
    }

    // ---------------------------------------------------------------- WebSocket

    private void wsConnect(WsContext ctx) {
        String origin = ctx.header("Origin");
        if (!allowedOrigins.isEmpty() && (origin == null || !allowedOrigins.contains(origin))) {
            LOG.info("refused a WebSocket from origin " + origin);
            ctx.closeSession(1008, "origin not allowed");
            return;
        }
        LOG.info("call from " + ctx.session.getRemoteSocketAddress() + " (websocket)");
        sockets.put(ctx.sessionId(), new ServerConnection(exchange, new Wire() {
            @Override
            public void send(byte[] bytes) {
                ctx.send(ByteBuffer.wrap(bytes));
            }

            @Override
            public void close() {
                ctx.closeSession();
            }
        }, LocalDate.now()));
    }

    @Override
    public void close() {
        closed = true;
        try {
            tcp.close();
        } catch (IOException e) {
            // closing anyway
        }
        web.stop();
    }

    // ---------------------------------------------------------------- command line

    /** {@code [--tcp-port N] [--ws-port N] [--origin URL ...]}: runs the echo exchange until stopped. */
    public static void main(String[] args) throws IOException {
        int tcpPort = DEFAULT_TCP_PORT;
        int wsPort = DEFAULT_WS_PORT;
        List<String> origins = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tcp-port" -> tcpPort = Integer.parseInt(args[++i]);
                case "--ws-port" -> wsPort = Integer.parseInt(args[++i]);
                case "--origin" -> origins.add(args[++i]);
                default -> {
                    System.err.println("usage: triada-net-server [--tcp-port N] [--ws-port N] [--origin URL ...]");
                    System.exit(2);
                }
            }
        }
        NetServer server = new NetServer(new EchoExchange(), tcpPort, wsPort, Set.copyOf(origins));
        System.out.println("TRIADA-NET echo server: tcp " + server.tcpPort() + ", websocket ws://localhost:"
                + server.wsPort() + WS_PATH + " (dial " + EchoExchange.ECHO + ")");
    }
}
