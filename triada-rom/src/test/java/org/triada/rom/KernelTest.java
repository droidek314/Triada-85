package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;
import org.triada.core.video.Palette;

/**
 * The kernel: boot, font, chime, and the public routines (SPEC 8.1, 8.2),
 * tested with small TRIADA programs started through AUTORUN.
 */
class KernelTest {

    // ================================================================ jump table

    @Test
    void jumpTableEntriesAreJmpsAtTheSpecAddresses() {
        String[] names = {"RESET", "PUTCHAR", "PRINT", "PRINTNUM", "CLS", "GETKEY", "LOCATE",
            "DISKSAVE", "DISKLOAD", "DISKDIR", "DISKDEL"};
        String[] targets = {"boot", "putchar", "print", "printnum", "cls", "getkey", "locate",
            "disksave", "diskload", "diskdir", "diskdel"};
        var rom = Rom.image();
        for (int i = 0; i < names.length; i++) {
            int address = -19_683 + 2 * i;                        // SPEC 8.1 table
            int index = address - MemoryMap.ROM_START;
            assertEquals(org.triada.core.cpu.Instruction.encode(org.triada.core.cpu.Instruction.JMP, 0, 0, 0),
                    rom.word(index), names[i] + " is a JMP");
            assertEquals(Rom.address(targets[i]), rom.word(index + 1), names[i] + " jumps to " + targets[i]);
        }
    }

    @Test
    void theRomFitsAndStartsAtTheFirstRomWord() {
        assertEquals(MemoryMap.ROM_START, Rom.image().loadAddress());
        assertTrue(Rom.image().size() < MemoryMap.ROM_SIZE / 4, "plenty of room left: " + Rom.image().size());
    }

    // ================================================================ boot

    @Test
    void bootSetsTheStackColoursAndCursor() {
        Triada t = Triada.booted();
        assertEquals(Palette.rgb(-11), t.m.video().frame()[0], "blue border");
        assertEquals(-12, t.m.video().screenPixel(200, 140), "dark blue screen (a space tile)");
        assertTrue(t.m.cpu().register(8) <= 59_049 && t.m.cpu().register(8) > 59_000, "SP near the top of RAM");
    }

    @Test
    void bootChimePlaysCEGAndEndsInSilence() {
        Triada t = new Triada();
        boolean heardC = false;
        boolean heardChord = false;
        for (int f = 0; f < 15; f++) {
            t.frames(1);
            heardC |= reg(t, IoRegisters.V1_FREQ) == 523 && reg(t, IoRegisters.V1_VOL) == 13;
            heardChord |= reg(t, IoRegisters.V2_FREQ) == 659 && reg(t, IoRegisters.V3_FREQ) == 784
                    && reg(t, IoRegisters.V3_VOL) == 13 && reg(t, IoRegisters.V3_WAVE) == 1;
        }
        assertTrue(heardC, "C5 on voice 1");
        assertTrue(heardChord, "C-E-G ternary squares at full volume");
        t.waitForPrompt();
        for (int v = 0; v < 3; v++) {
            assertEquals(0, reg(t, IoRegisters.V1_FREQ + 3 * v), "voice " + (v + 1) + " off after boot");
            assertEquals(0, reg(t, IoRegisters.V1_VOL + 3 * v));
        }
        for (short s : t.m.sound().frameSamples()) {
            assertEquals(0, s);
        }
    }

    @Test
    void bootIsQuick() {
        Triada t = new Triada();
        int frames = 0;
        while (!(t.cursorX() == 2 && t.row(3).startsWith(">"))) {
            t.frames(1);
            frames++;
        }
        assertTrue(frames < 60, "boot to prompt in " + frames + " frames (under a second)");
    }

    private static int reg(Triada t, int k) {
        return t.m.bus().read(MemoryMap.IO_BASE + k);
    }

    // ================================================================ font

    /**
     * Every pixel of tiles 32 ... 126 against the ORIGINAL font file
     * (rom/third_party/font8x8/font8x8_basic.h): bit i of row r set means
     * pixel i of that row is the text colour, otherwise the background.
     */
    @Test
    void fontTilesMatchTheOriginalFontBitForBit() throws Exception {
        Triada t = Triada.booted();
        Path header = Path.of(System.getProperty("triada.root", ".."), "rom/third_party/font8x8/font8x8_basic.h");
        Matcher m = Pattern.compile("\\{\\s*(0x[^}]*)\\},\\s*// U\\+([0-9A-F]{4})").matcher(Files.readString(header));
        int checked = 0;
        while (m.find()) {
            int code = Integer.parseInt(m.group(2), 16);
            if (code < 32 || code > 126) {
                continue;
            }
            String[] bytes = m.group(1).split(",");
            for (int row = 0; row < 8; row++) {
                int bits = Integer.decode(bytes[row].trim());
                int[] pixels = tileRow(t, code, row);
                for (int x = 0; x < 8; x++) {
                    int expected = (bits >> x & 1) == 1 ? 4 : -12;
                    assertEquals(expected, pixels[x], "character " + code + " row " + row + " pixel " + x);
                }
            }
            checked++;
        }
        assertEquals(95, checked);
    }

