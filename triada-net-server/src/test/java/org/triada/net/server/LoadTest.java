package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.triada.net.BoardDef;
import org.triada.net.Limits;
import org.triada.net.NetLog;
import org.triada.net.Network;

/**
 * 50 callers at once on a real server with the SQLite store (M18): every
 * one registers, writes a message, lists the board, reads the wall, and says
 * goodbye. Every session must complete, with no errors.
 */
@Timeout(180)
class LoadTest {

    static final int CALLERS = 50;

    @TempDir
    Path dir;

    @Test
    void fiftyCallersAtOnce() throws Exception {
        String board = """
                NUMBER 1-00-00
                NAME LOAD TEST BOARD
                LINES 60
                MESSAGES GENERAL GENERAL
                """;
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        NetLog log = (event, fields) -> {
            if (event.equals("error")) {
                errors.add(java.util.Arrays.toString(fields));
            }
        };
        // All callers come from 127.0.0.1, so the per-address limits are opened wide here.
        Limits limits = Limits.DEFAULT.withPasswordIterations(1_000).withNewUsersPerIp(CALLERS);
        try (SqliteNetStore store = new SqliteNetStore(dir.resolve("load.db").toString())) {
            Network net = new Network(List.of(BoardDef.parse(board)), store, limits, Clock.systemUTC(), log);
            try (NetServer server = new NetServer(net, 0, 0,
                    new NetServer.Options(Set.of(), CALLERS, CALLERS * 2, false, null, null), log)) {
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
                List<java.util.concurrent.Future<?>> calls = new ArrayList<>();
                for (int i = 0; i < CALLERS; i++) {
                    String handle = "LOAD" + i;
                    calls.add(pool.submit(() -> {
                        start.await();
                        try (TcpCaller c = new TcpCaller(server.tcpPort(), "1-00-00")) {
                            c.waitFor("HANDLE");
                            c.type("NEW\nY\n" + handle + "\nsecret1\nsecret1\n");
                            c.waitFor("MIN LEFT. COMMAND? ");
                            c.type("MP");
                            c.waitFor("SUBJECT: ");
                            c.type("FROM " + handle + "\nHELLO FROM CALLER " + handle + "\n.\nS");
                            // Each step waits for the LAST text of the screen, as a person
                            // would; typing earlier would race the rest of that screen.
                            c.waitFor("PRESS ANY KEY.");
                            c.type(" L");
                            while (c.waitForAny("MORE? (Y/N) ", "PRESS ANY KEY.").startsWith("MORE")) {
                                c.type("Y");                            // read the whole list
                            }
                            c.type(" Q");
                            c.waitFor("MIN LEFT. COMMAND? ");
                            c.type("O");
                            c.waitFor("A ADD  Q BACK");
                            c.type("QG");
                            c.waitFor("THANK YOU FOR CALLING");
                            c.waitClosed();
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (java.util.concurrent.Future<?> f : calls) {
                    f.get(150, TimeUnit.SECONDS);                // throws if any session failed
                }
                pool.shutdown();
                assertTrue(errors.isEmpty(), "server errors: " + errors);
                assertEquals(CALLERS, store.countMessages("1-00-00", "GENERAL"));
                assertEquals(CALLERS, store.lastCallers("1-00-00", 100).size());
                Thread.sleep(500);
                assertTrue(net.online().isEmpty(), "everyone hung up: " + net.online());
            }
        }
    }
}
