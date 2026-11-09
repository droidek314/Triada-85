package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.triada.core.modem.WireModemPort;
import org.triada.net.EchoExchange;
import org.triada.net.LoopbackNetwork;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * The TRIBASIC test suite (M14): every {@code tests/basic/NAME.bas} is typed
 * into a freshly booted machine, then run, and the screen must show exactly
 * {@code NAME.expected}.
 *
 * <p>A {@code .bas} file has up to two parts, split by a line {@code ---}:
 * <ol>
 * <li>the <b>setup</b>, typed first (usually the program's lines);</li>
 * <li>the <b>session</b>, typed after the screen has been cleared; without a
 *     {@code ---} it is just {@code RUN}. It may answer INPUT and ASK;
 *     {@code {ESC}} types the Esc key, {@code {BS}} Backspace, and {@code {WAIT}} lets 10 frames pass
 *     before typing on (so Esc arrives while the program runs).</li>
 * </ol>
 * The runner waits until the machine is back at READY. with every key taken
 * (at most 5,000 frames), and compares the screen's rows, trailing empty rows
 * left out. On a mismatch it writes what it saw to
 * {@code triada-rom/build/basic-actual/NAME.actual}.
 */
class BasicSuiteTest {

    static final int MIN_PROGRAMS = 40;
    private static final int MAX_FRAMES = 5_000;
    private static final int WAIT_FRAMES = 10;

    static Path suite() {
        return Path.of(System.getProperty("triada.root", "..")).resolve("tests/basic");
    }

    @TestFactory
    Stream<DynamicTest> everyProgramShowsWhatItsExpectedFileSays() throws IOException {
        List<Path> programs = new ArrayList<>();
        try (Stream<Path> files = Files.list(suite())) {
            files.filter(p -> p.getFileName().toString().endsWith(".bas")).sorted().forEach(programs::add);
        }
        assertTrue(programs.size() >= MIN_PROGRAMS, "at least " + MIN_PROGRAMS + " programs, found " + programs.size());
        return programs.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> check(p)));
    }

    private static void check(Path bas) throws IOException {
        String name = bas.getFileName().toString().replace(".bas", "");
        Path expectedFile = bas.resolveSibling(name + ".expected");
        assertTrue(Files.exists(expectedFile), "missing " + expectedFile);
        List<String> expected = trim(Files.readAllLines(expectedFile, StandardCharsets.UTF_8));
        List<String> actual = run(Files.readString(bas, StandardCharsets.UTF_8));
        if (!expected.equals(actual)) {
            Path out = Path.of("build/basic-actual").resolve(name + ".actual");
            Files.createDirectories(out.getParent());
            Files.writeString(out, String.join("\n", actual) + "\n", StandardCharsets.UTF_8);
        }
        assertEquals(String.join("\n", expected), String.join("\n", actual), name);
    }

    /** Types the setup, clears the screen, types the session, and returns the screen's rows. */
    static List<String> run(String bas) {
        String text = bas.replace("\r\n", "\n");
        String setup = text;
        String session = "RUN\n";
        int split = text.indexOf("\n---\n");
        if (split >= 0) {
            setup = text.substring(0, split + 1);
            session = text.substring(split + 5);
        } else if (text.startsWith("---\n")) {
            setup = "";
            session = text.substring(4);
        }
        Triada t = Triada.basic();
        // The modem's line goes to the echo exchange (M17): 1-11-11 echoes, 2-22-22 is busy,
        // 3-33-33 never connects, other numbers don't exist.
        t.m.modem().setPort(new WireModemPort(new LoopbackNetwork(new EchoExchange()), t.m.profile()));
        t.typeOnly(setup);
        t.frames(2);
        t.clearScreen();
        for (String part : session.replace("{ESC}", "\u001b").replace("{BS}", "\b").split("\\{WAIT}", -1)) {
            t.typeOnly(part);
            t.frames(WAIT_FRAMES);              // {WAIT}: let the program run a little first
        }
        t.waitForPrompt(MAX_FRAMES);
        return trim(t.screen());
    }

    private static List<String> trim(List<String> rows) {
        List<String> r = new ArrayList<>();
        for (String s : rows) {
            r.add(s.stripTrailing());
        }
        while (!r.isEmpty() && r.get(r.size() - 1).isEmpty()) {
            r.remove(r.size() - 1);
        }
        return r;
    }
}
