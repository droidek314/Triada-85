package org.triada.net;

import java.nio.charset.StandardCharsets;

/**
 * The smallest possible TRIADA-NET (M17), for testing the modem end to end:
 * <ul>
 * <li>{@code 1-11-11} answers and echoes every character back. It greets with
 *     "ECHO READY" and a carriage return, and on character 4 (end of
 *     transmission) says "BYE" and hangs up;</li>
 * <li>{@code 2-22-22} is busy;</li>
 * <li>{@code 3-33-33} rings but never connects (NO CARRIER);</li>
 * <li>every other number doesn't exist.</li>
 * </ul>
 */
public final class EchoExchange implements Exchange {

    public static final String ECHO = "1-11-11";
    public static final String BUSY = "2-22-22";
    public static final String NO_CARRIER = "3-33-33";
    /** The character that makes the echo board hang up. */
    public static final int HANG_UP = 4;

    @Override
    public Answer dial(String number, Caller caller) {
        switch (number) {
            case ECHO:
                return Answer.connect(new Echo());
            case BUSY:
                return Answer.busy();
            case NO_CARRIER:
                return Answer.noCarrier();
            default:
                return Answer.noNumber();
        }
    }

    private static final class Echo implements Call {
        private Wire line;

        @Override
        public void start(Wire line) {
            this.line = line;
            line.send("ECHO READY\r".getBytes(StandardCharsets.US_ASCII));
        }

        @Override
        public void receive(int c) {
            if (c == HANG_UP) {
                line.send("BYE\r".getBytes(StandardCharsets.US_ASCII));
                line.close();
                return;
            }
            line.send(new byte[] {(byte) c});
        }
    }
}
