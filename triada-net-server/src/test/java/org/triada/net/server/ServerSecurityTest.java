package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.triada.net.BoardDef;
import org.triada.net.Limits;
import org.triada.net.MemoryNetStore;
import org.triada.net.NetLog;
import org.triada.net.Network;
import org.triada.net.Trimodem;

/**
 * The security list of M18 (NET-SPEC §5), over real TCP connections: limits
 * per address, wrong passwords, oversized input, flooding, and path tricks
 * in file names. (Rate limits, lockouts and limits inside a call are tested
 * one by one in {@code BoardSessionTest}; the origin check in {@code NetServerTest}.)
 */
@Timeout(60)
class ServerSecurityTest {

    static final String BOARD = """
            NUMBER 1-00-00
            NAME TEST BOARD
            LINES 20
            MESSAGES GENERAL GENERAL
            FILES PROGRAMS PROGRAMS
            """;

    private final MemoryNetStore store = new MemoryNetStore();
    private final List<String> log = java.util.Collections.synchronizedList(new ArrayList<>());
    private NetServer server;

    private void start(int perIp, int perMinute) throws Exception {
        NetLog l = (event, fields) -> log.add(event + " " + java.util.Arrays.toString(fields));
        Network net = new Network(List.of(BoardDef.parse(BOARD)), store, Limits.DEFAULT.withPasswordIterations(10),
                Clock.systemUTC(), l);
        server = new NetServer(net, 0, 0, new NetServer.Options(Set.of(), perIp, perMinute, false, null, null), l);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void anAddressGetsAFewCallsAtOnce() throws Exception {
        start(2, 100);
        TcpCaller a = new TcpCaller(server.tcpPort(), "1-00-00");
        try (TcpCaller b = new TcpCaller(server.tcpPort(), "1-00-00");
                TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
            a.waitFor("HANDLE");
            b.waitFor("HANDLE");
            c.waitFor("ERROR too many calls from your address");
            c.waitClosed();
            a.close();                                           // one caller hangs up
            a.waitClosed();
            Thread.sleep(300);                                   // the server notices the hang-up
            try (TcpCaller d = new TcpCaller(server.tcpPort(), "1-00-00")) {
                d.waitFor("HANDLE");                              // a line is free again
            }
        }
        assertTrue(log.stream().anyMatch(l -> l.startsWith("refused") && l.contains("too many calls")));
    }

    @Test
    void andAFewNewCallsAMinute() throws Exception {
        start(100, 3);
        for (int i = 0; i < 3; i++) {
            try (TcpCaller ok = new TcpCaller(server.tcpPort(), "1-00-00")) {
                ok.waitFor("HANDLE");
            }
        }
        try (TcpCaller fourth = new TcpCaller(server.tcpPort(), "1-00-00")) {
            fourth.waitFor("ERROR too many calls");
        }
    }

    @Test
    void wrongPasswordsHangUp() throws Exception {
        start(10, 100);
        try (TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
            c.waitFor("HANDLE");
            c.type("NEW\nY\nOLGA\nsecret1\nsecret1\nG");
            c.waitClosed();
        }
        try (TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
            c.waitFor("HANDLE");
            c.type("OLGA\nwrong1\nOLGA\nwrong2\nOLGA\nwrong3\n");
            c.waitFor("WRONG. GOODBYE.");
            c.waitClosed();
        }
        assertTrue(log.stream().noneMatch(l -> l.contains("wrong1") || l.contains("secret1")), "never a password");
    }

    @Test
    void oversizedInput() throws Exception {
        start(10, 100);
        try (TcpCaller c = new TcpCaller(server.tcpPort(), null)) {
            c.send("DIAL " + "9".repeat(10_000) + "\n");        // a control line far too long
            c.waitFor("ERROR line too long");
            c.waitClosed();
        }
        try (TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
            c.waitFor("HANDLE");
            c.type("Z".repeat(500));                             // a handle has 12 characters at most
            Thread.sleep(300);
            assertEquals(12, c.text().chars().filter(ch -> ch == 'Z').count(), "nothing past the 12th: " + c.text());
        }
    }

    @Test
    void floodingIsCutOff() throws Exception {
        start(10, 100);
        try (TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
            c.waitFor("HANDLE");
            c.send("X".repeat(5_000));
            c.waitFor("FLOODING. GOODBYE.");
            c.waitClosed();
        }
    }

    @Test
    void fileNamesAreNeverPaths() throws Exception {
        start(10, 100);
        try (TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
            c.waitFor("HANDLE");
            c.type("NEW\nY\nOLGA\nsecret1\nsecret1\n");
            c.waitFor("COMMAND?");
            c.type("FD../../etc/passwd\n");
            c.waitFor("NO SUCH FILE.");
            c.type(" U");
            c.waitFor("TRIMODEM NOW.");
            c.takeRaw(0);
            // a start message whose "name" tries to climb out of a folder
            c.send("\u001bF../../X.TRI;" + Trimodem.encode(1) + ";TRI;" + Trimodem.encode(243));
            byte[] answer = c.takeRaw(1);
            assertEquals(Trimodem.NAK, answer[answer.length - 1]);
        }
        assertTrue(store.queue().isEmpty());
    }
}
