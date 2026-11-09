package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.core.TriImage;

/** {@code --model} picks the machine for tasm and run (M16); today only triada-85 exists. */
class ModelOptionTest {

    @TempDir
    Path dir;

    @Test
    void tasmAcceptsTheDefaultModelAndItsSymbols() throws Exception {
        String src = Cli.write(dir, "p.tasm", ".word MAP, MAP_CELLS, STACK_TOP, PROGRAM_START\n");
        Cli.Result r = Cli.run("tasm", src, "--model", "triada-85");
        assertEquals(0, r.code(), r.toString());
        TriImage image = TriImage.fromBytes(Files.readAllBytes(dir.resolve("p.tri")));
        assertEquals(44_000, image.word(0));
        assertEquals(486, image.word(1));
        assertEquals(59_049, image.word(2));
        assertEquals(243, image.word(3));
    }

    @Test
    void runAcceptsTheDefaultModel() throws Exception {
        String src = Cli.write(dir, "p.tasm", "LDI R0, IO_BASE\nLDI R1, 7\nST R1, [R0+HOST_EXIT]\nHLT\n");
        assertEquals(7, Cli.run("run", src, "--model", "TRIADA-85").code());
    }

    @Test
    void anUnknownModelIsAUsageError() throws Exception {
        String src = Cli.write(dir, "p.tasm", "HLT\n");
        Cli.Result r = Cli.run("run", src, "--model", "triada-88");
        assertEquals(Main.EXIT_USAGE, r.code(), r.toString());
        assertTrue(r.err().contains("no such model 'triada-88' (known: triada-85)"), r.toString());
    }
}
