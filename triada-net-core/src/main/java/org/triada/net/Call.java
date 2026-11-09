package org.triada.net;

/**
 * One answered call on a board (M17): the board's side of a conversation
 * with a caller, after CONNECT. Everything the caller types arrives in
 * {@link #receive(int)}; the board answers through the {@link Wire} it got
 * in {@link #start(Wire)}, and hangs up by closing it.
 */
public interface Call {

    /** The line is connected: say hello. */
    void start(Wire line);

    /** One character from the caller, 0 … 255. */
    void receive(int c);

    /** The caller hung up (or the line was lost). */
    default void hungUp() {
    }

    /** Called about once a second, for timeouts and time limits (M18). */
    default void tick() {
    }
}