    /** The 8 pixel values of row r of tile t, unpacked with SPEC 4.3's formula. */
    private static int[] tileRow(Triada t, int tile, int row) {
        int[] p = new int[8];
        for (int half = 0; half < 2; half++) {
            int w = t.m.bus().read(MemoryMap.TILES + 16 * tile + 2 * row + half);
            int u = w + 265_720;
            p[4 * half] = u / 19_683 - 13;
            p[4 * half + 1] = u / 729 % 27 - 13;
            p[4 * half + 2] = u / 27 % 27 - 13;
            p[4 * half + 3] = u % 27 - 13;
        }
        return p;
    }

    // ================================================================ routines

    @Test
    void printnumPrintsDecimalNumbers() {
        Triada t = Triada.autorun("""
                        JSR CLS
                        LDI R3, nums
                next:   LD R0, [R3]
                        JSR PRINTNUM
                        LDI R0, ' '
                        JSR PUTCHAR
                        INC R3
                        LDI R1, end
                        CMP R3, R1
                        JN next
                        RET
                nums:   .word 0, 7, -5, 10, 100, 12345, 265720, -265720
                end:
                """);
        t.waitForPrompt();
        assertEquals("0 7 -5 10 100 12345 265720", t.row(0));
        assertEquals("-265720", t.row(1).substring(0, 7), "the 28th character wrapped to the next line");
    }

    @Test
    void putcharWrapsAFullLineOnlyWhenTheNextCharacterComes() {
        // 27 'A's then a newline then 'B': exactly two lines, no empty line between.
        Triada t = Triada.autorun("""
                        JSR CLS
                        LDI R3, 27
                more:   LDI R0, 'A'
                        JSR PUTCHAR
                        DEC R3
                        JNZ more
                        LDI R0, 10
                        JSR PUTCHAR
                        LDI R0, 'B'
                        JSR PUTCHAR
                        LDI R0, 10
                        JSR PUTCHAR
                        RET
                """);
        t.waitForPrompt();
        assertEquals("A".repeat(27), t.row(0));
        assertEquals("B", t.row(1));
        assertEquals("READY.", t.row(2));
    }

    @Test
    void putcharWithoutNewlineWrapsToTheNextLine() {
        Triada t = Triada.autorun("""
                        JSR CLS
                        LDI R3, 30
                more:   LDI R0, 'x'
                        JSR PUTCHAR
                        DEC R3
                        JNZ more
                        LDI R0, 10
                        JSR PUTCHAR
                        RET
                """);
        t.waitForPrompt();
        assertEquals("x".repeat(27), t.row(0));
        assertEquals("xxx", t.row(1));
    }

    @Test
    void backspaceErasesAndStopsAtColumn0() {
        Triada t = Triada.autorun("""
                        JSR CLS
                        LDI R0, msg
                        JSR PRINT
                        RET
                msg:    .word 'A', 'B', 'C', 8, 8, 'X', 10, 8, 8, 'Y', 10, 0   ; 8 = backspace
                """);
        t.waitForPrompt();
        assertEquals("AX", t.row(0));
        assertEquals("Y", t.row(1), "backspace at column 0 does nothing");
    }

    @Test
    void locateMovesTheCursorAndClampsToTheScreen() {
        Triada t = Triada.autorun("""
                        JSR CLS
                        LDI R0, 5
                        LDI R1, 3
                        JSR LOCATE
                        LDI R0, '*'
                        JSR PUTCHAR
                        LDI R0, 100
                        LDI R1, -7
                        JSR LOCATE
                        LDI R0, '#'
                        JSR PUTCHAR
                        LDI R0, 0
                        LDI R1, 6
                        JSR LOCATE
                        RET
                """);
        t.waitForPrompt();
        assertEquals("     *", t.row(3));
        assertEquals("                          #", t.row(0), "column 100 -> 26, row -7 -> 0");
        assertEquals("READY.", t.row(6));
    }

