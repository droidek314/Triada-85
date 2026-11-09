package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.triada.net.EchoExchange;
import org.triada.net.InWorld;

/** The echo server over real TCP and WebSocket connections on this computer (M17). */
@Timeout(30)
class NetServerTest {

    private static final String SITE = "https://droidek314.github.io";
    private NetServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private static String readUntil(InputStream in, String end) throws Exception {
        StringBuilder b = new StringBuilder();
        while (!b.toString().endsWith(end)) {
            int c = in.read();
            if (c < 0) {
                break;
            }
            b.append((char) c);
        }
        return b.toString();
    }

    @Test
    void aCallOverTcp() throws Exception {
        server = new NetServer(new EchoExchange(), 0, 0, Set.of());
        try (Socket s = new Socket("localhost", server.tcpPort())) {
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write("HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=1.1\n".getBytes(StandardCharsets.UTF_8));
            String welcome = readUntil(in, "\n");
            assertEquals("WELCOME TRIADA-NET/1 " + InWorld.date(java.time.LocalDate.now()) + "\n", welcome);
            out.write("DIAL 1-11-11\nHI".getBytes(StandardCharsets.UTF_8));
            assertEquals("CONNECT 1200\nECHO READY\rHI", readUntil(in, "HI"));
            out.write(EchoExchange.HANG_UP);
            assertEquals("BYE\r", readUntil(in, "\r"));
            assertEquals(-1, in.read(), "the board hung up");
        }
    }

    /** A WebSocket client that collects everything as text. */
    private static final class Collector implements WebSocket.Listener {
        final StringBuffer got = new StringBuffer();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] b = new byte[data.remaining()];
            data.get(b);
            got.append(new String(b, StandardCharsets.ISO_8859_1));
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            got.append(data);
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            closed.complete(status);
            return null;
        }

        void waitFor(String text) throws InterruptedException {
            for (int i = 0; i < 500 && !got.toString().contains(text); i++) {
                Thread.sleep(10);
            }
            assertTrue(got.toString().contains(text), "waited for " + text + ", got " + got);
        }
    }

    private WebSocket open(Collector c, String origin) {
        return HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + server.wsPort() + NetServer.WS_PATH), c).join();
    }

    @Test
    void aCallOverWebSocketInBinaryAndTextMessages() throws Exception {
        server = new NetServer(new EchoExchange(), 0, 0, Set.of(SITE));
        Collector c = new Collector();
        WebSocket ws = open(c, SITE);
        ws.sendBinary(ByteBuffer.wrap("HELLO TRIADA-NET/1 model=TRIADA-85\n".getBytes(StandardCharsets.UTF_8)), true)
                .join();
        c.waitFor("WELCOME TRIADA-NET/1 ");
        ws.sendText("DIAL 1-11-11\n", true).join();               // a text message is the same stream
        c.waitFor("ECHO READY\r");
        ws.sendBinary(ByteBuffer.wrap(new byte[] {'O', 'K', EchoExchange.HANG_UP}), true).join();
        c.waitFor("OKBYE\r");
        c.closed.get(10, TimeUnit.SECONDS);
    }

    @Test
    void aWebSocketFromAnotherSiteIsRefused() throws Exception {
        server = new NetServer(new EchoExchange(), 0, 0, Set.of(SITE));
        Collector c = new Collector();
        open(c, "https://somewhere-else.example");
        assertEquals(1008, c.closed.get(10, TimeUnit.SECONDS));
    }

    @Test
    void health() throws Exception {
        server = new NetServer(new EchoExchange(), 0, 0, Set.of());
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.wsPort() + "/health")).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertEquals("OK", r.body());
    }
}
