package org.triada.net;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.triada.core.TriImage;
import org.triada.core.triskette.TrisketteNames;

/**
 * Sends one file with TRIMODEM (NET-SPEC §3, M17). It never touches a line
 * itself: {@link #start()} gives the first bytes to send, and every byte the
 * receiver answers goes into {@link #receive(int)}, which gives the next
 * bytes. The caller decides when the receiver has been quiet too long and
 * then calls {@link #timeout()}.
 *
 * <pre>
 *   start ──ACK──▶ block 0 ──ACK──▶ … ──ACK──▶ end ──ACK──▶ done
 *     │ NAK or timeout: send the same message again; the third failure sends ESC 'X'
 * </pre>
 */
public final class TrimodemSender {

    private static final byte[] NOTHING = new byte[0];

    private final byte[] header;
    private final int[] words;          // padded to whole blocks
    private final int blocks;
    /** The message being sent: −1 = start, 0 … blocks − 1 = a block, blocks = end. */
    private int current = -1;
    private int failures;
    private boolean done;
    private boolean failed;

    /**
     * @throws IllegalArgumentException if {@code name} isn't a triskette name (SPEC §5.1)
     */
    public TrimodemSender(String name, Trimodem.Type type, TriImage file) {
        String n = TrisketteNames.normalize(name);
        if (n == null) {
            throw new IllegalArgumentException("not a triskette name: " + name);
        }
        StringBuilder h = new StringBuilder();
        h.append((char) Trimodem.ESC).append((char) Trimodem.START).append(n).append(';');
        Trimodem.encode(file.size(), h);
        h.append(';').append(type.name()).append(';');
        Trimodem.encode(file.loadAddress(), h);
        header = h.toString().getBytes(StandardCharsets.US_ASCII);
        blocks = Trimodem.blocks(file.size());
        words = Arrays.copyOf(file.words(), blocks * Trimodem.BLOCK_WORDS);
    }

    /** The first message: the start of the transfer. */
    public byte[] start() {
        return message();
    }

    /** One byte from the receiver; returns what to send next (often nothing). */
    public byte[] receive(int b) {
        if (done || failed) {
            return NOTHING;
        }
        if (b == Trimodem.ACK) {
            if (current == blocks) {
                done = true;            // the end was acknowledged
                return NOTHING;
            }
            current++;
            failures = 0;
            return message();
        }
        if (b == Trimodem.NAK) {
            return retry();
        }
        return NOTHING;                 // anything else is line noise
    }

    /** The receiver said nothing for too long: counts as a failure. */
    public byte[] timeout() {
        return done || failed ? NOTHING : retry();
    }

    private byte[] retry() {
        failures++;
        if (failures >= Trimodem.MAX_TRIES) {
            failed = true;
            return new byte[] {Trimodem.ESC, Trimodem.ABORT};
        }
        return message();
    }

    private byte[] message() {
        if (current < 0) {
            return header.clone();
        }
        if (current == blocks) {
            return new byte[] {Trimodem.ESC, Trimodem.END};
        }
        StringBuilder b = new StringBuilder(2 + 3 + Trimodem.BLOCK_CHARS + 3);
        b.append((char) Trimodem.ESC).append((char) Trimodem.BLOCK);
        Trimodem.encode(current, b);
        int from = current * Trimodem.BLOCK_WORDS;
        for (int i = 0; i < Trimodem.BLOCK_WORDS; i++) {
            Trimodem.encode(words[from + i], b);
        }
        Trimodem.encode(Trimodem.checksum(current, words, from), b);
        return b.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /** True once the receiver acknowledged the end. */
    public boolean done() {
        return done;
    }

    /** True if the transfer was aborted. */
    public boolean failed() {
        return failed;
    }

    /** How many blocks the receiver has acknowledged, for a progress bar. */
    public int blocksDone() {
        return Math.max(0, Math.min(current, blocks));
    }

    public int blocks() {
        return blocks;
    }
}
