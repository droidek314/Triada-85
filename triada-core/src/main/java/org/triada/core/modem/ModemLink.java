package org.triada.core.modem;

/**
 * A two-way stream of bytes between the emulator and a TRIADA-NET server
 * (M17): one TCP connection, one WebSocket, or an in-process
 * {@link LoopbackLink}. The host provides it; {@link WireModemPort} speaks the
 * wire protocol (NET-SPEC §2) over it. Opening a link is starting a call, and
 * closing it is hanging up.
 *
 * <p>Nothing here waits: a link that is still connecting simply has nothing
 * to read yet, and keeps what is sent until it can deliver it.
 */
public interface ModemLink {

    /** The next byte received, 0 … 255, or −1 if none is waiting. */
    int read();

    /** Sends the bytes (or keeps them until the connection is open). */
    void send(byte[] bytes);

    /**
     * True once the other side closed the connection, or it could not be made
     * or was lost. Bytes that arrived before may still be waiting in {@link #read()}.
     */
    boolean isClosed();

    /** Closes the connection: hangs up. */
    void close();
}
