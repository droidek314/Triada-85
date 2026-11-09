package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.triada.net.BoardDef;
import org.triada.net.InWorld;
import org.triada.net.Limits;
import org.triada.net.MemoryNetStore;
import org.triada.net.NetLog;
import org.triada.net.Network;
import org.triada.net.TerminalScreen;

/**
 * The wire protocol (NET-SPEC §2) against the real board network (M18), over
 * TCP and WebSocket: every answer to DIAL, the directory, and a whole call.
 */
@Timeout(60)
class ProtocolTest {

    private NetServer server;

    @BeforeEach
    void start() throws Exception {
        Network net = new Network(List.of(
                BoardDef.parse("NUMBER 1-00-00\nNAME FIRST BOARD\nLINES 1\nMESSAGES G G\n"),
                BoardDef.parse("NUMBER 2-00-00\nNAME SECRET BOARD\nUNLISTED\nMESSAGES G G\n")),
                new MemoryNetStore(), Limits.DEFAULT.withPasswordIterations(10), Clock.systemUTC(), NetLog.NONE);
        server = new NetServer(net, 0, 0, new NetServer.Options(Set.of(), 10, 100, false, null, null), NetLog.NONE);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void everyAnswerToDial() throws Exception {
        String welcome = "WELCOME TRIADA-NET/1 " + InWorld.date(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        try (TcpCaller one = new TcpCaller(server.tcpPort(), "1-00-00")) {
            one.waitFor("HANDLE");
            assertTrue(one.controlLines().startsWith(welcome + "\nCONNECT 1200\n"), one.controlLines());
            try (TcpCaller busy = new TcpCaller(server.tcpPort(), "1-00-00")) {
                busy.waitFor("BUSY");                               // the board has one line
                busy.waitClosed();
            }
        }
        try (TcpCaller none = new TcpCaller(server.tcpPort(), "5-55-55")) {
            none.waitFor("NO NUMBER");
            none.waitClosed();
        }
        try (TcpCaller wrong = new TcpCaller(server.tcpPort(), null)) {
            wrong.send("HANG UP\n");
            wrong.waitFor("ERROR expected DIAL");
            wrong.waitClosed();
        }
    }

    @Test
    void theDirectoryOverTcp() throws Exception {
        try (TcpCaller c = new TcpCaller(server.tcpPort(), "0-00-00")) {
            c.waitFor("PRESS ANY KEY.");
            assertTrue(c.text().contains("1-00-00 FIRST BOARD"), c.text());
            assertTrue(!c.text().contains("SECRET"), "unlisted");
            c.type("X");
            c.waitClosed();
        }
    }

    @Test
    void aWholeCallOverWebSocket() throws Exception {
        TerminalScreen screen = new TerminalScreen();
        StringBuilder control = new StringBuilder();
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(
                URI.create("ws://localhost:" + server.wsPort() + NetServer.WS_PATH), new WebSocket.Listener() {
                    private boolean connected;

                    @Override
                    public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
                        synchronized (screen) {
                            while (data.hasRemaining()) {
                                int b = data.get() & 0xFF;
                                if (connected) {
                                    screen.receive(b);
                                } else {
                                    control.append((char) b);
                                    connected = control.toString().contains("CONNECT 1200\n");
                                }
                            }
                        }
                        w.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket w, int status, String reason) {
                        closed.complete(status);
                        return null;
                    }
                }).join();
        ws.sendBinary(ByteBuffer.wrap("HELLO TRIADA-NET/1 model=TRIADA-85\nDIAL 1-00-00\n"
                .getBytes(StandardCharsets.US_ASCII)), true).join();
        waitFor(screen, "HANDLE (GUEST, NEW):");
        ws.sendText("GUEST\r", true).join();
        waitFor(screen, "MIN LEFT. COMMAND?");
        ws.sendText("G", true).join();
        closed.get(10, TimeUnit.SECONDS);
        assertTrue(screen.text().contains("THANK YOU FOR CALLING"), screen.text());
        assertEquals(1000, closed.get());
    }

    private static void waitFor(TerminalScreen screen, String text) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            synchronized (screen) {
                if (screen.text().contains(text)) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("waited for " + text + ":\n" + screen.text());
    }
}