    @Test
    void scrollingKeepsTheLastLines() {
        Triada t = Triada.autorun("""
                        JSR CLS
                        LDI R3, 1
                line:   MOV R0, R3
                        JSR PRINTNUM
                        LDI R0, 10
                        JSR PUTCHAR
                        INC R3
                        LDI R1, 26
                        CMP R3, R1
                        JN line
                        RET
                """);
        t.waitForPrompt();
        // Lines 1 ... 25, then READY. and the prompt: 27 lines on 18 rows.
        assertEquals("10", t.row(0));
        assertEquals("25", t.row(15));
        assertEquals("READY.", t.row(16));
        assertEquals(">", t.row(17));
    }

    @Test
    void getkeyWaitsForAKey() {
        Triada t = Triada.autorun("""
                        JSR GETKEY
                        LDI R1, IO_BASE
                        ST R0, [R1+DEBUG_NUM]
                        RET
                """);
        t.frames(120);                                   // boot, then waiting ...
        assertEquals("", t.m.debug().takeOutput(), "nothing yet: GETKEY waits");
        t.m.input().pushKey('K');
        t.frames(2);
        assertEquals("75\n", t.m.debug().takeOutput());
    }

    /**
     * SPEC 8.1: "Routines may change R0-R2 and must preserve R3-R8." The
     * program fills R3 ... R7 with markers, calls every routine, and reports
     * through HOST_EXIT how many registers changed (and SP separately).
     */
    @Test
    void everyRoutineKeepsR3ToR8() {
        Triada t = Triada.autorun("""
                        LDI R3, 3333
                        LDI R4, -4444
                        LDI R5, 5555
                        LDI R6, -6666
                        LDI R7, 7777
                        MOV R2, SP
                        LDI R1, IO_BASE
                        ST R2, [R1+DEBUG_NUM]   ; SP before
                        JSR CLS
                        LDI R0, 'Z'
                        JSR PUTCHAR
                        LDI R0, 10
                        JSR PUTCHAR
                        LDI R0, 8
                        JSR PUTCHAR
                        LDI R0, msg
                        JSR PRINT
                        LDI R0, -12345
                        JSR PRINTNUM
                        LDI R0, 3
                        LDI R1, 4
                        JSR LOCATE
                        JSR GETKEY
                        LDI R1, IO_BASE
                        MOV R2, SP
                        ST R2, [R1+DEBUG_NUM]   ; SP after
                        ST R3, [R1+DEBUG_NUM]
                        ST R4, [R1+DEBUG_NUM]
                        ST R5, [R1+DEBUG_NUM]
                        ST R6, [R1+DEBUG_NUM]
                        ST R7, [R1+DEBUG_NUM]
                        RET
                msg:    .str "PRINT\\n"
                """);
        t.frames(100);
        t.m.input().pushKey('!');
        t.frames(3);
        String[] out = t.m.debug().takeOutput().split("\n");
        assertEquals(out[0], out[1], "SP unchanged");
        assertEquals("3333", out[2]);
        assertEquals("-4444", out[3]);
        assertEquals("5555", out[4]);
        assertEquals("-6666", out[5]);
        assertEquals("7777", out[6]);
    }

    // ================================================================ golden boot screen

    @Test
    void goldenBootScreen() throws Exception {
        Triada t = Triada.booted();
        // Freeze the blinking cursor so the picture doesn't depend on the frame number.
        t.m.bus().write(MemoryMap.MAP + 3 * 27 + 2, ' ');
        t.m.video().render();
        java.awt.image.BufferedImage actual = new java.awt.image.BufferedImage(240, 168,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        actual.setRGB(0, 0, 240, 168, t.m.video().frame(), 0, 240);
        Path golden = Path.of(System.getProperty("triada.root", ".."), "triada-rom/src/test/resources/golden/boot.png");
        if (Boolean.getBoolean("triada.updateGolden")) {
            Files.createDirectories(golden.getParent());
            javax.imageio.ImageIO.write(actual, "png", golden.toFile());
            return;
        }
        assertTrue(Files.exists(golden), "missing " + golden);
        java.awt.image.BufferedImage expected = javax.imageio.ImageIO.read(golden.toFile());
        for (int y = 0; y < 168; y++) {
            for (int x = 0; x < 240; x++) {
                assertEquals(expected.getRGB(x, y) & 0xFFFFFF, actual.getRGB(x, y) & 0xFFFFFF,
                        "boot screen differs at (" + x + ", " + y + ")");
            }
        }
    }
}
