package org.triada.core.ternary;

/**
 * One balanced-ternary digit ("trit"): −1, 0 or +1.
 *
 * <p>A trit is stored as a plain {@code int} holding −1, 0 or +1. There is no
 * Trit object: the CPU handles millions of trits per second, and creating an
 * object for each would keep the garbage collector busy (CLAUDE.md §2).
 * This class only collects the constants and small helpers.
 *
 * <p>Written notation (SPEC §1.1): −1 is {@code '-'}, 0 is {@code '0'},
 * +1 is {@code '+'}.
 */
public final class Trit {

    public static final int NEG = -1;
    public static final int ZERO = 0;
    public static final int POS = 1;

    private Trit() {
    }

    /** True if {@code t} is −1, 0 or +1. */
    public static boolean isValid(int t) {
        return t >= NEG && t <= POS;
    }

    /** The character for a trit: {@code '-'}, {@code '0'} or {@code '+'}. */
    public static char toChar(int t) {
        switch (t) {
            case NEG:
                return '-';
            case ZERO:
                return '0';
            case POS:
                return '+';
            default:
                throw new IllegalArgumentException("not a trit: " + t + " (expected -1, 0 or +1)");
        }
    }

    /** The trit for a character {@code '-'}, {@code '0'} or {@code '+'}. */
    public static int fromChar(char c) {
        switch (c) {
            case '-':
                return NEG;
            case '0':
                return ZERO;
            case '+':
                return POS;
            default:
                throw new IllegalArgumentException(
                        "not a trit character: '" + c + "' (expected '-', '0' or '+')");
        }
    }

    /** True if {@code c} is one of {@code - 0 +}. */
    public static boolean isTritChar(char c) {
        return c == '-' || c == '0' || c == '+';
    }

    /**
     * Rotates one trit "up" {@code n} times: −1 → 0 → +1 → −1 (SPEC §3.6).
     * {@code n} may be any integer; only {@code n mod 3} matters, so
     * {@code n = -1} rotates down once.
     */
    public static int rotate(int t, int n) {
        // Shift −1..+1 to 0..2, add n on a clock with three hours, shift back.
        return Math.floorMod(t + 1 + n, 3) - 1;
    }
}
