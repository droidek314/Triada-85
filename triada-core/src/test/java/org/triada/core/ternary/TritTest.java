package org.triada.core.ternary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TritTest {

    @Test
    void charactersFollowSpecNotation() {
        assertEquals('-', Trit.toChar(-1));
        assertEquals('0', Trit.toChar(0));
        assertEquals('+', Trit.toChar(1));
        assertEquals(-1, Trit.fromChar('-'));
        assertEquals(0, Trit.fromChar('0'));
        assertEquals(1, Trit.fromChar('+'));
    }

    @Test
    void invalidTritsAreRejected() {
        assertFalse(Trit.isValid(2));
        assertFalse(Trit.isValid(-2));
        assertTrue(Trit.isValid(0));
        assertThrows(IllegalArgumentException.class, () -> Trit.toChar(2));
        assertThrows(IllegalArgumentException.class, () -> Trit.fromChar('1'));
        assertFalse(Trit.isTritChar('x'));
    }

    @Test
    void rotateUpGoesMinusZeroPlusAndAround() {
        assertEquals(0, Trit.rotate(-1, 1));
        assertEquals(1, Trit.rotate(0, 1));
        assertEquals(-1, Trit.rotate(1, 1));
    }

    @Test
    void rotateUsesMathematicalModThree() {
        for (int t = -1; t <= 1; t++) {
            assertEquals(t, Trit.rotate(t, 0));
            assertEquals(t, Trit.rotate(t, 3));
            assertEquals(Trit.rotate(t, 2), Trit.rotate(t, -1), "down once = up twice");
            // The imm5 extremes: 121 mod 3 = 1, and −121 mod 3 = 2 (mathematical mod).
            assertEquals(Trit.rotate(t, 1), Trit.rotate(t, 121));
            assertEquals(Trit.rotate(t, 2), Trit.rotate(t, -121));
        }
    }
}
