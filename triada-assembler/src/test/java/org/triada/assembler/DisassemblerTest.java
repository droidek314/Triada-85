package org.triada.assembler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.triada.core.cpu.Instruction.encode;
import static org.triada.core.cpu.Instruction.reg;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;
import org.triada.core.cpu.Instruction;
import org.triada.core.ternary.Word;

class DisassemblerTest {

    private static String one(int... words) {
        return Disassembler.decode(243, java.util.Arrays.copyOf(words, 4), words.length).text();
    }

    @Test
    void textForEveryForm() {
        assertEquals("HLT", one(encode(Instruction.HLT, 0, 0, 0)));
        assertEquals("ADD R1, R2", one(encode(Instruction.ADD, reg(1), reg(2), 0)));
        assertEquals("NEG R7", one(encode(Instruction.NEG, reg(7), 0, 0)));
        assertEquals("LDI R3, -1000", one(encode(Instruction.LDI, reg(3), 0, 0), -1000));
        assertEquals("LD R0, [R1]", one(encode(Instruction.LD, reg(0), reg(1), 0)));
        assertEquals("LD R0, [R1+5]", one(encode(Instruction.LD, reg(0), reg(1), 5)));
        assertEquals("ST R0, [R8-3]", one(encode(Instruction.ST, reg(0), reg(8), -3)));
        assertEquals("ADDI R2, -121", one(encode(Instruction.ADDI, reg(2), 0, -121)));
        assertEquals("ROT R2, 1", one(encode(Instruction.ROT, reg(2), 0, 1)));
        assertEquals("JMP 300", one(encode(Instruction.JMP, 0, 0, 0), 300));
        assertEquals("JNZ 300", one(encode(Instruction.JIF, 2, 0, 0), 300));
        assertEquals("JLE -5", one(encode(Instruction.JIF, -2, 0, 0), -5));
        assertEquals("BR3 1, 2, 3", one(encode(Instruction.BR3, 0, 0, 0), 1, 2, 3));
    }

    @Test
    void wordsWithoutAProperSpellingBecomeData() {
        int junkNop = encode(Instruction.NOP, 2, 0, 0);            // unused rd field isn't 0
        assertEquals(".word " + junkNop, one(junkNop));
        int badJif = encode(Instruction.JIF, 4, 0, 0);              // condition 4 would fault
        assertEquals(".word " + badJif, one(badJif, 300));
        assertEquals(".word 72", one('H'));                         // 'H' decodes as HLT with imm5 = 72
        int ldi = encode(Instruction.LDI, reg(0), 0, 0);
        assertEquals(".word " + ldi, one(ldi), "LDI whose value word is missing");
    }

    @Test
    void disassemblingFromMemoryWrapsAddresses() {
        int jmp = encode(Instruction.JMP, 0, 0, 0);
        Disassembler.Result r = Disassembler.disassemble(Word.MAX, a -> a == Word.MAX ? jmp : a == Word.MIN ? 77 : 0);
        assertEquals("JMP 77", r.text());
        assertEquals(2, r.length());
        assertEquals(Word.MAX, r.address());
    }

    @Test
    void disassembleAllWalksInstructionByInstruction() {
        TriImage img = AssemblerTest.asm("LDI R0, 5\nloop: DEC R0\nJNZ loop\nHLT").image();
        List<Disassembler.Result> rs = Disassembler.disassembleAll(img.loadAddress(), img.words());
        assertEquals(List.of("LDI R0, 5", "ADDI R0, -1", "JNZ 245", "HLT"), rs.stream().map(Disassembler.Result::text).toList());
        assertEquals(List.of(243, 245, 246, 248), rs.stream().map(Disassembler.Result::address).toList());
    }

    /** M4 acceptance: assemble → disassemble → assemble gives identical words. */
    @Test
    void roundTripOfARealProgram() {
        String src = String.join("\n",
                "        .org 243",
                "start:  LDI R1, IO_BASE",
                "        LD R0, [R1+KEY]",
                "        JZ start",
                "        PUSH R0",
                "        JSR show",
                "        POP R0",
                "        CMP R0, R2",
                "        BR3 start, done, start",
                "done:   HLT",
                "show:   ST R0, [R1+DEBUG_CHAR]",
                "        ROT R3",
                "        SHT R3, -2",
                "        MIN R3, R4",
                "        RET",
                "msg:    .str \"HELLO; WORLD\"",
                "table:  .word start, done, -1, 0t+-0",
                "        .fill 5, -7",
                "        .pix 13, 0, -13, 5");
        TriImage first = AssemblerTest.asm(src).image();
        String disassembled = Disassembler.toSource(first.loadAddress(), first.words());
        TriImage second = AssemblerTest.asm(disassembled).image();
        assertEquals(first, second, disassembled);
    }

    /**
     * The strongest round-trip check: every one of the 531,441 possible words as
     * the first word of an instruction, followed by fixed operand words. The
     * disassembled lines are assembled in batches and must give back exactly
     * the words they came from.
     */
    @Test
    void roundTripOfEveryPossibleFirstWord() throws AssemblyException {
        int[] operands = {1234, -5, Word.MAX};
        Assembler assembler = new Assembler(SourceLoader.NONE);
        int batch = 60_000;
        for (int startWord = Word.MIN; startWord <= Word.MAX; startWord += batch) {
            int endWord = (int) Math.min((long) startWord + batch - 1, Word.MAX);
            StringBuilder src = new StringBuilder(".org ").append(Word.MIN).append('\n');
            List<Integer> expected = new ArrayList<>();
            int[] buf = new int[4];
            for (int w = startWord; w <= endWord; w++) {
                buf[0] = w;
                System.arraycopy(operands, 0, buf, 1, 3);
                Disassembler.Result r = Disassembler.decode(0, buf, 4);
                src.append(r.text()).append('\n');
                for (int x : r.words()) {
                    expected.add(x);
                }
            }
            int[] actual = assembler.assembleSource("roundtrip.tasm", src.toString()).image().words();
            int[] exp = expected.stream().mapToInt(Integer::intValue).toArray();
            if (!java.util.Arrays.equals(exp, actual)) {
                for (int i = 0; i < Math.min(exp.length, actual.length); i++) {
                    if (exp[i] != actual[i]) {
                        fail("round trip differs in batch starting at " + startWord + ", word index " + i
                                + ": expected " + exp[i] + ", got " + actual[i]);
                    }
                }
                fail("round trip length differs: " + exp.length + " vs " + actual.length);
            }
        }
    }

    @Test
    void toSourceStartsWithOrg() {
        assertEquals("        .org 243\n        HLT\n", Disassembler.toSource(243, new int[] {0}));
        assertArrayEquals(new int[] {0}, AssemblerTest.words(Disassembler.toSource(243, new int[] {0})));
    }
}
