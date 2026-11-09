package org.triada.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.triada.core.modem.ModemLink;

/**
 * A modem line over TCP (M17), for the CLI and the desktop: one connection to
 * a TRIADA-NET server per call. Connecting and reading happen on a virtual
 * thread of their own, so the emulator never waits for the network; it just
 * finds bytes in {@link #read()} when they have arrived.
 */
public final class TcpModemLink implements ModemLink {

    /** How long the connection may take to open. */
    static final int CONNECT_TIMEOUT_MS = 5_000;

    private final Socket socket = new Socket();
    private final ConcurrentLinkedQueue<Integer> received = new ConcurrentLinkedQueue<>();
    private final Object lock = new Object();
    /** What was sent before the connection was open. */
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private OutputStream out;
    private volatile boolean closed;

    /** Starts connecting to {@code host}:{@code port} in the background. */
    public TcpModemLink(String host, int port) {
        Thread.ofVirtual().name("triada-modem").start(() -> run(host, port));
    }

    /**
     * A line opener for {@code WireModemPort}: every call connects to
     * {@code address}, written "host:port".
     *
     * @throws IllegalArgumentException if the address has no port
     */
    public static Supplier<ModemLink> opener(String address) {
        int colon = address.lastIndexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("expected host:port, got '" + address + "'");
        }
        String host = address.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(address.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expected host:port, got '" + address + "'");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("no such port: " + port);
        }
        return () -> new TcpModemLink(host, port);
    }

    private void run(String host, int port) {
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            synchronized (lock) {
                out = socket.getOutputStream();
                out.write(pending.toByteArray());
                out.flush();
                pending.reset();
            }
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                for (int i = 0; i < n; i++) {
                    received.add(buffer[i] & 0xFF);
                }
            }
        } catch (IOException e) {
            // refused, unreachable, or lost: the line is dead
        } finally {
            closed = true;
            closeQuietly();
        }
    }

    @Override
    public int read() {
        Integer b = received.poll();
        return b == null ? -1 : b;
    }

    @Override
    public void send(byte[] bytes) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (out == null) {
                pending.writeBytes(bytes);
                return;
            }
            try {
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                closed = true;
                closeQuietly();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }
}
