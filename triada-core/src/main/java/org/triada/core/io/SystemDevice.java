package org.triada.core.io;

import org.triada.core.ternary.Word;

/**
 * The machine's own registers: FRAME (k = 0), the frame counter, and RAND
 * (k = 19), a source of random words (SPEC §5). Both are read-only for the
 * CPU; writes are ignored.
 *
 * <p>RAND comes from a small, fast pseudo-random generator (xorshift32) with
 * a fixed starting seed, so the same program behaves the same way on every
 * run. That makes tests repeatable. A frontend that wants different games
 * every time can call {@link #seed(int)} with, say, the clock.
 */
public final class SystemDevice implements IoDevice {

    private int frame;
    private int randomState;
    /** The next value RAND will return; computed ahead so that peek can show it. */
    private int nextRandom;

    /** Default seed: any non-zero number works; this one is 3^12 + 85. */
    public static final int DEFAULT_SEED = 531_526;

    public SystemDevice() {
        seed(DEFAULT_SEED);
    }

    /** Restarts the random sequence. A seed of 0 is replaced by the default (xorshift needs non-zero). */
    public void seed(int seed) {
        randomState = seed == 0 ? DEFAULT_SEED : seed;
        nextRandom = nextWord();
    }

    /** Called once per frame by the machine after the CPU's turn (SPEC §6). Wraps like any word. */
    public void advanceFrame() {
        frame = frame == Word.MAX ? Word.MIN : frame + 1;
    }

    /** The current FRAME value. */
    public int frame() {
        return frame;
    }

    /** Sets FRAME back to 0, e.g. at power-on. */
    public void resetFrame() {
        frame = 0;
    }

    @Override
    public int read(int k) {
        if (k == IoRegisters.RAND) {
            int value = nextRandom;
            nextRandom = nextWord();
            return value;
        }
        return peek(k);
    }

    @Override
    public int peek(int k) {
        if (k == IoRegisters.FRAME) {
            return frame;
        }
        if (k == IoRegisters.RAND) {
            return nextRandom;
        }
        return 0;
    }

    @Override
    public void write(int k, int value) {
        // FRAME and RAND are read-only (SPEC §5).
    }

    /**
     * xorshift32 (George Marsaglia, 2003): three shift-and-XOR steps scramble a
     * 32-bit state. The unsigned state is folded into the word range with
     * {@code mod 3^12}; the tiny bias this leaves (2^32 isn't a multiple of
     * 3^12) is far below anything a game could notice.
     */
    private int nextWord() {
        int x = randomState;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        randomState = x;
        return (int) (Integer.toUnsignedLong(x) % Word.COUNT) - Word.MAX;
    }
}
