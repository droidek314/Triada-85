package org.triada.net.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.triada.net.TerminalScreen;

/**
 * A caller over a real TCP connection, for the server's tests (M18): it reads
 * everything on a thread of its own into a {@link TerminalScreen}, and can
 * wait for text to appear, as a person watching the screen would.
 */
final class TcpCaller implements AutoCloseable {

    private final Socket socket;
    private final OutputStream out;
    private final TerminalScreen screen = new TerminalScreen();
    private final StringBuilder control = new StringBuilder();
    private final ByteArrayOutputStream raw = new ByteArrayOutputStream();
    /** Text received since the caller last sent something: what {@link #waitFor} looks at. */
    private final StringBuilder fresh = new StringBuilder();
    private boolean connected;
    private volatile boolean closed;

    /** Connects and says HELLO; dials {@code number} unless it is null. */
    TcpCaller(int port, String number) throws IOException {
        socket = new Socket("localhost", port);
        socket.setSoTimeout(0);
        out = socket.getOutputStream();
        Thread.ofVirtual().start(this::read);
        send("HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=test\n"
                + (number == null ? "" : "DIAL " + number + "\n"));
    }

    private void read() {
        try {
            InputStream in = socket.getInputStream();
            int b;
            while ((b = in.read()) >= 0) {
                synchronized (this) {
                    if (connected) {
                        screen.receive(b);
                        raw.write(b);
                        fresh.append(b == 13 ? '|' : (char) b);
                    } else {
                        control.append((char) b);
                        connected = control.toString().contains("CONNECT 1200\n");
                    }
                    notifyAll();
                }
            }
        } catch (IOException e) {
            // closed
        } finally {
            synchronized (this) {
                closed = true;
                notifyAll();
            }
        }
    }

    void send(String text) throws IOException {
        synchronized (this) {
            fresh.setLength(0);
        }
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    /** Types {@code text}; '\n' is Return (13). */
    void type(String text) throws IOException {
        send(text.replace('\n', '\r'));
    }

    /**
     * Waits until {@code text} has arrived since the caller last sent something
     * (or is in the control lines before CONNECT); old text on the screen doesn't count.
     */
    synchronized void waitFor(String text) throws InterruptedException {
        waitForAny(text);
    }

    /** Waits until one of {@code texts} has arrived since the caller last sent something; returns which. */
    synchronized String waitForAny(String... texts) throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        while (true) {
            for (String t : texts) {
                if (fresh.toString().contains(t) || control.toString().contains(t)) {
                    return t;
                }
            }
            if (closed || System.currentTimeMillis() > end) {
                throw new AssertionError("waited for " + java.util.Arrays.toString(texts) + "; control: " + control
                        + "\nscreen:\n" + screen.text());
            }
            wait(100);
        }
    }

    /** Waits until the server hangs up. */
    synchronized void waitClosed() throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        while (!closed) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("still connected; screen:\n" + screen.text());
            }
            wait(100);
        }
    }

    /** Waits for {@code count} bytes after CONNECT, then hands them over. */
    synchronized byte[] takeRaw(int count) throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        while (raw.size() < count && !closed && System.currentTimeMillis() < end) {
            wait(50);
        }
        byte[] b = raw.toByteArray();
        raw.reset();
        return b;
    }

    synchronized String controlLines() {
        return control.toString();
    }

    synchronized String text() {
        return screen.text();
    }

    boolean closed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
