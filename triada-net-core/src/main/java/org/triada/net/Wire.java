package org.triada.net;

/**
 * The server's end of one caller's connection (M17): a TCP socket, a
 * WebSocket or an in-process loopback. The transport provides it.
 */
public interface Wire {

    /** Sends bytes to the caller. */
    void send(byte[] bytes);

    /** Hangs up. */
    void close();
}
