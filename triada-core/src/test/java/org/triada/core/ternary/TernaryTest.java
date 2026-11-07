package org.triada.core.ternary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TernaryTest {

    @Test
    void powersOfThree() {
        assertEquals(1, Ternary.pow3(0));
        assertEquals(243, Ternary.pow3(5));
        assertEquals(59_049, Ternary.pow3(10));
        assertEquals(531_441, Ternary.pow3(12));
    }

    @Test
    void rangesOfTheSpecUnits() {
        assertEquals(1, Ternary.maxValue(1));        // trit
        assertEquals(13, Ternary.maxValue(3));       // pixel (SPEC §4.2)
        assertEquals(121, Ternary.maxValue(5));      // imm5 (SPEC §3.3)
        assertEquals(364, Ternary.maxValue(6));      // tryte
        assertEquals(265_720, Ternary.maxValue(12)); // word
    }

    @Test
    void specExamples() {
        // SPEC §1.1: +-0 = 9 − 3 + 0 = 6, and -+ = −3 + 1 = −2.
        assertEquals(6, Ternary.parse("+-0"));
        assertEquals(-2, Ternary.parse("-+"));
        assertEquals("+-0", Ternary.toString(6, 3));
        assertEquals("-+", Ternary.toString(-2, 2));
    }

    @Test
    void pixelColorsFromSpecTable() {
        // SPEC §4.2: the trits of a pixel value are its color (R, G, B).
        assertEquals("---", Ternary.toString(-13, 3));
        assertEquals("+++", Ternary.toString(13, 3));
        assertEquals("+--", Ternary.toString(5, 3));
        assertEquals("-+-", Ternary.toString(-7, 3));
        assertEquals("--+", Ternary.toString(-11, 3));
        assertEquals("++-", Ternary.toString(11, 3));
    }

    @Test
    void everyValueOfEveryWidthRoundTrips() {
        for (int width = 1; width <= 9; width++) {   // widths 10–12 are covered by WordTest
            int max = Ternary.maxValue(width);
            for (int v = -max; v <= max; v++) {
                String s = Ternary.toString(v, width);
                assertEquals(width, s.length());
                assertEquals(v, Ternary.parse(s));
            }
        }
    }

    @Test
    void leadingZerosDoNotChangeTheValue() {
        assertEquals(6, Ternary.parse("000000000+-0"));
    }

    @Test
    void valueTooWideForWidthIsRejected() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> Ternary.toString(14, 3));
        assertTrue(e.getMessage().contains("does not fit in 3 trits"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Ternary.toString(0, 0));
        assertThrows(IllegalArgumentException.class, () -> Ternary.toString(0, 13));
    }

    @Test
    void parseErrorsNameTheProblem() {
        IllegalArgumentException empty =
                assertThrows(IllegalArgumentException.class, () -> Ternary.parse(""));
        assertTrue(empty.getMessage().contains("empty"), empty.getMessage());

        IllegalArgumentException tooLong =
                assertThrows(IllegalArgumentException.class, () -> Ternary.parse("+++++++++++++"));
        assertTrue(tooLong.getMessage().contains("13 trits"), tooLong.getMessage());

        IllegalArgumentException badChar =
                assertThrows(IllegalArgumentException.class, () -> Ternary.parse("+-1"));
        assertTrue(badChar.getMessage().contains("'1' at position 3"), badChar.getMessage());
    }

    @Test
    void lowestTritOfNegativeNumbers() {
        assertEquals(1, Ternary.lowestTrit(-2));   // -2 = -+  → t0 = +
        assertEquals(-1, Ternary.lowestTrit(2));   //  2 = +-  → t0 = -
        assertEquals(0, Ternary.lowestTrit(-3));
    }
}
