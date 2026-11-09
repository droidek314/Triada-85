package org.triada.net.server;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * How a TRIADA-NET server is set up (M18): read from environment variables,
 * so a container needs no files changed, and overridable on the command line.
 *
 * <pre>
 *   TRIADA_NET_TCP_PORT             2385     --tcp-port N
 *   TRIADA_NET_WS_PORT              2386     --ws-port N
 *   TRIADA_NET_DB                   triada-net.db                 --db FILE
 *   TRIADA_NET_BOARDS               (the built-in boards)         --boards DIR
 *   TRIADA_NET_ORIGINS              web origins allowed, comma-separated (none: all)   --origin URL
 *   TRIADA_NET_ADMIN_TOKEN          the admin page's secret, 16+ characters (none: no admin page)
 *   TRIADA_NET_CONNECTIONS_PER_IP   4        calls one address may have at once
 *   TRIADA_NET_CONNECTIONS_PER_MINUTE 20     new calls one address may make per minute
 *   TRIADA_NET_TRUST_PROXY          false    true behind Caddy: take WebSocket callers' addresses from X-Forwarded-For
 *                                            --echo: the M17 echo exchange instead of the boards
 * </pre>
 */
public record ServerConfig(int tcpPort, int wsPort, String database, Path boards, Set<String> origins,
        String adminToken, int connectionsPerIp, int connectionsPerMinute, boolean trustProxy, boolean echo) {

    public static final int MIN_TOKEN_LENGTH = 16;

    public ServerConfig {
        origins = Set.copyOf(origins);
        if (adminToken != null && adminToken.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException("the admin token must have at least " + MIN_TOKEN_LENGTH
                    + " characters");
        }
        if (connectionsPerIp < 1 || connectionsPerMinute < 1) {
            throw new IllegalArgumentException("connection limits must be at least 1");
        }
    }

    /** The settings in {@code env} (usually {@code System.getenv()}); defaults for what is missing. */
    public static ServerConfig fromEnv(Map<String, String> env) {
        String origins = env.getOrDefault("TRIADA_NET_ORIGINS", "");
        String boards = env.get("TRIADA_NET_BOARDS");
        String token = env.get("TRIADA_NET_ADMIN_TOKEN");
        return new ServerConfig(
                number(env, "TRIADA_NET_TCP_PORT", NetServer.DEFAULT_TCP_PORT),
                number(env, "TRIADA_NET_WS_PORT", NetServer.DEFAULT_WS_PORT),
                env.getOrDefault("TRIADA_NET_DB", "triada-net.db"),
                boards == null || boards.isBlank() ? null : Path.of(boards),
                new LinkedHashSet<>(Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                        .toList()),
                token == null || token.isBlank() ? null : token.strip(),
                number(env, "TRIADA_NET_CONNECTIONS_PER_IP", 4),
                number(env, "TRIADA_NET_CONNECTIONS_PER_MINUTE", 20),
                Boolean.parseBoolean(env.getOrDefault("TRIADA_NET_TRUST_PROXY", "false")),
                false);
    }

    private static int number(Map<String, String> env, String name, int otherwise) {
        String v = env.get(name);
        try {
            return v == null || v.isBlank() ? otherwise : Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number, not '" + v + "'");
        }
    }

    /** These settings with the command line's on top. */
    public ServerConfig withArgs(String[] args) {
        int tcp = tcpPort;
        int ws = wsPort;
        String db = database;
        Path dir = boards;
        Set<String> o = new LinkedHashSet<>(origins);
        boolean e = echo;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--tcp-port" -> tcp = Integer.parseInt(value(args, ++i, a));
                case "--ws-port" -> ws = Integer.parseInt(value(args, ++i, a));
                case "--db" -> db = value(args, ++i, a);
                case "--boards" -> dir = Path.of(value(args, ++i, a));
                case "--origin" -> o.add(value(args, ++i, a));
                case "--echo" -> e = true;
                default -> throw new IllegalArgumentException("unknown option " + a);
            }
        }
        return new ServerConfig(tcp, ws, db, dir, o, adminToken, connectionsPerIp, connectionsPerMinute, trustProxy, e);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }
}
