package org.triada.assembler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.triada.core.cpu.Instruction.encode;
import static org.triada.core.cpu.Instruction.reg;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;
import org.triada.core.cpu.Instruction;

class AssemblerTest {

    static AssemblyResult asm(String source) {
        try {
            return new Assembler(SourceLoader.NONE).assembleSource("test.tasm", source);
        } catch (AssemblyException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    static int[] words(String source) {
        return asm(source).image().words();
    }

    private static int i(int op, int rd, int rs, int imm) {
        return encode(op, reg(rd), reg(rs), imm);
    }

    // ================================================================ the spec's example

    @Test
    void theExampleFromSpecSection9() {
        String src = String.join("\n",
                "; comment",
                "NAME = 59049              ; constant",
                "        .org 243",
                "start:  LDI R0, msg       ; label as immediate",
                "        JSR PRINT",
                "loop:   WAIT",
                "        JMP loop",
                "msg:    .str \"HELLO, WORLD\"");
        AssemblyResult r = asm(src);
        TriImage img = r.image();
        assertEquals(243, img.loadAddress());
        int msg = 243 + 2 + 2 + 1 + 2;          // LDI(2) JSR(2) WAIT(1) JMP(2)
        assertEquals(msg, r.symbols().get("msg"));
        assertEquals(243, r.symbols().get("start"));
        assertEquals(247, r.symbols().get("loop"));
        assertEquals(59_049, r.symbols().get("NAME"));
        int[] expected = {
            i(Instruction.LDI, 0, 4, 0), msg,
            i(Instruction.JSR, 4, 4, 0), -19_679,
            i(Instruction.WAIT, 4, 4, 0),
            i(Instruction.JMP, 4, 4, 0), 247,
            'H', 'E', 'L', 'L', 'O', ',', ' ', 'W', 'O', 'R', 'L', 'D', 0,
        };
        assertArrayEquals(expected, img.words());
    }

    // ================================================================ every instruction form

    @Test
    void everyRealInstructionEncodesAsTheSpecSays() {
        String src = String.join("\n",
                "HLT", "NOP", "MOV R1, R2", "LDI R3, -1000", "LD R4, [R5+7]", "ST R6, [R7-121]",
                "PUSH R8", "POP R0", "ADD R1, R2", "SUB R2, R3", "ADDI R3, 121", "MUL R4, R5",
                "DIV R5, R6", "MOD R6, R7", "NEG R7", "MIN R8, R0", "MAX R0, R1", "TMUL R1, R2",
                "ROT R2, -1", "SHT R3, -5", "CMP R4, R5", "JMP 300", "JSR 400", "RET",
                "JIF 2, 500", "BR3 1, 2, 3", "WAIT");
        int[] expected = {
            i(0, 4, 4, 0), i(1, 4, 4, 0), i(2, 1, 2, 0), i(3, 3, 4, 0), -1000, i(4, 4, 5, 7),
            i(5, 6, 7, -121), i(6, 8, 4, 0), i(7, 0, 4, 0), i(8, 1, 2, 0), i(9, 2, 3, 0),
            i(10, 3, 4, 121), i(11, 4, 5, 0), i(12, 5, 6, 0), i(13, 6, 7, 0), i(-1, 7, 4, 0),
            i(-2, 8, 0, 0), i(-3, 0, 1, 0), i(-4, 1, 2, 0), i(-5, 2, 4, -1), i(-6, 3, 4, -5),
            i(-7, 4, 5, 0), i(-8, 4, 4, 0), 300, i(-9, 4, 4, 0), 400, i(-10, 4, 4, 0),
            encode(Instruction.JIF, 2, 0, 0), 500, i(-12, 4, 4, 0), 1, 2, 3, i(-13, 4, 4, 0),
        };
        assertArrayEquals(expected, words(src));
    }

    @Test
    void pseudoInstructions() {
        String src = "JN 10\nJZ 11\nJP 12\nJNZ 13\nJLE 14\nJGE 15\nINC R1\nDEC R2\nROT R3";
        int[] expected = {
            encode(Instruction.JIF, -1, 0, 0), 10, encode(Instruction.JIF, 0, 0, 0), 11,
            encode(Instruction.JIF, 1, 0, 0), 12, encode(Instruction.JIF, 2, 0, 0), 13,
            encode(Instruction.JIF, -2, 0, 0), 14, encode(Instruction.JIF, 3, 0, 0), 15,
            i(Instruction.ADDI, 1, 4, 1), i(Instruction.ADDI, 2, 4, -1), i(Instruction.ROT, 3, 4, 1),
        };
        assertArrayEquals(expected, words(src));
    }

    @Test
    void memoryOperandShapes() {
        int[] w = words("LD R0, [R1]\nLD R0, [R1+5]\nLD R0, [R1-3]\nLD R0, [R1 + KEY]\nLD R0, [R1-3+1]\nLD R0, [SP+1]");
        assertEquals(i(Instruction.LD, 0, 1, 0), w[0]);
        assertEquals(i(Instruction.LD, 0, 1, 5), w[1]);
        assertEquals(i(Instruction.LD, 0, 1, -3), w[2]);
        assertEquals(i(Instruction.LD, 0, 1, 6), w[3], "KEY = 6");
        assertEquals(i(Instruction.LD, 0, 1, -2), w[4], "[R1-3+1] means R1 + (-3 + 1)");
        assertEquals(i(Instruction.LD, 0, 8, 1), w[5], "SP is R8");
    }

    @Test
    void mnemonicsAndRegistersAreCaseInsensitive() {
        assertArrayEquals(words("ADD R1, R2\nLDI R0, 5"), words("add r1, r2\nLdI r0, 5"));
    }

    // ================================================================ numbers and expressions

    @Test
    void numberNotations() {
        assertArrayEquals(new int[] {123, -7, 6, -2, 65, 10, 0}, words(".word 123, -7, 0t+-0, 0t-+, 'A', '\\n', 0"));
    }

    @Test
    void expressionsCombineNumbersLabelsAndConstants() {
        String src = String.join("\n",
                "BASE = 1000",
                "OFF = BASE - 10 + 'A'",
                "here:   .word here + 1, -here, BASE - -5, OFF, IO_BASE + KEY, there - here",
                "there:  .word -0t+");
        int here = 243;
        assertArrayEquals(new int[] {here + 1, -here, 1005, 1055, -20_406, 6, -1}, words(src));
    }

    @Test
    void forwardReferencesWork() {
        int[] w = words("JMP end\nNOP\nend: HLT");
        assertEquals(246, w[1]);
    }

    @Test
    void constantsMayUseLaterLabelsAndOtherConstants() {
        int[] w = words("LDI R0, SIZE\nSIZE = last - first\nfirst: .fill 3, 9\nlast: HLT");
        assertEquals(3, w[1]);
    }

    @Test
    void labelsAreCaseSensitive() {
        Map<String, Integer> s = asm("loop: NOP\nLoop: NOP").symbols();
        assertEquals(243, s.get("loop"));
        assertEquals(244, s.get("Loop"));
    }

    @Test
    void builtinSymbols() {
        String src = ".word RESET, PUTCHAR, PRINT, PRINTNUM, CLS, GETKEY, LOCATE, IO_BASE,"
                + " FRAME, KEY, RAND, HOST_EXIT, TILES, MAP, SPRITES, V3_WAVE";
        assertArrayEquals(new int[] {-19_683, -19_681, -19_679, -19_677, -19_675, -19_673, -19_671, -20_412,
            0, 6, 19, 22, 40_000, 44_000, 44_600, 18}, words(src));
    }

    // ================================================================ directives

    @Test
    void orgWithoutOrgStartsAt243() {
        assertEquals(243, asm("NOP").image().loadAddress());
        assertEquals(243, Assembler.DEFAULT_ORIGIN);
    }

    @Test
    void orgBlocksWithGapsAreFilledWithZeros() {
        TriImage img = asm(".org 300\n.word 1\n.org 296\n.word 2, 3").image();
        assertEquals(296, img.loadAddress());
        assertArrayEquals(new int[] {2, 3, 0, 0, 1}, img.words());
    }

    @Test
    void orgCanTargetRom() {
        TriImage img = asm(".org -19683\nJMP boot\nboot: HLT").image();
        assertEquals(-19_683, img.loadAddress());
        assertEquals(-19_681, img.word(1));
    }

    @Test
    void labelOnAnOrgLineGetsTheNewAddress() {
        assertEquals(500, asm("NOP\nstart: .org 500\nNOP").symbols().get("start"));
    }

    @Test
    void strAddsATerminatingZero() {
        assertArrayEquals(new int[] {'H', 'I', 0, 'x', 0, 0}, words(".str \"HI\"\n.str \"x\"\n.str \"\""));
    }

    @Test
    void fillRepeatsAWord() {
        assertArrayEquals(new int[] {7, 7, 7, 1}, words(".fill 3, 7\n.word 1"));
        assertEquals(0, words(".fill 0, 5").length);
    }

    @Test
    void pixPacksFourPixels() {
        // SPEC §4.3: word = p0·19683 + p1·729 + p2·27 + p3
        assertArrayEquals(new int[] {13 * 19_683 + 5 * 729 - 11 * 27 + 0},
                words(".pix 13, 5, -11, 0"));
        assertArrayEquals(new int[] {-13 * 19_683 - 13 * 729 - 13 * 27 - 13}, words(".pix -13, -13, -13, -13"));
    }

    @Test
    void directivesAreCaseInsensitive() {
        assertArrayEquals(words(".word 5"), words(".WORD 5"));
    }

    // ================================================================ include

    @Test
    void includeInsertsAnotherFileRelativeToTheIncluder() throws AssemblyException {
        Map<String, String> files = Map.of(
                "games/main.tasm", "JSR helper\nHLT\n.include \"lib/helper.tasm\"",
                "games/lib/helper.tasm", "helper: LDI R0, VALUE\nRET\nVALUE = 42");
        AssemblyResult r = new Assembler(files::get).assemble("games/main.tasm");
        assertEquals(246, r.symbols().get("helper"));
        assertEquals(42, r.image().word(4));
        assertTrue(r.listing().contains("; ---- games/lib/helper.tasm ----"), r.listing());
    }

    @Test
    void resolveHandlesFoldersAndAbsolutePaths() {
        assertEquals("a/b.tasm", Assembler.resolve("a/main.tasm", "b.tasm"));
        assertEquals("b.tasm", Assembler.resolve("main.tasm", "b.tasm"));
        assertEquals("a/x/b.tasm", Assembler.resolve("a\\main.tasm", "x\\b.tasm"));
        assertEquals("/abs/b.tasm", Assembler.resolve("a/main.tasm", "/abs/b.tasm"));
        assertEquals("C:/abs/b.tasm", Assembler.resolve("a/main.tasm", "C:\\abs\\b.tasm"));
    }

    // ================================================================ listing

    @Test
    void listingShowsAddressTernaryDecimalLineAndSource() {
        String listing = asm("; hello\nstart: LDI R0, 6\n.fill 20, 0").listing();
        String[] rows = listing.split("\n");
        assertTrue(rows[0].startsWith("; TRIADA-85 assembler listing"));
        assertTrue(rows[1].contains("ADDRESS") && rows[1].contains("TERNARY") && rows[1].contains("SOURCE"));
        assertTrue(listing.contains("    1  ; hello"), listing);
        int ldi = i(Instruction.LDI, 0, 4, 0);
        String expectedRow = "     243  " + org.triada.core.ternary.Word.toString(ldi) + "  "
                + String.format("%8d", ldi) + "      2  start: LDI R0, 6";
        assertTrue(listing.contains(expectedRow), listing);
        assertTrue(listing.contains("     244  000000000+-0         6"), listing);
        assertTrue(listing.contains("(13 more words, up to address 264)"), listing);
    }

    @Test
    void lineEndingsOfAllKindsWork() {
        assertArrayEquals(words("NOP\nHLT\n"), words("NOP\r\nHLT\r\n"));
        assertArrayEquals(words("NOP\nHLT"), words("NOP\rHLT"));
        assertEquals(java.util.List.of("a", "", "b"), Assembler.splitLines("a\n\nb\n"));
    }

    @Test
    void emptySourceGivesAnEmptyImage() {
        TriImage img = asm("; nothing here\n\n").image();
        assertEquals(0, img.size());
        assertEquals(243, img.loadAddress());
    }
}
