package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.triada.core.TriImage;
import org.triada.net.BoardDef;
import org.triada.net.Limits;
import org.triada.net.MemoryNetStore;
import org.triada.net.Moderation;
import org.triada.net.NetLog;
import org.triada.net.NetStore;
import org.triada.net.Network;
import org.triada.net.Trimodem;

/** The sysop's tools (NET-SPEC §5, M18): the admin API and page, the command line, configuration, the log. */
@Timeout(60)
class AdminTest {

    private static final String TOKEN = "a-long-secret-token-1985";
    private static final Instant T = Instant.parse("2026-11-09T12:00:00Z");

    @TempDir
    Path dir;
    private NetServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private static long queued(NetStore s, String name) {
        return s.addFile(new NetStore.FileEntry(0, "1-00-00", "PROGRAMS", name, Trimodem.Type.TRI, "A GAME",
                new TriImage(243, new int[3]), "OLGA", NetStore.FileStatus.QUEUED, T));
    }

    private HttpResponse<String> call(String method, String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + server.wsPort() + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theAdminApi() throws Exception {
        MemoryNetStore store = new MemoryNetStore();
        store.addUser(new NetStore.User("PETYA", "x", 0, null, false, T));
        long file = queued(store, "GAME.TRI");
        long msg = store.post(new NetStore.Message(0, "1-00-00", "GENERAL", "PETYA", "RUDE", "<b>RUDE</b>", T, 0));
        store.report(msg, "OLGA", T);
        Network net = new Network(List.of(BoardDef.parse("NUMBER 1-00-00\nNAME T\nMESSAGES GENERAL G\n")), store,
                Limits.DEFAULT, Clock.systemUTC(), NetLog.NONE);
        server = new NetServer(net, 0, 0, new NetServer.Options(Set.of(), 10, 100, false, TOKEN,
                new Moderation(store, NetLog.NONE, "admin page")), NetLog.NONE);

        assertEquals(200, call("GET", "/admin", null).statusCode(), "the page itself holds no secret");
        assertTrue(call("GET", "/admin", null).body().contains("TRIADA-NET sysop"));
        assertEquals(401, call("GET", "/admin/api/queue", null).statusCode());
        assertEquals(401, call("GET", "/admin/api/queue", "wrong").statusCode());

        HttpResponse<String> q = call("GET", "/admin/api/queue", TOKEN);
        assertEquals(200, q.statusCode());
        assertTrue(q.body().contains("\"name\":\"GAME.TRI\""), q.body());
        assertEquals(200, call("POST", "/admin/api/files/" + file + "/approve", TOKEN).statusCode());
        assertEquals(NetStore.FileStatus.PUBLIC, store.file(file).status());
        assertEquals(404, call("POST", "/admin/api/files/" + file + "/reject", TOKEN).statusCode(), "not queued now");

        HttpResponse<String> r = call("GET", "/admin/api/reports", TOKEN);
        assertTrue(r.body().contains("\"body\":\"<b>RUDE</b>\""), "text is data, escaped as JSON: " + r.body());
        assertEquals(200, call("POST", "/admin/api/messages/" + msg + "/delete", TOKEN).statusCode());
        assertNull(store.message(msg));
        assertTrue(call("GET", "/admin/api/reports", TOKEN).body().contains("\"message\":null"));

        assertEquals(200, call("POST", "/admin/api/handles/petya/ban", TOKEN).statusCode());
        assertTrue(store.user("PETYA").banned());
        assertEquals(404, call("POST", "/admin/api/handles/NOBODY/ban", TOKEN).statusCode());
        assertEquals(200, call("POST", "/admin/api/ips/10.0.0.9/ban", TOKEN).statusCode());
        assertTrue(store.ipBanned("10.0.0.9"));
        assertEquals(200, call("POST", "/admin/api/ips/10.0.0.9/unban", TOKEN).statusCode());
        assertFalse(store.ipBanned("10.0.0.9"));
    }

    @Test
    void wrongTokensLockTheAddressOut() throws Exception {
        MemoryNetStore store = new MemoryNetStore();
        server = new NetServer(new org.triada.net.EchoExchange(), 0, 0, new NetServer.Options(Set.of(), 10, 100,
                false, TOKEN, new Moderation(store, NetLog.NONE, "admin page")), NetLog.NONE);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, call("GET", "/admin/api/queue", "guess" + i).statusCode());
        }
        assertEquals(401, call("GET", "/admin/api/queue", TOKEN).statusCode(), "even the right one, for a while");
    }

    @Test
    void withoutATokenThereIsNoAdminPage() throws Exception {
        server = new NetServer(new org.triada.net.EchoExchange(), 0, 0, NetServer.Options.open(Set.of()),
                NetLog.NONE);
        assertEquals(404, call("GET", "/admin", null).statusCode());
        assertEquals(404, call("GET", "/admin/api/queue", TOKEN).statusCode());
    }

    // ---------------------------------------------------------------- the command line

    private record Run(int code, String out, String err) {
    }

    private Run admin(String db, String... args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] all = new String[args.length + 2];
        all[0] = "--db";
        all[1] = db;
        System.arraycopy(args, 0, all, 2, args.length);
        int code = AdminCommand.run(all, ServerConfig.fromEnv(Map.of()), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void theCommandLine() throws Exception {
        String db = dir.resolve("net.db").toString();
        long file;
        long msg;
        try (SqliteNetStore s = new SqliteNetStore(db)) {
            s.addUser(new NetStore.User("PETYA", "x", 0, null, false, T));
            file = queued(s, "GAME.TRI");
            msg = s.post(new NetStore.Message(0, "1-00-00", "GENERAL", "PETYA", "RUDE", "BAD WORDS", T, 0));
            s.report(msg, "OLGA", T);
        }
        assertTrue(admin(db, "queue").out().contains(file + "  GAME.TRI  3 words  from OLGA"));
        assertTrue(admin(db, "reports").out().contains("message " + msg + " reported by OLGA: PETYA: RUDE"));
        assertTrue(admin(db, "show", "" + msg).out().contains("BAD WORDS"));
        Run approve = admin(db, "approve", "" + file);
        assertEquals(0, approve.code());
        assertTrue(approve.err().contains("\"action\":\"approve upload\""), "the action is logged: " + approve.err());
        assertEquals(1, admin(db, "approve", "" + file).code(), "nothing waiting any more");
        assertEquals("nothing waiting", admin(db, "queue").out().strip());
        assertEquals(0, admin(db, "delete", "" + msg).code());
        assertEquals(1, admin(db, "show", "" + msg).code());
        assertEquals(0, admin(db, "ban", "petya").code());
        assertEquals(1, admin(db, "ban", "nobody").code());
        assertEquals(0, admin(db, "ban-ip", "10.0.0.9").code());
        assertEquals(2, admin(db, "approve").code());
        assertEquals(2, admin(db, "dance").code());
        try (SqliteNetStore s = new SqliteNetStore(db)) {
            assertTrue(s.user("PETYA").banned());
            assertTrue(s.ipBanned("10.0.0.9"));
            assertEquals(NetStore.FileStatus.PUBLIC, s.file(file).status());
        }
    }

    // ---------------------------------------------------------------- configuration, log, boards

    @Test
    void configurationFromTheEnvironment() {
        ServerConfig d = ServerConfig.fromEnv(Map.of());
        assertEquals(2385, d.tcpPort());
        assertEquals(2386, d.wsPort());
        assertEquals("triada-net.db", d.database());
        assertNull(d.boards());
        assertTrue(d.origins().isEmpty());
        assertNull(d.adminToken());
        assertFalse(d.trustProxy());
        ServerConfig c = ServerConfig.fromEnv(Map.of("TRIADA_NET_TCP_PORT", "4000", "TRIADA_NET_ORIGINS",
                "https://a.example, https://b.example", "TRIADA_NET_ADMIN_TOKEN", TOKEN, "TRIADA_NET_TRUST_PROXY",
                "true", "TRIADA_NET_DB", "/data/net.db", "TRIADA_NET_BOARDS", "/boards"));
        assertEquals(4000, c.tcpPort());
        assertEquals(Set.of("https://a.example", "https://b.example"), c.origins());
        assertEquals(TOKEN, c.adminToken());
        assertTrue(c.trustProxy());
        assertEquals("/data/net.db", c.database());
        assertEquals(Path.of("/boards"), c.boards());
        ServerConfig a = c.withArgs(new String[] {"--ws-port", "5000", "--echo", "--origin", "https://c.example"});
        assertEquals(5000, a.wsPort());
        assertTrue(a.echo());
        assertEquals(3, a.origins().size());
        assertThrows(IllegalArgumentException.class,
                () -> ServerConfig.fromEnv(Map.of("TRIADA_NET_ADMIN_TOKEN", "short")), "a guessable token");
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.fromEnv(Map.of("TRIADA_NET_WS_PORT", "x")));
        assertThrows(IllegalArgumentException.class, () -> d.withArgs(new String[] {"--what"}));
    }

    @Test
    void theLogIsJsonLines() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        new JsonLog(new PrintStream(b, true, StandardCharsets.UTF_8)).event("login", "handle", "O\"LGA\n",
                "id", 12, "ok", true);
        String line = b.toString(StandardCharsets.UTF_8).strip();
        assertTrue(line.startsWith("{\"time\":\""), line);
        assertTrue(line.endsWith(",\"event\":\"login\",\"handle\":\"O\\\"LGA\\n\",\"id\":12,\"ok\":true}"), line);
    }

    @Test
    void boardsFromAFolderOrBuiltIn() throws Exception {
        List<BoardDef> builtIn = Boards.load(null);
        assertEquals("1-00-00", builtIn.get(0).number());
        Files.writeString(dir.resolve("b.board"), "NUMBER 2-00-00\nNAME B\nMESSAGES G G\n");
        Files.writeString(dir.resolve("a.board"), "NUMBER 3-00-00\nNAME A\nMESSAGES G G\n");
        Files.writeString(dir.resolve("notes.txt"), "not a board");
        assertEquals(List.of("A", "B"), Boards.load(dir).stream().map(BoardDef::name).toList());
        Files.writeString(dir.resolve("c.board"), "NAME BROKEN\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Boards.load(dir));
        assertTrue(e.getMessage().startsWith("c.board: "), e.getMessage());
    }
}
