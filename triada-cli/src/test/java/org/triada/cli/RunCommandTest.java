package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.core.TriImage;

class RunCommandTest {

    @TempDir
    Path dir;

    private Cli.Result runSource(String source, String... options) throws Exception {
        String src = Cli.write(dir, "prog.tasm", source);
        String[] args = new String[options.length + 2];
        args[0] = "run";
        args[1] = src;
        System.arraycopy(options, 0, args, 2, options.length);
        return Cli.run(args);
    }

    /** M15: a TRIBASIC program typed in by --type; POKE reaches the debug port and HOST_EXIT. */
    @Test
    void typeFeedsTheKeyboardAndTribasicRunsIt() throws Exception {
        String bas = Cli.write(dir, "count.bas", """
                10 FOR I = 1 TO 3: POKE -20391, I: NEXT
                20 POKE -20390, 7
                RUN
                """);
        Cli.Result r = Cli.run("run", "--type", bas, "--frames", "600");
        assertEquals(7, r.code(), r.toString());
        assertEquals("1\n2\n3\n", r.out());
    }

    @Test
    void hostExitBecomesTheExitCode() throws Exception {
        Cli.Result r = runSource("LDI R1, IO_BASE\nLDI R0, 42\nST R0, [R1+HOST_EXIT]\n");
        assertEquals(42, r.code(), r.toString());
        assertEquals("", r.err());
    }

    @Test
    void debugNumAndCharGoToStdout() throws Exception {
        Cli.Result r = runSource("""
                        LDI R1, IO_BASE
                        LDI R0, -265720
                        ST R0, [R1+DEBUG_NUM]
                        LDI R0, 'A'
                        ST R0, [R1+DEBUG_CHAR]
                        LDI R0, 0
                        ST R0, [R1+DEBUG_CHAR]
                        ST R0, [R1+HOST_EXIT]
                """);
        assertEquals(0, r.code(), r.toString());
        assertEquals("-265720\nA<0>", r.out());
    }

    @Test
    void nothingRunsAfterHostExit() throws Exception {
        Cli.Result r = runSource("""
                        LDI R1, IO_BASE
                        LDI R0, 7
                        ST R0, [R1+HOST_EXIT]
                        LDI R0, 'X'
                        ST R0, [R1+DEBUG_CHAR]
                """);
        assertEquals(7, r.code());
        assertEquals("", r.out(), "the X must not be printed");
    }

    @Test
    void outputIsPrintedFrameByFrame() throws Exception {
        // Prints one number per frame forever; --frames 5 stops it after five.
        Cli.Result r = runSource("""
                        LDI R1, IO_BASE
                loop:   LD R0, [R1+FRAME]
                        ST R0, [R1+DEBUG_NUM]
                        WAIT
                        JMP loop
                """, "--frames", "5", "--no-boot");
        assertEquals(0, r.code(), "reaching an explicit frame limit is normal");
        assertEquals("0\n1\n2\n3\n4\n", r.out());
        assertEquals("", r.err());
    }

    @Test
    void hltEndsWithExitCode0AndANote() throws Exception {
        Cli.Result r = runSource("NOP\nHLT\n", "--no-boot");
        assertEquals(0, r.code());
        assertTrue(r.err().contains("CPU halted (HLT) in frame 1, PC = 245"), r.err());
    }

    @Test
    void byDefaultTheRomBootsAndThenStartsTheProgram() throws Exception {
        // PRINT and the font only work after boot: the text must be on the screen.
        Cli.Result r = runSource("""
                        JSR CLS
                        LDI R0, msg
                        JSR PRINT
                        WAIT
                        LDI R1, IO_BASE
                        ST R0, [R1+HOST_EXIT]
                msg:    .str "TERNARY!"
                """, "--dump-screen", dir.resolve("p.png").toString());
        assertEquals(0, r.code(), r.toString());
        java.awt.image.BufferedImage png = javax.imageio.ImageIO.read(dir.resolve("p.png").toFile());
        // The 'T' at text position (0, 0): font row 0 of 'T' is 0x3F = pixels 0..5 in the text colour.
        assertEquals(org.triada.core.video.Palette.rgb(4), png.getRGB(12, 12) & 0xFFFFFF, "text colour");
        assertEquals(org.triada.core.video.Palette.rgb(-12), png.getRGB(12 + 7, 12) & 0xFFFFFF, "background");
    }

    @Test
    void aProgramThatReturnsToTheMonitorEndsTheRun() throws Exception {
        Cli.Result r = runSource("LDI R0, 5\nRET\n");
        assertEquals(0, r.code(), r.toString());
        assertTrue(r.err().contains("the program returned (RET)"), r.err());
    }

