package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.core.TriImage;

class TasmCommandTest {

    @TempDir
    Path dir;

    @Test
    void assemblesToADotTriNextToTheSourceByDefault() throws Exception {
        String src = Cli.write(dir, "prog.tasm", "LDI R0, 5\nHLT\n");
        Cli.Result r = Cli.run("tasm", src);
        assertEquals(0, r.code(), r.toString());
        Path tri = dir.resolve("prog.tri");
        assertTrue(Files.exists(tri));
        TriImage image = TriImage.fromBytes(Files.readAllBytes(tri));
        assertEquals(243, image.loadAddress());
        assertEquals(3, image.size());
        assertEquals(5, image.word(1));
        assertTrue(r.out().contains("3 words at 243 -> " + tri), r.toString());
    }

    @Test
    void outputAndListingNamesCanBeChosen() throws Exception {
        String src = Cli.write(dir, "prog.tasm", "start: NOP\n");
        String tri = dir.resolve("out/x.tri").toString();
        Files.createDirectories(dir.resolve("out"));
        String lst = dir.resolve("out/x.lst").toString();
        Cli.Result r = Cli.run("tasm", src, "-o", tri, "--listing", lst);
        assertEquals(0, r.code(), r.toString());
        assertTrue(Files.exists(Path.of(tri)));
        String listing = Files.readString(Path.of(lst));
        assertTrue(listing.startsWith("; TRIADA-85 assembler listing"), listing);
        assertTrue(listing.contains("start: NOP"), listing);
        assertTrue(r.out().contains("(listing: " + lst + ")"), r.toString());
    }

    @Test
    void includesAreFoundNextToTheIncludingFile() throws Exception {
        Files.createDirectories(dir.resolve("lib"));
        Cli.write(dir, "lib/consts.tasm", "ANSWER = 42\n");
        String src = Cli.write(dir, "main.tasm", ".include \"lib/consts.tasm\"\n.word ANSWER\n");
        assertEquals(0, Cli.run("tasm", src).code());
        assertEquals(42, TriImage.fromBytes(Files.readAllBytes(dir.resolve("main.tri"))).word(0));
    }

    @Test
    void assemblyErrorsGoToStderrWithExitCode1() throws Exception {
        String src = Cli.write(dir, "bad.tasm", "NOP\n  FOO\nLDI R0, 999999\n");
        Cli.Result r = Cli.run("tasm", src);
        assertEquals(Main.EXIT_ASSEMBLY_ERRORS, r.code(), r.toString());
        assertTrue(r.err().contains(src + ":2:3: unknown instruction 'FOO'"), r.err());
        assertTrue(r.err().endsWith("1 error\n"), r.err());
        assertFalse(Files.exists(dir.resolve("bad.tri")), "no output file on errors");
        assertEquals("", r.out());
    }

    @Test
    void usageAndFileProblemsGiveExitCode2() throws Exception {
        assertEquals(2, Cli.run("tasm").code());
        assertEquals(2, Cli.run("tasm", "a.tasm", "-o").code());
        Cli.Result unknown = Cli.run("tasm", "a.tasm", "--fast");
        assertEquals(2, unknown.code());
        assertTrue(unknown.err().contains("unknown option '--fast'"), unknown.err());
        Cli.Result missing = Cli.run("tasm", dir.resolve("nope.tasm").toString());
        assertEquals(2, missing.code());
        assertTrue(missing.err().contains("can't read"), missing.err());
        assertEquals(2, Cli.run("tasm", "a.tasm", "b.tasm").code());
    }

    @Test
    void extensionHelper() {
        assertEquals("games/snake.tri", TasmCommand.withExtension("games/snake.tasm", ".tri"));
        assertEquals("a.b/prog.tri", TasmCommand.withExtension("a.b/prog", ".tri"));
        assertEquals("prog.tri", TasmCommand.withExtension("prog", ".tri"));
    }
}
