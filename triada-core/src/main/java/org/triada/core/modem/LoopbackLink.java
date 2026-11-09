package org.triada.core.modem;

import java.util.ArrayDeque;

/**
 * One end of an in-process "telephone wire" (M17): two linked ends, made by
 * {@link #pair()}, where bytes sent at one end arrive at the other. Tests and
 * an offline node use it to connect a machine to a server in the same program,
 * with no network and no threads.
 *
 * <p>An end can have a {@link Listener}: then bytes sent to it are handed over
 * at once, so a server answers within the same call. Without one, they wait
 * for {@link #read()}.
 */
public final class LoopbackLink implements ModemLink {

    /** Receives what arrives at an end, instead of {@link #read()}. */
    public interface Listener {
        void received(byte[] bytes);

        /** The other end hung up. */
        void closed();
    }

    private final ArrayDeque<Integer> inbox = new ArrayDeque<>();
    private LoopbackLink peer;
    private Listener listener;
    private boolean closed;

    private LoopbackLink() {
    }

    /** Two connected ends: [0] for the caller, [1] for the server. */
    public static LoopbackLink[] pair() {
        LoopbackLink a = new LoopbackLink();
        LoopbackLink b = new LoopbackLink();
        a.peer = b;
        b.peer = a;
        return new LoopbackLink[] {a, b};
    }

    /** Hands everything that arrives at this end to {@code listener}. */
    public void listen(Listener listener) {
        this.listener = listener;
    }

    @Override
    public int read() {
        Integer b = inbox.poll();
        return b == null ? -1 : b;
    }

    @Override
    public void send(byte[] bytes) {
        if (closed) {
            return;
        }
        peer.arrive(bytes);
    }

    private void arrive(byte[] bytes) {
        if (closed) {
            return;
        }
        if (listener != null) {
            listener.received(bytes);
            return;
        }
        for (byte b : bytes) {
            inbox.add(b & 0xFF);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        peer.hangUpFromOtherSide();
    }

    private void hangUpFromOtherSide() {
        if (closed) {
            return;
        }
        closed = true;
        if (listener != null) {
            listener.closed();
        }
    }
}