    @Test
    void aFaultGivesExitCode3() throws Exception {
        // JIF with condition 4 can't be assembled (D-013), so build the word by hand:
        // opcode -11, rd field 4 = -11*19683 + 4*2187.
        Cli.Result r = runSource(".word " + (-11 * 19_683 + 4 * 2_187) + ", 0\n", "--no-boot");
        assertEquals(Main.EXIT_FAULT, r.code(), r.toString());
        assertTrue(r.err().contains("FAULT 1 (invalid JIF condition) at address 243 in frame 1"), r.err());
    }

    @Test
    void aProgramThatNeverEndsTimesOut() throws Exception {
        Cli.Result r = runSource("loop: WAIT\nJMP loop\n");
        assertEquals(Main.EXIT_TIMEOUT, r.code());
        assertTrue(r.err().contains("stopped after " + RunCommand.DEFAULT_FRAME_LIMIT + " frames"), r.err());
        assertEquals(3600, RunCommand.DEFAULT_FRAME_LIMIT, "one minute of machine time");
    }

    @Test
    void runsADotTriFile() throws Exception {
        String src = Cli.write(dir, "p.tasm", "LDI R1, IO_BASE\nLDI R0, 9\nST R0, [R1+HOST_EXIT]\n");
        assertEquals(0, Cli.run("tasm", src).code());
        assertEquals(9, Cli.run("run", dir.resolve("p.tri").toString()).code());
    }

    @Test
    void theStackPointerIsSetUpLikeTheRomWillDoIt() throws Exception {
        Cli.Result r = runSource("LDI R1, IO_BASE\nST SP, [R1+DEBUG_NUM]\nST R0, [R1+HOST_EXIT]\n", "--no-boot");
        assertEquals("59049\n", r.out());
    }

    @Test
    void imagesOutsideRamAreRejected() throws Exception {
        Path tri = dir.resolve("rom.tri");
        Files.write(tri, new TriImage(-19_683, new int[] {0}).toBytes());
        Cli.Result r = Cli.run("run", tri.toString());
        assertEquals(2, r.code());
        assertTrue(r.err().contains("not inside RAM"), r.err());
    }

    @Test
    void brokenFilesAndBadOptionsGiveExitCode2() throws Exception {
        Path junk = dir.resolve("junk.tri");
        Files.writeString(junk, "definitely not a triskette");
        Cli.Result r = Cli.run("run", junk.toString());
        assertEquals(2, r.code());
        assertTrue(r.err().contains("not a .tri file"), r.err());

        assertEquals(2, Cli.run("run", dir.resolve("missing.tri").toString()).code());
        assertEquals(2, Cli.run("run", dir.resolve("missing.tasm").toString()).code());
        assertEquals(2, Cli.run("run").code());
        assertEquals(2, Cli.run("run", "x.tri", "--frames", "0").code());
        assertEquals(2, Cli.run("run", "x.tri", "--frames", "many").code());
        assertEquals(2, Cli.run("run", "x.tri", "--turbo").code());
    }

    @Test
    void assemblyErrorsInARunGiveExitCode1() throws Exception {
        Cli.Result r = runSource("JMP nowhere\n");
        assertEquals(1, r.code());
        assertTrue(r.err().contains(":1:5: unknown name 'nowhere'"), r.err());
    }

    @Test
    void dumpScreenWritesTheLastFrameAsPng() throws Exception {
        // Red border; one white tile in the top-left map cell.
        Cli.Result r = runSource("""
                        LDI R1, IO_BASE
                        LDI R0, 5
                        ST R0, [R1+BORDER]
                        LDI R2, TILES + 16
                        LDI R0, 265720          ; .pix 13, 13, 13, 13: four white pixels
                        ST R0, [R2]
                        LDI R2, MAP
                        LDI R0, 1
                        ST R0, [R2]             ; map cell (0, 0) = tile 1
                        WAIT                    ; let the chip draw one frame
                        HLT
                """, "--dump-screen", dir.resolve("s.png").toString());
        assertEquals(0, r.code(), r.toString());
        java.awt.image.BufferedImage png = javax.imageio.ImageIO.read(dir.resolve("s.png").toFile());
        assertEquals(240, png.getWidth());
        assertEquals(168, png.getHeight());
        assertEquals(0xFF0000, png.getRGB(0, 0) & 0xFFFFFF, "border");
        assertEquals(0xFFFFFF, png.getRGB(12, 12) & 0xFFFFFF, "first screen pixel: row 0 of tile 1");
        assertEquals(0x808080, png.getRGB(12, 13) & 0xFFFFFF, "row 1 of tile 1 is still 0 = gray");
    }

    @Test
    void dumpScreenProblemsAreReported() throws Exception {
        Cli.Result r = runSource("HLT\n", "--dump-screen", dir.resolve("no/such/folder/s.png").toString());
        assertEquals(2, r.code());
        assertTrue(r.err().contains("can't write"), r.err());
    }
}
