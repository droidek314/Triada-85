package org.triada.core.ternary;

/**
 * Every arithmetic and trit-wise operation the CPU needs, on words stored as
 * {@code int} (see {@link Word}). All methods are static, pure and allocate
 * nothing, so the CPU can call them millions of times per second.
 *
 * <p>Inputs are assumed to be valid words; results always are. Flag handling
 * (S and O) belongs to the CPU (M3). This class only provides the values the
 * CPU needs to set them, such as {@link #addCarry(int, int)}.
 *
 * <p><b>The offset trick</b> used by the trit-wise operations: adding
 * {@link Word#MAX} (= 1 + 3 + 9 + … + 3^11) to a word adds 1 to every trit, so
 * {@code word + MAX} is an ordinary base-3 number (0 … 531,440) whose digits
 * 0, 1, 2 stand for the trits −1, 0, +1. Ordinary {@code % 3} and {@code / 3}
 * then take the word apart without any sign trouble.
 */
public final class WordMath {

    private WordMath() {
    }

    // ---------------------------------------------------------------- arithmetic

    /** a + b, wrapped (ADD). */
    public static int add(int a, int b) {
        return Word.wrap((long) a + b);
    }

    /** The carry trit of a + b: −1, 0 or +1 (ADD and ADDI set flag O to this). */
    public static int addCarry(int a, int b) {
        return Word.carry((long) a + b);
    }

    /** a − b, wrapped (SUB). */
    public static int sub(int a, int b) {
        return Word.wrap((long) a - b);
    }

    /** The carry trit of a − b: −1, 0 or +1 (SUB sets flag O to this). */
    public static int subCarry(int a, int b) {
        return Word.carry((long) a - b);
    }

    /** a × b, wrapped (MUL). The full product needs up to 37 bits, hence {@code long}. */
    public static int mul(int a, int b) {
        return Word.wrap((long) a * b);
    }

    /**
     * a ÷ b rounded to the nearest whole number, ties toward zero (DIV, SPEC §3.5).
     * Examples: 7÷2 → 3, −7÷2 → −3, 8÷3 → 3, 5÷3 → 2. Division by zero gives 0
     * (the CPU also sets O = +1, see {@link #isDivideByZero(int)}).
     */
    public static int div(int a, int b) {
        if (b == 0) {
            return 0;
        }
        int q = a / b;          // Java rounds toward zero
        int r = a - q * b;      // remainder, same sign as a, |r| < |b|
        // If the remainder is MORE than half of b, the true quotient is closer
        // to the next whole number away from zero. Exactly half (a tie) stays.
        if (2 * Math.abs(r) > Math.abs(b)) {
            q += Integer.signum(a) * Integer.signum(b);
        }
        return q;               // |q| ≤ |a|, so never out of range
    }

    /**
     * a − round(a ÷ b) × b, the remainder that belongs to {@link #div} (MOD).
     * It always satisfies |result| ≤ |b| / 2. Example: 8 MOD 3 = −1.
     * Modulo zero gives 0 (DECISIONS.md D-006).
     */
    public static int mod(int a, int b) {
        if (b == 0) {
            return 0;
        }
        return a - div(a, b) * b;
    }

    /** True if DIV or MOD by {@code b} is a division by zero (sets O = +1). */
    public static boolean isDivideByZero(int b) {
        return b == 0;
    }

    /** −a (NEG). In balanced ternary this flips every trit; it can never overflow. */
    public static int neg(int a) {
        return -a;
    }

    /** The sign of a word: −1, 0 or +1 (flag S after most instructions). */
    public static int sign(int a) {
        return Integer.signum(a);
    }

    /**
     * sign(a − b) computed <em>without</em> wrapping (CMP). For example
     * MAX compared with MIN is +1, although MAX − MIN would wrap to −1.
     */
    public static int compare(int a, int b) {
        return Integer.signum(a - b);   // |a − b| ≤ 531,440 fits in an int
    }

    // ---------------------------------------------------------------- trit-wise

    /** Trit-wise minimum (MIN, "ternary AND"): each result trit is the smaller of the two. */
    public static int min(int a, int b) {
        int x = a + Word.MAX;
        int y = b + Word.MAX;
        int result = 0;
        int place = 1;
        for (int i = 0; i < Word.TRITS; i++) {
            result += Math.min(x % 3, y % 3) * place;
            x /= 3;
            y /= 3;
            place *= 3;
        }
        return result - Word.MAX;
    }

    /** Trit-wise maximum (MAX, "ternary OR"): each result trit is the larger of the two. */
    public static int max(int a, int b) {
        int x = a + Word.MAX;
        int y = b + Word.MAX;
        int result = 0;
        int place = 1;
        for (int i = 0; i < Word.TRITS; i++) {
            result += Math.max(x % 3, y % 3) * place;
            x /= 3;
            y /= 3;
            place *= 3;
        }
        return result - Word.MAX;
    }

    /** Trit-wise product (TMUL): each result trit is the product of the two trits. */
    public static int tmul(int a, int b) {
        int x = a + Word.MAX;
        int y = b + Word.MAX;
        int result = 0;
        int place = 1;
        for (int i = 0; i < Word.TRITS; i++) {
            int t = (x % 3 - 1) * (y % 3 - 1);   // back to −1..+1, multiply
            result += (t + 1) * place;           // and to digit 0..2 again
            x /= 3;
            y /= 3;
            place *= 3;
        }
        return result - Word.MAX;
    }

    /**
     * Rotates every trit "up" {@code n} times, −1 → 0 → +1 → −1 (ROT, SPEC §3.6).
     * Only {@code n mod 3} matters, so n = −1 rotates down once.
     * Note that a 0 trit rotates too: ROT 0 by 1 gives all +1 = MAX.
     */
    public static int rot(int a, int n) {
        int steps = Math.floorMod(n, 3);
        if (steps == 0) {
            return a;
        }
        int x = a + Word.MAX;
        int result = 0;
        int place = 1;
        for (int i = 0; i < Word.TRITS; i++) {
            result += ((x % 3 + steps) % 3) * place;
            x /= 3;
            place *= 3;
        }
        return result - Word.MAX;
    }

    /**
     * Shifts by {@code n} trits (SHT, SPEC §3.7).
     * <ul>
     * <li>n &gt; 0: multiply by 3^n, dropping trits pushed past t11 (= wrapping).</li>
     * <li>n &lt; 0: drop the lowest |n| trits. In balanced ternary this is exactly
     *     a ÷ 3^|n| <em>rounded to nearest</em>; there are never ties, because
     *     3^|n| is odd.</li>
     * <li>|n| ≥ 12 shifts every trit out and gives 0.</li>
     * </ul>
     */
    public static int sht(int a, int n) {
        if (n >= Word.TRITS || n <= -Word.TRITS) {
            return 0;
        }
        if (n >= 0) {
            return Word.wrap((long) a * Ternary.pow3(n));
        }
        int p = Ternary.pow3(-n);
        int half = (p - 1) / 2;
        // The value of the low |n| trits, as a balanced number in −half … +half.
        int low = Math.floorMod(a + half, p) - half;
        return (a - low) / p;   // exact division: the low trits are gone
    }
}
