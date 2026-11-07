package org.triada.core.ternary;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class WordTest {

    @Test
    void constantsMatchSpec() {
        assertEquals(12, Word.TRITS);
        assertEquals(531_441, Word.COUNT);
        assertEquals(265_720, Word.MAX);
        assertEquals(-265_720, Word.MIN);
        assertEquals(Word.COUNT, Word.MAX - Word.MIN + 1);
    }

    /**
     * The milestone's headline test: all 531,441 word values go to trits and
     * back, and to a string and back. The trits are checked against the
     * definition itself (sum of trit × 3^position, every trit −1/0/+1), which
     * is independent of the code under test. Because balanced ternary has
     * exactly one way to write each number, that proves toTrits is right.
     */
    @Test
    void exhaustiveRoundTripOfEveryWord() {
        int[] trits = new int[Word.TRITS];
        for (int v = Word.MIN; v <= Word.MAX; v++) {
            Word.toTrits(v, trits);

            long sum = 0;
            for (int i = 0; i < Word.TRITS; i++) {
                if (!Trit.isValid(trits[i])) {
                    fail("word " + v + ": trit " + i + " = " + trits[i]);
                }
                sum += trits[i] * (long) Ternary.pow3(i);
            }
            if (sum != v) {
                fail("word " + v + ": trits add up to " + sum);
            }
            if (Word.fromTrits(trits) != v) {
                fail("word " + v + ": fromTrits gives " + Word.fromTrits(trits));
            }

            String s = Word.toString(v);
            if (s.length() != 12 || Word.parse(s) != v) {
                fail("word " + v + ": string \"" + s + "\" parses to " + Word.parse(s));
            }
            // The string is the trit array written backwards (most significant first).
            for (int i = 0; i < Word.TRITS; i++) {
                if (s.charAt(11 - i) != Trit.toChar(trits[i])) {
                    fail("word " + v + ": string \"" + s + "\" disagrees with trit " + i);
                }
            }
        }
    }

    @Test
    void extremesAndZeroAsStrings() {
        assertEquals("++++++++++++", Word.toString(Word.MAX));
        assertEquals("------------", Word.toString(Word.MIN));
        assertEquals("000000000000", Word.toString(0));
        assertEquals("000000000+-0", Word.toString(6));
    }

    @Test
    void tritArrayIsIndexedByPosition() {
        // 6 = +-0: t2 = +1, t1 = −1, t0 = 0, all others 0.
        int[] expected = new int[12];
        expected[2] = 1;
        expected[1] = -1;
        assertArrayEquals(expected, Word.toTrits(6));
    }

    @Test
    void singleTritAccess() {
        int six = Word.parse("+-0");
        assertEquals(0, Word.trit(six, 0));
        assertEquals(-1, Word.trit(six, 1));
        assertEquals(1, Word.trit(six, 2));
        assertEquals(0, Word.trit(six, 11));
        assertEquals(1, Word.trit(Word.MAX, 11));
        assertEquals(-1, Word.trit(Word.MIN, 11));
        assertThrows(IllegalArgumentException.class, () -> Word.trit(0, 12));
        assertThrows(IllegalArgumentException.class, () -> Word.trit(0, -1));
    }

    @Test
    void withTritChangesOnlyThatTrit() {
        assertEquals(Word.parse("+-+"), Word.withTrit(Word.parse("+-0"), 0, 1));
        assertEquals(Word.parse("+00000000000"), Word.withTrit(0, 11, 1));
        assertEquals(Word.MIN, Word.withTrit(Word.parse("0-----------"), 11, -1));
        assertThrows(IllegalArgumentException.class, () -> Word.withTrit(0, 0, 2));
    }

    @Test
    void singleTritAccessAgreesWithArrayForSampledWords() {
        for (int v = Word.MIN; v <= Word.MAX; v += 97) {
            int[] trits = Word.toTrits(v);
            for (int i = 0; i < 12; i++) {
                assertEquals(trits[i], Word.trit(v, i), "word " + v + " trit " + i);
            }
        }
    }

    @Test
    void wrapFormulaFromSpec() {
        assertEquals(Word.MIN, Word.wrap(Word.MAX + 1L));   // one past the top: to the bottom
        assertEquals(Word.MAX, Word.wrap(Word.MIN - 1L));   // one past the bottom: to the top
        assertEquals(0, Word.wrap(Word.COUNT));
        assertEquals(5, Word.wrap(5 + 3L * Word.COUNT));
        assertEquals(-5, Word.wrap(-5 - 7L * Word.COUNT));
        for (int v = -1000; v <= 1000; v++) {
            assertEquals(v, Word.wrap(v), "values in range don't change");
        }
    }

    @Test
    void carryCountsTheThrownAwayRounds() {
        assertEquals(0, Word.carry(Word.MAX));
        assertEquals(1, Word.carry(Word.MAX + 1L));
        assertEquals(-1, Word.carry(Word.MIN - 1L));
        assertEquals(1, Word.carry(2L * Word.MAX));   // largest ADD result
        assertEquals(-1, Word.carry(2L * Word.MIN));  // smallest ADD result
        assertEquals(3, Word.carry(3L * Word.COUNT + 2));
    }

    @Test
    void wrapEqualsDroppingHighTrits() {
        // SPEC §1.3: wrapping = throwing away the trits above t11.
        // 3^12 + 6 is "+000000000+-0" (13 trits); dropping the 13th trit leaves 6.
        assertEquals(6, Word.wrap(Ternary.pow3(12) + 6L));
        assertEquals(6, Word.wrap(-Ternary.pow3(12) + 6L));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Word.toTrits(Word.MAX + 1));
        assertThrows(IllegalArgumentException.class, () -> Word.fromTrits(new int[11]));
        int[] bad = new int[12];
        bad[4] = 2;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Word.fromTrits(bad));
        assertTrue(e.getMessage().contains("trits[4]"), e.getMessage());
        assertTrue(Word.isValid(Word.MIN) && Word.isValid(Word.MAX));
        assertTrue(!Word.isValid(Word.MIN - 1L) && !Word.isValid(Word.MAX + 1L));
    }
}
