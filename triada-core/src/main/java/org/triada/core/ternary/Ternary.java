package org.triada.core.ternary;

/**
 * Balanced-ternary helpers for numbers of any width from 1 to 12 trits:
 * powers of three, value ranges, and conversion to and from the written
 * {@code -0+} notation (most significant trit first, SPEC §1.1).
 *
 * <p>{@link Word} uses these for the 12-trit word. Other widths appear
 * elsewhere in the machine: a pixel is 3 trits (SPEC §4.2), an instruction's
 * imm5 field is 5 trits (SPEC §3.3).
 */
public final class Ternary {

    /** The widest number handled here: one word. */
    public static final int MAX_WIDTH = 12;

    /** {@code POW3[i]} = 3^i for i = 0 … 12. */
    private static final int[] POW3 = new int[MAX_WIDTH + 1];

    static {
        POW3[0] = 1;
        for (int i = 1; i <= MAX_WIDTH; i++) {
            POW3[i] = POW3[i - 1] * 3;
        }
    }

    private Ternary() {
    }

    /** 3 to the power {@code n}, for n = 0 … 12. */
    public static int pow3(int n) {
        return POW3[n];
    }

    /**
     * The largest value that {@code width} trits can hold: all trits +1,
     * which is 1 + 3 + 9 + … = (3^width − 1) / 2. The smallest is its negative.
     */
    public static int maxValue(int width) {
        checkWidth(width);
        return (POW3[width] - 1) / 2;
    }

    /**
     * Writes {@code value} as exactly {@code width} trit characters, most
     * significant first. Example: {@code toString(6, 3)} is {@code "+-0"}.
     *
     * @throws IllegalArgumentException if the value doesn't fit in that width
     */
    public static String toString(int value, int width) {
        int max = maxValue(width);
        if (value < -max || value > max) {
            throw new IllegalArgumentException(
                    value + " does not fit in " + width + " trits (range " + -max + " … " + max + ")");
        }
        char[] chars = new char[width];
        int v = value;
        // Peel off the lowest trit each round, filling the string from the right.
        for (int i = width - 1; i >= 0; i--) {
            int t = lowestTrit(v);
            chars[i] = Trit.toChar(t);
            v = (v - t) / 3;  // exact: v − t is a multiple of 3
        }
        return new String(chars);
    }

    /**
     * Reads 1 to 12 trit characters ({@code -}, {@code 0}, {@code +}), most
     * significant first. Example: {@code parse("-+")} is −2.
     *
     * @throws IllegalArgumentException for an empty string, more than 12
     *     trits, or any other character (the message names it and its position)
     */
    public static int parse(CharSequence text) {
        int n = text.length();
        if (n == 0) {
            throw new IllegalArgumentException("empty ternary number (expected 1 to 12 of '-', '0', '+')");
        }
        if (n > MAX_WIDTH) {
            throw new IllegalArgumentException(
                    "ternary number \"" + text + "\" has " + n + " trits (at most 12 fit in a word)");
        }
        int value = 0;
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (!Trit.isTritChar(c)) {
                throw new IllegalArgumentException("ternary number \"" + text + "\": character '" + c
                        + "' at position " + (i + 1) + " is not a trit (expected '-', '0' or '+')");
            }
            // Horner's rule: shift everything one place up, then add the new trit.
            value = value * 3 + Trit.fromChar(c);
        }
        return value;
    }

    /**
     * The lowest trit (t0) of any integer: the value v mod 3, but taken from
     * {−1, 0, +1} instead of {0, 1, 2}. Works for negative numbers too.
     */
    public static int lowestTrit(int v) {
        return Math.floorMod(v + 1, 3) - 1;
    }

    private static void checkWidth(int width) {
        if (width < 1 || width > MAX_WIDTH) {
            throw new IllegalArgumentException("width must be 1 … 12 trits, was " + width);
        }
    }
}
