package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.core.ternary.Word;

/**
 * End-to-end tests of the example programs in {@code programs/}: the real
 * files from the repository, assembled and run through the real command line.
 */
class ProgramsTest {

    @TempDir
    Path dir;

    private static String program(String name) {
        return Cli.root().resolve("programs").resolve(name).toString();
    }

    @Test
    void helloPrintsHelloWorldAndExitsWithZero() {
        Cli.Result r = Cli.run("run", program("hello.tasm"));
        assertEquals(0, r.code(), r.toString());
        assertEquals("HELLO, WORLD\n", r.out());
        assertEquals("", r.err());
    }

    @Test
    void helloAlsoWorksThroughATriFile() throws Exception {
        String tri = dir.resolve("hello.tri").toString();
        String lst = dir.resolve("hello.lst").toString();
        Cli.Result asm = Cli.run("tasm", program("hello.tasm"), "-o", tri, "--listing", lst);
        assertEquals(0, asm.code(), asm.toString());
        assertTrue(Files.readString(Path.of(lst)).contains("msg:    .str \"HELLO, WORLD\""));
        Cli.Result r = Cli.run("run", tri);
        assertEquals(0, r.code());
        assertEquals("HELLO, WORLD\n", r.out());
    }

    @Test
    void countdownCountsOneNumberPerFrame() {
        Cli.Result r = Cli.run("run", program("countdown.tasm"));
        assertEquals(0, r.code(), r.toString());
        assertEquals("10\n9\n8\n7\n6\n5\n4\n3\n2\n1\n0\nLIFT-OFF!\n", r.out());
        assertEquals("", r.err());

        // One WAIT per number: in exactly 10 frames, exactly 10 numbers appear.
        Cli.Result tenFrames = Cli.run("run", program("countdown.tasm"), "--frames", "10");
        assertEquals(0, tenFrames.code());
        assertEquals("10\n9\n8\n7\n6\n5\n4\n3\n2\n1\n", tenFrames.out());
    }

    @Test
    void ternaryPrintsEveryNumberFromMinus13To13() {
        Cli.Result r = Cli.run("run", program("ternary.tasm"));
        assertEquals(0, r.code(), r.toString());
        String[] lines = r.out().split("\n");
        assertEquals(27, lines.length, r.out());
        for (int n = -13; n <= 13; n++) {
            String trits = Word.toString(n).replaceFirst("^0+(?=.)", "");   // drop leading zeros
            assertEquals(String.format("%3d = %s", n, trits), lines[n + 13]);
        }
    }

    @Test
    void everyExampleProgramAssembles() throws Exception {
        try (var files = Files.list(Cli.root().resolve("programs"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".tasm")).toList()) {
                Cli.Result r = Cli.run("tasm", p.toString(), "-o", dir.resolve("x.tri").toString());
                assertEquals(0, r.code(), p + ": " + r);
            }
        }
    }
}
