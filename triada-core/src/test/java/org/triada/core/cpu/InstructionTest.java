package org.triada.core.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.triada.core.cpu.Instruction.reg;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.triada.core.ternary.Word;

class InstructionTest {

    @Test
    void encodingLaysTheFieldsOutAsInTheSpec() {
        // ADD R1, R2: opcode 8 = "+0-", rd R1 = −3 = "-0", rs R2 = −2 = "-+", imm5 0 = "00000".
        int word = Instruction.encode(Instruction.ADD, reg(1), reg(2), 0);
        assertEquals("+0-" + "-0" + "-+" + "00000", Word.toString(word));
        assertEquals(8 * 19_683 - 3 * 2_187 - 2 * 243, word);
    }

    @Test
    void imm5SitsInTheLowFiveTrits() {
        assertEquals("000" + "++" + "00" + "+++++",
                Word.toString(Instruction.encode(Instruction.HLT, reg(8), reg(4), 121)));
        assertEquals("---" + "--" + "--" + "-----",
                Word.toString(Instruction.encode(Instruction.WAIT, reg(0), reg(0), -121)));
    }

    @Test
    void everyWordDecodesIntoFieldsThatEncodeBackToIt() {
        for (int w = Word.MIN; w <= Word.MAX; w++) {
            int op = Instruction.opcode(w);
            int rd = Instruction.rdField(w);
            int rs = Instruction.rsField(w);
            int imm = Instruction.imm5(w);
            if (Instruction.encode(op, rd, rs, imm) != w) {
                fail("word " + w + " decodes to " + op + "/" + rd + "/" + rs + "/" + imm);
            }
        }
    }

    @Test
    void registerFieldIsNMinusFour() {
        assertEquals(-4, reg(0));
        assertEquals(0, reg(4));
        assertEquals(4, reg(8));
        assertThrows(IllegalArgumentException.class, () -> reg(9));
        assertThrows(IllegalArgumentException.class, () -> reg(-1));
    }

    @Test
    void outOfRangeFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Instruction.encode(14, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Instruction.encode(0, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Instruction.encode(0, 0, -5, 0));
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> Instruction.encode(0, 0, 0, 122));
        assertTrue(e.getMessage().contains("imm5 122"), e.getMessage());
    }

    @Test
    void all27OpcodesHaveDistinctMnemonics() {
        Set<String> names = new HashSet<>();
        for (int op = -13; op <= 13; op++) {
            names.add(Instruction.mnemonic(op));
        }
        assertEquals(27, names.size());
        assertEquals("HLT", Instruction.mnemonic(0));
        assertEquals("MOD", Instruction.mnemonic(13));
        assertEquals("NEG", Instruction.mnemonic(-1));
        assertEquals("WAIT", Instruction.mnemonic(-13));
    }

    @Test
    void lengthsMatchTheSpecTable() {
        int[] twoWords = {Instruction.LDI, Instruction.JMP, Instruction.JSR, Instruction.JIF};
        for (int op = -13; op <= 13; op++) {
            int expected = op == Instruction.BR3 ? 4 : 1;
            for (int two : twoWords) {
                if (op == two) {
                    expected = 2;
                }
            }
            assertEquals(expected, Instruction.length(op), Instruction.mnemonic(op));
        }
    }

    @Test
    void validConditionsAreMinusTwoToThree() {
        for (int c = -4; c <= 4; c++) {
            assertEquals(c >= -2 && c <= 3, Instruction.isValidCondition(c), "cond " + c);
        }
        assertFalse(Instruction.isValidCondition(4));
    }
}
