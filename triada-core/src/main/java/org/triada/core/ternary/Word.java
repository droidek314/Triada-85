package org.triada.core.ternary;

/**
 * The TRIADA word: 12 balanced trits, value range −265,720 … +265,720
 * (SPEC §1.2). Every register and every memory cell holds one word.
 *
 * <p><b>Representation.</b> A word is a plain Java {@code int} in that range,
 * not an object. Normal integer arithmetic followed by {@link #wrap(long)}
 * gives exactly the ternary result, and it is far faster than working trit
 * by trit (see DECISIONS.md D-004). Trits are only taken apart where an
 * operation really works per trit (MIN, MAX, TMUL, ROT) and for display.
 *
 * <p><b>Trit positions</b> (SPEC §1.4): {@code t11} is the most significant
 * trit (weight 3^11), {@code t0} the least (weight 1). In a trit array,
 * index {@code i} holds {@code ti}, so the array reads <em>backwards</em>
 * compared with the written string (D-005).
 */
public final class Word {

    /** Trits per word. */
    public static final int TRITS = 12;

    /** Number of different word values: 3^12. Called M in SPEC §1.3. */
    public static final int COUNT = 531_441;

    /** Largest word value, all trits +1: (3^12 − 1) / 2. */
    public static final int MAX = 265_720;

    /** Smallest word value, all trits −1. */
    public static final int MIN = -MAX;

    private Word() {
    }

    /** True if {@code value} is a valid word (within MIN … MAX). */
    public static boolean isValid(long value) {
        return value >= MIN && value <= MAX;
    }

    /**
     * Wraps any mathematical result into the word range (SPEC §1.3).
     * Same as throwing away every trit above t11.
     * Example: MAX + 1 wraps to MIN.
     */
    public static int wrap(long r) {
        return (int) (Math.floorMod(r + MAX, (long) COUNT) - MAX);
    }

    /**
     * The carry of a mathematical result: how many whole "rounds" of 3^12 were
     * thrown away by {@link #wrap(long)}. For ADD and SUB it is always −1, 0 or +1.
     */
    public static int carry(long r) {
        return (int) ((r - wrap(r)) / COUNT);
    }

    /** The trit at {@code position} (0 = t0 … 11 = t11) of a word. */
    public static int trit(int word, int position) {
        checkPosition(position);
        // Divide away the lower trits (rounding to nearest, see WordMath.sht),
        // then read the lowest remaining one.
        return Ternary.lowestTrit(WordMath.sht(word, -position));
    }

    /**
     * Returns {@code word} with the trit at {@code position} replaced by
     * {@code trit}. Useful for building test values and for the debugger.
     */
    public static int withTrit(int word, int position, int trit) {
        checkPosition(position);
        if (!Trit.isValid(trit)) {
            throw new IllegalArgumentException("not a trit: " + trit);
        }
        int old = trit(word, position);
        // Changing one trit by d changes the value by d · 3^position; never leaves the range.
        return word + (trit - old) * Ternary.pow3(position);
    }

    /**
     * Fills {@code out[0..11]} with the trits of {@code word}, {@code out[i]} = ti.
     * This version allocates nothing, so the CPU may use it with a reused array.
     */
    public static void toTrits(int word, int[] out) {
        checkWord(word);
        int v = word;
        for (int i = 0; i < TRITS; i++) {
            int t = Ternary.lowestTrit(v);
            out[i] = t;
            v = (v - t) / 3;
        }
    }

    /** The trits of {@code word} in a new array, index {@code i} = ti. */
    public static int[] toTrits(int word) {
        int[] trits = new int[TRITS];
        toTrits(word, trits);
        return trits;
    }

    /**
     * Builds a word from a trit array ({@code trits[i]} = ti). The array must
     * have exactly 12 entries, each −1, 0 or +1.
     */
    public static int fromTrits(int[] trits) {
        if (trits.length != TRITS) {
            throw new IllegalArgumentException("a word has 12 trits, array has " + trits.length);
        }
        int value = 0;
        for (int i = TRITS - 1; i >= 0; i--) {
            if (!Trit.isValid(trits[i])) {
                throw new IllegalArgumentException("trits[" + i + "] = " + trits[i] + " is not a trit");
            }
            value = value * 3 + trits[i];
        }
        return value;
    }

    /** The word as 12 trit characters, e.g. 6 → {@code "000000000+-0"}. */
    public static String toString(int word) {
        return Ternary.toString(word, TRITS);
    }

    /**
     * Reads 1 to 12 trit characters, e.g. {@code "+-0"} → 6. Shorter strings
     * mean the missing high trits are 0, as with ordinary numbers.
     */
    public static int parse(CharSequence text) {
        return Ternary.parse(text);
    }

    private static void checkPosition(int position) {
        if (position < 0 || position >= TRITS) {
            throw new IllegalArgumentException("trit position must be 0 … 11, was " + position);
        }
    }

    static void checkWord(int word) {
        if (!isValid(word)) {
            throw new IllegalArgumentException(word + " is not a word (range " + MIN + " … " + MAX + ")");
        }
    }
}
