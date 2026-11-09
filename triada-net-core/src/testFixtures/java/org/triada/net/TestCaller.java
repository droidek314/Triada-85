package org.triada.net;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * A caller for tests (M18): it dials a number on a {@link Network} through a
 * real {@link ServerConnection}, types like a person, and shows what arrives
 * on a {@link TerminalScreen}. Bytes after CONNECT are also kept raw, for
 * TRIMODEM. No sockets, no threads.
 */
public final class TestCaller {

    public final TerminalScreen screen = new TerminalScreen();
    private final ServerConnection connection;
    private final ByteArrayOutputStream raw = new ByteArrayOutputStream();
    private final StringBuilder handshake = new StringBuilder();
    private boolean connected;
    private boolean closed;
    private String answer;

    /** Dials {@code number} from the address {@code ip}. */
    public TestCaller(Exchange exchange, String number, String ip) {
        connection = new ServerConnection(exchange, new Wire() {
            @Override
            public void send(byte[] bytes) {
                arrive(bytes);
            }

            @Override
            public void close() {
                closed = true;
            }
        }, LocalDate.of(2026, 11, 9), ip);
        connection.receive(("HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=test\nDIAL " + number + "\n")
                .getBytes(StandardCharsets.US_ASCII));
    }

    public TestCaller(Exchange exchange, String number) {
        this(exchange, number, "10.0.0.1");
    }

    private void arrive(byte[] bytes) {
        for (byte b : bytes) {
            if (connected) {
                screen.receive(b & 0xFF);
                raw.write(b);
                continue;
            }
            if (b != '\n') {
                handshake.append((char) (b & 0xFF));
                continue;
            }
            String line = handshake.toString();     // a control line: WELCOME, then the answer
            handshake.setLength(0);
            if (!line.startsWith("WELCOME")) {
                answer = line;
                connected = line.startsWith("CONNECT");
            }
        }
    }

    /** The server's answer to DIAL: "CONNECT 1200", "BUSY", "NO NUMBER", "NO CARRIER" (or an ERROR). */
    public String answer() {
        return answer;
    }

    /** Types {@code text}; '\n' is the Return key (13). */
    public TestCaller type(String text) {
        for (char c : text.toCharArray()) {
            send(c == '\n' ? 13 : c);
        }
        return this;
    }

    /** Sends one raw byte. */
    public void send(int b) {
        connection.receive(new byte[] {(byte) b});
    }

    /** One second passes on the server's clock tick (the clock itself is moved by the test). */
    public void tick() {
        connection.tick();
    }

    public void hangUp() {
        connection.hungUp();
    }

    /** True once the server hung up. */
    public boolean closed() {
        return closed;
    }

    /** The screen as text. */
    public String text() {
        return screen.text();
    }

    /** Everything received after CONNECT, and forget it. */
    public byte[] takeRaw() {
        byte[] b = raw.toByteArray();
        raw.reset();
        return b;
    }
}
