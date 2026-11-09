package org.triada.net;

import java.util.Arrays;
import org.triada.core.TriImage;
import org.triada.core.ternary.Word;
import org.triada.core.triskette.TrisketteNames;

/**
 * Receives one file with TRIMODEM (NET-SPEC §3, M17). Every byte from the
 * line goes into {@link #receive(int)}, which returns what to answer: ACK,
 * NAK or nothing. A message that arrives broken (a bad character, a wrong
 * checksum, a block out of order) is answered with NAK, and the third
 * failure in a row ends the transfer. A block or start that arrives twice,
 * because our ACK got lost, is acknowledged again and otherwise ignored.
 */
public final class TrimodemReceiver {

    private static final byte[] NOTHING = new byte[0];
    private static final byte[] ACK = {Trimodem.ACK};
    private static final byte[] NAK = {Trimodem.NAK};
    /** A start message longer than this is broken: 12 + 1 + 3 + 1 + 3 + 1 + 3 characters at most. */
    private static final int MAX_HEADER = 24;
    /** The largest file accepted: all of RAM. */
    public static final int MAX_WORDS = 59_049;

    private enum State { WAIT_ESC, WAIT_KIND, HEADER, BLOCK }

    private State state = State.WAIT_ESC;
    private final StringBuilder buffer = new StringBuilder();

    private String name;
    private Trimodem.Type type;
    private int length;
    private int address;
    private int[] words;
    private int blocks;
    private int expected;
    private int failures;
    private boolean done;
    private boolean failed;

    /** One byte from the line; returns the answer to send (ACK, NAK, or nothing). */
    public byte[] receive(int c) {
        if (done || failed) {
            return NOTHING;
        }
        switch (state) {
            case WAIT_ESC:
                if (c == Trimodem.ESC) {
                    state = State.WAIT_KIND;
                }
                return NOTHING;
            case WAIT_KIND:
                return kind(c);
            case HEADER:
                return headerChar(c);
            default:
                return blockChar(c);
        }
    }

    private byte[] kind(int c) {
        buffer.setLength(0);
        switch (c) {
            case Trimodem.START:
                state = State.HEADER;
                return NOTHING;
            case Trimodem.BLOCK:
                state = State.BLOCK;
                return NOTHING;
            case Trimodem.END:
                state = State.WAIT_ESC;
                if (words != null && expected == blocks) {
                    done = true;
                    return ACK;
                }
                return fail();
            case Trimodem.ABORT:
                failed = true;
                return NOTHING;
            case Trimodem.ESC:
                return NOTHING;                     // ESC ESC: still waiting for the letter
            default:
                state = State.WAIT_ESC;
                return NOTHING;
        }
    }

    private byte[] headerChar(int c) {
        if (c == Trimodem.ESC || buffer.length() >= MAX_HEADER) {
            state = c == Trimodem.ESC ? State.WAIT_KIND : State.WAIT_ESC;
            return fail();
        }
        buffer.append((char) c);
        // name ';' LLL ';' TYPE ';' AAA. The digits may themselves be ';' (59 is
        // a digit character), so the fields are found by position, not by splitting.
        int p = buffer.indexOf(";");
        if (p < 0 || buffer.length() < p + 5) {
            return NOTHING;
        }
        if (buffer.charAt(p + 4) != ';') {
            state = State.WAIT_ESC;
            return fail();
        }
        int q = buffer.indexOf(";", p + 5);
        if (q < 0 || buffer.length() < q + 4) {
            return NOTHING;
        }
        state = State.WAIT_ESC;
        return header(buffer.substring(0, p), buffer.substring(p + 1, p + 4), buffer.substring(p + 5, q),
                buffer.substring(q + 1, q + 4));
    }

    private byte[] header(String fileName, String len3, String typeName, String addr3) {
        try {
            String n = TrisketteNames.normalize(fileName);
            Trimodem.Type t = Trimodem.Type.valueOf(typeName);
            int len = Trimodem.decode(len3, 0);
            int addr = Trimodem.decode(addr3, 0);
            if (n == null || len < 0 || len > MAX_WORDS || (long) addr + len - 1 > Word.MAX) {
                return fail();
            }
            if (words != null) {                    // the start again: our ACK was lost
                return n.equals(name) && len == length && expected == 0 ? ACK : fail();
            }
            name = n;
            type = t;
            length = len;
            address = addr;
            blocks = Trimodem.blocks(len);
            words = new int[blocks * Trimodem.BLOCK_WORDS];
            failures = 0;
            return ACK;
        } catch (IllegalArgumentException e) {      // a bad digit or type
            return fail();
        }
    }

    private byte[] blockChar(int c) {
        if (!Trimodem.isDigit(c)) {
            state = c == Trimodem.ESC ? State.WAIT_KIND : State.WAIT_ESC;
            return fail();
        }
        buffer.append((char) c);
        if (buffer.length() < 3 + Trimodem.BLOCK_CHARS + 3) {
            return NOTHING;
        }
        state = State.WAIT_ESC;
        if (words == null) {
            return fail();                          // a block before the start
        }
        int number = Trimodem.decode(buffer, 0);
        int[] data = new int[Trimodem.BLOCK_WORDS];
        for (int i = 0; i < data.length; i++) {
            data[i] = Trimodem.decode(buffer, 3 + 3 * i);
        }
        int sum = Trimodem.decode(buffer, 3 + Trimodem.BLOCK_CHARS);
        if (sum != Trimodem.checksum(number, data, 0)) {
            return fail();
        }
        if (number == expected - 1) {
            return ACK;                             // a repeat: our ACK was lost
        }
        if (number != expected || expected >= blocks) {
            return fail();
        }
        System.arraycopy(data, 0, words, expected * Trimodem.BLOCK_WORDS, data.length);
        expected++;
        failures = 0;
        return ACK;
    }

    private byte[] fail() {
        failures++;
        if (failures >= Trimodem.MAX_TRIES) {
            failed = true;
        }
        return NAK;
    }

    public boolean done() {
        return done;
    }

    public boolean failed() {
        return failed;
    }

    /** The file's name from the start message (upper case), or null before it. */
    public String name() {
        return name;
    }

    public Trimodem.Type type() {
        return type;
    }

    /** Blocks received so far, and in all, for a progress bar. */
    public int blocksDone() {
        return expected;
    }

    public int blocks() {
        return blocks;
    }

    /** The file received; only once {@link #done()}. */
    public TriImage file() {
        if (!done) {
            throw new IllegalStateException("the transfer isn't finished");
        }
        return new TriImage(address, Arrays.copyOf(words, length));
    }
}
