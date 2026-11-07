package org.triada.core.ternary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class WordMathTest {

    private static final int MAX = Word.MAX;
    private static final int MIN = Word.MIN;

    // ------------------------------------------------------------- add / sub

    @Test
    void addWithoutOverflow() {
        assertEquals(5, WordMath.add(2, 3));
        assertEquals(0, WordMath.addCarry(2, 3));
        assertEquals(-1, WordMath.add(2, -3));
    }

    @Test
    void addWrapsUpwardWithCarryPlusOne() {
        assertEquals(MIN, WordMath.add(MAX, 1));
        assertEquals(1, WordMath.addCarry(MAX, 1));
        assertEquals(-1, WordMath.add(MAX, MAX));      // 531,440 − 531,441 = −1
        assertEquals(1, WordMath.addCarry(MAX, MAX));
    }

    @Test
    void addWrapsDownwardWithCarryMinusOne() {
        assertEquals(MAX, WordMath.add(MIN, -1));
        assertEquals(-1, WordMath.addCarry(MIN, -1));
        assertEquals(1, WordMath.add(MIN, MIN));       // −531,440 + 531,441 = 1
        assertEquals(-1, WordMath.addCarry(MIN, MIN));
    }

    @Test
    void subWrapsBothWays() {
        assertEquals(MIN, WordMath.sub(MAX, -1));
        assertEquals(1, WordMath.subCarry(MAX, -1));
        assertEquals(MAX, WordMath.sub(MIN, 1));
        assertEquals(-1, WordMath.subCarry(MIN, 1));
        assertEquals(0, WordMath.sub(MAX, MAX));
        assertEquals(0, WordMath.subCarry(MAX, MAX));
        assertEquals(-1, WordMath.sub(MAX, MIN));      // 531,440 wraps to −1
        assertEquals(1, WordMath.subCarry(MAX, MIN));
    }

    @Test
    void carryIsAlwaysATritAndResultPlusCarryIsExact() {
        Random rnd = new Random(85);
        for (int i = 0; i < 200_000; i++) {
            int a = randomWord(rnd);
            int b = randomWord(rnd);
            int c = WordMath.addCarry(a, b);
            assertTrue(Trit.isValid(c));
            assertEquals((long) a + b, WordMath.add(a, b) + (long) c * Word.COUNT);
            int d = WordMath.subCarry(a, b);
            assertTrue(Trit.isValid(d));
            assertEquals((long) a - b, WordMath.sub(a, b) + (long) d * Word.COUNT);
        }
    }

    // ------------------------------------------------------------- mul

    @Test
    void mulSmallAndWrapped() {
        assertEquals(42, WordMath.mul(6, 7));
        assertEquals(-42, WordMath.mul(-6, 7));
        assertEquals(Word.wrap((long) MAX * MAX), WordMath.mul(MAX, MAX));
        assertEquals(WordMath.mul(MAX, MAX), WordMath.mul(MIN, MIN));
        // 3^6 × 3^6 = 3^12: exactly one full round, wraps to 0.
        assertEquals(0, WordMath.mul(729, 729));
        // (3^6 + 1)² = 3^12 + 2·3^6 + 1 → the 3^12 part is dropped.
        assertEquals(2 * 729 + 1, WordMath.mul(730, 730));
    }

    @Test
    void mulAgreesWithWrapOfExactProduct() {
        Random rnd = new Random(3);
        for (int i = 0; i < 200_000; i++) {
            int a = randomWord(rnd);
            int b = randomWord(rnd);
            assertEquals(Word.wrap((long) a * b), WordMath.mul(a, b));
        }
    }

    // ------------------------------------------------------------- div / mod

    @Test
    void divRoundingExamplesFromSpec() {
        assertEquals(3, WordMath.div(7, 2));    // 3.5 → 3 (tie toward zero)
        assertEquals(-3, WordMath.div(-7, 2));  // −3.5 → −3
        assertEquals(3, WordMath.div(8, 3));    // 2.67 → 3
        assertEquals(2, WordMath.div(5, 3));    // 1.67 → 2
    }

    @Test
    void divOtherSignsAndTies() {
        assertEquals(-3, WordMath.div(7, -2));
        assertEquals(3, WordMath.div(-7, -2));
        assertEquals(-3, WordMath.div(-8, 3));
        assertEquals(0, WordMath.div(1, 2));    // 0.5 → 0
        assertEquals(1, WordMath.div(3, 2));    // 1.5 → 1
        assertEquals(1, WordMath.div(2, 3));    // 0.67 → 1
        assertEquals(0, WordMath.div(1, 3));
    }

    @Test
    void divExtremesNeverOverflow() {
        assertEquals(MAX, WordMath.div(MIN, -1));   // in two's complement this overflows; here it can't
        assertEquals(MIN, WordMath.div(MAX, -1));
        assertEquals(1, WordMath.div(MAX, MAX));
        assertEquals(-1, WordMath.div(MIN, MAX));
        assertEquals(0, WordMath.div(1, MAX));
    }

    @Test
    void modExamples() {
        assertEquals(-1, WordMath.mod(8, 3));   // SPEC §3.5
        assertEquals(1, WordMath.mod(7, 2));    // 7 − 3·2
        assertEquals(-1, WordMath.mod(-7, 2));  // −7 − (−3)·2
        assertEquals(1, WordMath.mod(-8, 3));   // −8 − (−3)·3
        assertEquals(0, WordMath.mod(MIN, -1));
    }

    @Test
    void divisionByZeroGivesZero() {
        assertEquals(0, WordMath.div(17, 0));
        assertEquals(0, WordMath.div(MIN, 0));
        assertEquals(0, WordMath.mod(17, 0));
        assertEquals(0, WordMath.mod(MAX, 0));
        assertTrue(WordMath.isDivideByZero(0));
        assertTrue(!WordMath.isDivideByZero(1));
    }

    /** Checks div and mod against a slow, obviously-correct reference for many pairs. */
    @Test
    void divAndModMatchReferenceAndModIsAtMostHalfTheDivisor() {
        for (int a = -400; a <= 400; a++) {
            for (int b = -40; b <= 40; b++) {
                checkDivMod(a, b);
            }
        }
        Random rnd = new Random(12);
        for (int i = 0; i < 200_000; i++) {
            checkDivMod(randomWord(rnd), randomWord(rnd));
            checkDivMod(randomWord(rnd), rnd.nextInt(201) - 100);
        }
        int[] edges = {MIN, MIN + 1, -2, -1, 1, 2, MAX - 1, MAX};
        for (int a : edges) {
            for (int b : edges) {
                checkDivMod(a, b);
            }
        }
    }

    private static void checkDivMod(int a, int b) {
        if (b == 0) {
            return;
        }
        int expected = referenceDiv(a, b);
        int q = WordMath.div(a, b);
        if (q != expected) {
            fail(a + " DIV " + b + " = " + q + ", expected " + expected);
        }
        int r = WordMath.mod(a, b);
        if (r != a - expected * b || 2L * Math.abs(r) > Math.abs((long) b)) {
            fail(a + " MOD " + b + " = " + r);
        }
    }

    /** Round to nearest, ties toward zero, by comparing the two nearest candidates. */
    private static int referenceDiv(int a, int b) {
        long lo = Math.floorDiv((long) a, b);
        long hi = lo + 1;
        long errLo = Math.abs((long) a - lo * b);
        long errHi = Math.abs((long) a - hi * b);
        if (errLo != errHi) {
            return (int) (errLo < errHi ? lo : hi);
        }
        return (int) (Math.abs(lo) < Math.abs(hi) ? lo : hi);   // tie: the one nearer zero
    }

    // ------------------------------------------------------------- neg / sign / compare

    @Test
    void negFlipsEveryTritForEveryWord() {
        int[] t = new int[12];
        int[] n = new int[12];
        for (int v = MIN; v <= MAX; v++) {
            Word.toTrits(v, t);
            Word.toTrits(WordMath.neg(v), n);
            for (int i = 0; i < 12; i++) {
                if (n[i] != -t[i]) {
                    fail("NEG " + v + ": trit " + i);
                }
            }
        }
    }

    @Test
    void signAndCompare() {
        assertEquals(-1, WordMath.sign(MIN));
        assertEquals(0, WordMath.sign(0));
        assertEquals(1, WordMath.sign(7));
        assertEquals(1, WordMath.compare(MAX, MIN));    // no wrapping, unlike SUB
        assertEquals(-1, WordMath.compare(MIN, MAX));
        assertEquals(0, WordMath.compare(-5, -5));
        assertEquals(-1, WordMath.compare(-6, -5));
    }

    // ------------------------------------------------------------- trit-wise

    @Test
    void tritwiseTruthTables() {
        // One trit each, all nine combinations (written as a, b → MIN, MAX, TMUL).
        int[][] table = {
            {-1, -1, -1, -1, 1}, {-1, 0, -1, 0, 0}, {-1, 1, -1, 1, -1},
            {0, -1, -1, 0, 0}, {0, 0, 0, 0, 0}, {0, 1, 0, 1, 0},
            {1, -1, -1, 1, -1}, {1, 0, 0, 1, 0}, {1, 1, 1, 1, 1},
        };
        for (int[] row : table) {
            assertEquals(row[2], WordMath.min(row[0], row[1]), "MIN " + row[0] + "," + row[1]);
            assertEquals(row[3], WordMath.max(row[0], row[1]), "MAX " + row[0] + "," + row[1]);
            assertEquals(row[4], WordMath.tmul(row[0], row[1]), "TMUL " + row[0] + "," + row[1]);
        }
    }

    @Test
    void tritwiseExamplesOnWholeWords() {
        int a = Word.parse("+-0+-0+-0+-0");
        int b = Word.parse("0+-0+-0+-0+-");
        // Column by column: (+,0) (-,+) (0,-) repeated four times.
        assertEquals(Word.parse("0--0--0--0--"), WordMath.min(a, b));
        assertEquals(Word.parse("++0++0++0++0"), WordMath.max(a, b));
        assertEquals(Word.parse("0-00-00-00-0"), WordMath.tmul(a, b));
    }

    @Test
    void tritwiseOpsMatchTritByTritReference() {
        // Every word against a handful of fixed partners, plus many random pairs.
        int[] partners = {MIN, -1, 0, 1, MAX, Word.parse("+-0+-0+-0+-0"), 12_345, -99_999};
        for (int a = MIN; a <= MAX; a++) {
            for (int b : partners) {
                checkTritwise(a, b);
            }
        }
        Random rnd = new Random(27);
        for (int i = 0; i < 300_000; i++) {
            checkTritwise(randomWord(rnd), randomWord(rnd));
        }
    }

    private static void checkTritwise(int a, int b) {
        check("MIN", a, b, WordMath.min(a, b), Math::min);
        check("MAX", a, b, WordMath.max(a, b), Math::max);
        check("TMUL", a, b, WordMath.tmul(a, b), (x, y) -> x * y);
    }

    private static void check(String name, int a, int b, int actual, IntBinaryOperator perTrit) {
        int[] ta = Word.toTrits(a);
        int[] tb = Word.toTrits(b);
        int[] expected = new int[12];
        for (int i = 0; i < 12; i++) {
            expected[i] = perTrit.applyAsInt(ta[i], tb[i]);
        }
        int e = Word.fromTrits(expected);
        if (actual != e) {
            fail(name + "(" + a + ", " + b + ") = " + actual + ", expected " + e);
        }
    }

    // ------------------------------------------------------------- rot

    @Test
    void rotExamples() {
        assertEquals(MAX, WordMath.rot(0, 1));                   // every 0 becomes +
        assertEquals(MIN, WordMath.rot(0, 2));                   // every 0 becomes -
        assertEquals(MIN, WordMath.rot(MAX, 1));                 // + wraps to -
        assertEquals(Word.parse("+++++++++0+-"), WordMath.rot(Word.parse("000000000-0+"), 1));
        assertEquals(WordMath.rot(77, 2), WordMath.rot(77, -1)); // down once = up twice
        assertEquals(77, WordMath.rot(77, 0));
        assertEquals(77, WordMath.rot(77, 3));
        assertEquals(WordMath.rot(77, 1), WordMath.rot(77, 121));
    }

    @Test
    void rotMatchesTritByTritReferenceForEveryWord() {
        int[] t = new int[12];
        int[] r = new int[12];
        for (int v = MIN; v <= MAX; v++) {
            Word.toTrits(v, t);
            for (int n = 1; n <= 2; n++) {
                Word.toTrits(WordMath.rot(v, n), r);
                for (int i = 0; i < 12; i++) {
                    if (r[i] != Trit.rotate(t[i], n)) {
                        fail("ROT " + v + " by " + n + ": trit " + i);
                    }
                }
            }
            if (WordMath.rot(WordMath.rot(WordMath.rot(v, 1), 1), 1) != v) {
                fail("three ROTs must return " + v);
            }
        }
    }

    // ------------------------------------------------------------- sht

    @Test
    void shtLeftMultipliesByPowersOfThree() {
        assertEquals(18, WordMath.sht(6, 1));
        assertEquals(6 * 243, WordMath.sht(6, 5));
        assertEquals(Word.parse("+-0000000000"), WordMath.sht(6, 9));
        assertEquals(Word.parse("-00000000000"), WordMath.sht(6, 10));  // "+" pushed out
        assertEquals(0, WordMath.sht(6, 11));                                      // all pushed out
        assertEquals(0, WordMath.sht(MAX, 12));
        assertEquals(0, WordMath.sht(MAX, 121));
    }

    @Test
    void shtRightRoundsToNearest() {
        // Drop the low trit of 7 = +-+ → +- = 2; and 7 ÷ 3 = 2.33 rounds to 2.
        assertEquals(2, WordMath.sht(7, -1));
        // 8 = +0- → +0 = 3; 8 ÷ 3 = 2.67 rounds to 3 (binary shifting would give 2).
        assertEquals(3, WordMath.sht(8, -1));
        assertEquals(-3, WordMath.sht(-8, -1));
        assertEquals(1, WordMath.sht(MAX, -11));
        assertEquals(0, WordMath.sht(MAX, -12));
        assertEquals(0, WordMath.sht(MIN, -121));
    }

    @Test
    void shtMatchesItsDefinitionForEveryWordAndEveryShift() {
        for (int k = 1; k <= 11; k++) {
            long p = Ternary.pow3(k);
            for (int v = MIN; v <= MAX; v++) {
                // Right shift = round to nearest (3^k is odd, so there are no ties).
                long nearest = Math.floorDiv(2L * v + p, 2 * p);
                if (WordMath.sht(v, -k) != nearest) {
                    fail("SHT " + v + ", " + -k + " = " + WordMath.sht(v, -k) + ", expected " + nearest);
                }
                if (WordMath.sht(v, k) != Word.wrap(v * p)) {
                    fail("SHT " + v + ", " + k);
                }
            }
        }
    }

    @Test
    void shtRightDropsTheLowTrits() {
        Random rnd = new Random(9);
        for (int i = 0; i < 50_000; i++) {
            int v = randomWord(rnd);
            int k = 1 + rnd.nextInt(11);
            int[] t = Word.toTrits(v);
            int[] shifted = new int[12];
            System.arraycopy(t, k, shifted, 0, 12 - k);
            assertEquals(Word.fromTrits(shifted), WordMath.sht(v, -k), "SHT " + v + ", " + -k);
        }
    }

    private static int randomWord(Random rnd) {
        return rnd.nextInt(Word.COUNT) + MIN;
    }
}
