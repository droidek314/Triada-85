package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.assembler.Assembler;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.core.triskette.TrisketteDevice;
import org.triada.rom.Rom;

/**
 * The triskette as a folder on the PC (M13): the host round trip, the folder's
 * rules, {@code triada run --disk}, and a released game stored on a triskette,
 * loaded from it by a TRIADA program and run.
 */
class TrisketteTest {

    @TempDir
    Path dir;

    /** A program that saves 5 words as NOTES.TB, loads them back to 31000, and exits with the two statuses. */
    private static final String SAVE_AND_LOAD = """
                    LDI R0, name
                    LDI R1, data
                    LDI R2, 5
                    JSR DISKSAVE
                    MOV R3, R0
                    LDI R0, name
                    LDI R1, 31000
                    JSR DISKLOAD
                    LDI R1, 3                   ; exit code = 3 * save status + load status: 0 = both OK
                    MUL R3, R1
                    ADD R0, R3
                    LDI R1, IO_BASE
                    ST R0, [R1+HOST_EXIT]
            name:   .str "notes.tb"
            data:   .word 265720, -1, 0, 0t+-0, -265720
            """;

    @Test
    void hostRoundTripThroughTheCommandLine() throws Exception {
        Path disk = dir.resolve("disk");
        String prog = Cli.write(dir, "save.tasm", SAVE_AND_LOAD);
        Cli.Result r = Cli.run("run", prog, "--disk", disk.toString());
        assertEquals(0, r.code(), r.toString());

        Path file = disk.resolve("NOTES.TB");
        assertTrue(Files.isRegularFile(file), "the file appears on the host");
        TriImage onHost = TriImage.fromBytes(Files.readAllBytes(file));
        assertArrayEquals(new int[] {265_720, -1, 0, 6, -265_720}, onHost.words());

        // ... and a second machine reads it back unchanged
        Machine m = Rom.newMachine();
        FolderTriskette port = new FolderTriskette(disk);
        m.triskette().setPort(port);
        assertEquals(List.of("NOTES.TB"), port.names());
        assertArrayEquals(onHost.words(), port.read("NOTES.TB").words());
        assertEquals(onHost.loadAddress(), port.read("NOTES.TB").loadAddress());
    }

    @Test
    void withoutDiskTheTrisketteIsEmptyAndInMemory() throws Exception {
        String prog = Cli.write(dir, "save.tasm", SAVE_AND_LOAD);
        Cli.Result r = Cli.run("run", prog);
        assertEquals(0, r.code(), r.toString() + "\n(saving and loading still work, in memory)");
        assertFalse(Files.exists(dir.resolve("NOTES.TB")));
    }

    @Test
    void theFolderIgnoresOtherFilesAndMatchesNamesWithoutCase() throws Exception {
        Files.write(dir.resolve("game.tri"), new TriImage(243, new int[] {1, 2}).toBytes());
        Files.writeString(dir.resolve("not a triskette name.txt"), "x");
        Files.createDirectory(dir.resolve("SUBDIR"));
        FolderTriskette port = new FolderTriskette(dir);
        assertEquals(List.of("GAME.TRI"), port.names());
        assertEquals(2, port.read("GAME.TRI").size());

        port.write("GAME.TRI", new TriImage(243, new int[] {9}));
        assertTrue(Files.exists(dir.resolve("game.tri")), "an existing file keeps its spelling");
        assertEquals(1, port.read("GAME.TRI").size());

        assertTrue(port.delete("GAME.TRI"));
        assertFalse(port.delete("GAME.TRI"));
        assertNull(port.read("GAME.TRI"));
        assertEquals(List.of(), new FolderTriskette(dir.resolve("missing")).names(), "no folder yet: empty");
    }

    @Test
    void aBrokenFileOnTheHostIsAnIoError() throws Exception {
        Files.writeString(dir.resolve("BROKEN.TRI"), "this is not a .tri file");
        Cli.Result r = Cli.run("run", Cli.write(dir, "load.tasm", """
                        LDI R0, name
                        LDI R1, -1
                        JSR DISKLOAD
                        LDI R1, IO_BASE
                        NEG R0                      ; exit codes are easier to read positive
                        ST R0, [R1+HOST_EXIT]
                name:   .str "broken.tri"
                """), "--disk", dir.toString());
        assertEquals(-TrisketteDevice.IO_ERROR, r.code(), r.toString());
    }

    /** CLAUDE.md M13: "Existing .tri games become loadable from the triskette." */
    @Test
    void aReleasedGameLoadsFromTheTrisketteAndRuns() throws Exception {
        TriImage game = new Assembler(new FileSourceLoader())
                .assemble(Cli.root().resolve("programs/troika.tasm").toString()).image();
        Files.write(dir.resolve("TROIKA.TRI"), game.toBytes());

        // a loader at 30000, out of the game's way: load TROIKA.TRI home, then jump to it
        TriImage loader = new Assembler(path -> null).assembleSource("loader.tasm", """
                        .org 30000
                        LDI R0, name
                        LDI R1, -1
                        JSR DISKLOAD
                        JNZ failed
                        PUSH R1                     ; jump to the address it was loaded to
                        RET
                failed: HLT
                name:   .str "TROIKA.TRI"
                """).image();
        Machine m = Rom.newMachine();
        m.triskette().setPort(new FolderTriskette(dir));
        Rom.autorun(m, loader);
        for (int f = 0; f < 120; f++) {
            m.runFrame();
        }
        assertFalse(m.cpu().isHalted(), "loaded and running, no fault: " + m.cpu().fault());
        assertEquals(game.word(0), m.bus().read(game.loadAddress()), "the game is in RAM at its own address");
        String title = rowText(m, 0) + rowText(m, 1) + rowText(m, 2);
        assertTrue(title.contains("TROIKA"), "TROIKA's title screen: " + title);
    }

    private static String rowText(Machine m, int row) {
        StringBuilder b = new StringBuilder();
        for (int c = 0; c < 27; c++) {
            int t = m.bus().read(org.triada.core.memory.MemoryMap.MAP + row * 27 + c);
            b.append(t >= 32 && t <= 126 ? (char) t : ' ');
        }
        return b.toString();
    }
}
