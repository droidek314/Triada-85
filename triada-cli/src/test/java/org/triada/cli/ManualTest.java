package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.triada.assembler.Assembler;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.SourceLoader;
import org.triada.core.Machine;
import org.triada.core.memory.MemoryMap;
import org.triada.core.ternary.Word;
import org.triada.rom.Rom;

/**
 * The owner's manual (docs/manual, M11) must tell the truth:
 * <ul>
 *   <li>every {@code ```monitor} block is a screen the real monitor produces
 *       when the "> " lines are typed into it after CLS;</li>
 *   <li>the type-in listing of chapter 7 (143 POKE lines and the source) is
 *       exactly {@code programs/triangle.tasm}, and typing it in through the
 *       monitor and RUNning it draws the triangle of threes.</li>
 * </ul>
 */
class ManualTest {

    private static final Path MANUAL = Cli.root().resolve("docs/manual");
    private static final Pattern MONITOR_BLOCK = Pattern.compile("```monitor\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern POKE = Pattern.compile("POKE (\\d+) (0t[-+0]+)");

    // --- a TRIADA driven through its keyboard ---------------------------------------

    private final Machine m = Rom.newMachine();

    private void frames(int n) {
        for (int i = 0; i < n; i++) {
            m.runFrame();
            assertFalse(m.cpu().isHalted(), "the CPU halted, fault " + m.cpu().fault());
        }
    }

    /** Types text ('\n' = Enter), 27 keys per frame (the queue's size), then waits for a prompt. */
    private void type(String text) {
        int i = 0;
        while (i < text.length()) {
            for (int k = 0; k < 27 && i < text.length(); k++, i++) {
                char c = text.charAt(i);
                m.input().pushKey(c == '\n' ? 10 : c);
            }
            frames(1);
        }
        waitForPrompt();
    }

    private void waitForPrompt() {
        int cx = Rom.address("CURSOR_X");
        int cy = Rom.address("CURSOR_Y");
        for (int f = 0; f < 600; f++) {
            frames(1);
            if (m.input().queuedKeys() == 0 && m.bus().peek(cx) == 2 && row(m.bus().peek(cy)).startsWith(">")) {
                return;
            }
        }
        fail("no prompt; screen:\n" + String.join("\n", screen()));
    }

    /**
     * Switches on and goes to the monitor, as the manual says: the machine
     * boots into TRIBASIC (M14), and MON reaches the monitor.
     */
    private void bootToMonitor() {
        int cy = Rom.address("CURSOR_Y");
        for (int f = 0; f < 600 && !(m.bus().peek(cy) > 0 && row(m.bus().peek(cy) - 1).equals("READY.")); f++) {
            frames(1);
        }
        type("MON\n");
    }

    /** Row r as text, trailing spaces and the blinking cursor removed. */
    private String row(int r) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < 27; c++) {
            int v = m.bus().peek(MemoryMap.MAP + r * 27 + c);
            sb.append(v >= 32 && v <= 126 ? (char) v : '?');
        }
        String s = sb.toString().stripTrailing();
        return s.endsWith("_") && r == m.bus().peek(Rom.address("CURSOR_Y"))
                ? s.substring(0, s.length() - 1).stripTrailing() : s;
    }

    private List<String> screen() {
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < 18; r++) {
            rows.add(row(r));
        }
        return rows;
    }

    private static String read(String file) throws IOException {
        return Files.readString(MANUAL.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    // --- the monitor examples --------------------------------------------------------

    /** One dynamic test per ```monitor block in the manual. */
    @TestFactory
    Stream<DynamicTest> everyMonitorExampleIsTrue() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        try (Stream<Path> files = Files.list(MANUAL)) {
            for (Path p : files.sorted().toList()) {
                Matcher mb = MONITOR_BLOCK.matcher(read(p.getFileName().toString()));
                int n = 0;
                while (mb.find()) {
                    String block = mb.group(1);
                    n++;
                    tests.add(DynamicTest.dynamicTest(p.getFileName() + " example " + n, () -> replay(block)));
                }
            }
        }
        assertTrue(tests.size() >= 7, "the manual has its examples: " + tests.size());
        return tests.stream();
    }

    private static void replay(String block) {
        ManualTest t = new ManualTest();
        t.bootToMonitor();
        t.type("CLS\n");
        List<String> expected = block.lines().map(String::stripTrailing).toList();
        for (String line : expected) {
            if (line.startsWith("> ")) {
                t.type(line.substring(2) + "\n");
            }
        }
        List<String> screen = t.screen();
        assertEquals(String.join("\n", expected), String.join("\n", screen.subList(0, expected.size())),
                "the screen after typing the example");
        assertEquals(">", screen.get(expected.size()), "then the prompt");
    }

    /** Chapter 5's "try it" lines do what the text promises. */
    @Test
    void theTryItPokesOfChapter5Work() throws Exception {
        String page = read("05-pictures-and-sound.md");
        for (String cmd : List.of("POKE 44000 65", "POKE -20403 5", "POKE -20402 440", "POKE -20401 10")) {
            assertTrue(page.contains("`" + cmd + "`"), cmd + " is in chapter 5");
        }
        bootToMonitor();
        type("CLS\n");
        type("POKE 44000 65\n");
        assertTrue(row(0).startsWith("A"), "an A in the top left corner: " + row(0));
        type("POKE -20403 5\n");
        assertEquals(0xFF0000, m.video().frame()[0], "a red border");
        type("POKE -20402 440\n");
        type("POKE -20401 10\n");
        int io = MemoryMap.IO_BASE;
        assertEquals(440, m.bus().peek(io + 10));
        assertEquals(10, m.bus().peek(io + 11));
        int peak = 0;
        for (short s : m.sound().frameSamples()) {
            peak = Math.max(peak, Math.abs(s));
        }
        assertTrue(peak > 5000, "you hear an A: peak " + peak);
    }

    // --- the type-in listing ---------------------------------------------------------

    @Test
    void theListingIsTheProgramInBothForms() throws Exception {
        String page = read("07-type-in-listing.md");
        int[] program = assemble(Cli.root().resolve("programs/triangle.tasm"));
        TreeMap<Integer, Integer> pokes = pokes(page);
        assertEquals(program.length, pokes.size(), "one POKE per word");
        assertEquals(243, pokes.firstKey());
        assertEquals(243 + program.length - 1, pokes.lastKey());
        for (int i = 0; i < program.length; i++) {
            assertEquals(program[i], pokes.get(243 + i), "POKE " + (243 + i));
        }
        Matcher src = Pattern.compile("```tasm\\n(.*?)```", Pattern.DOTALL).matcher(page);
        assertTrue(src.find(), "the page prints the source");
        int[] printed = new Assembler(SourceLoader.NONE).assembleSource("page.tasm", src.group(1)).image().words();
        assertArrayEquals(program, printed, "the printed source assembles to the same words");
        assertTrue(page.contains(program.length + " words"), "the page says how long it is");
    }

    @Test
    void typingTheListingInAndRunningItDrawsTheTriangle() throws Exception {
        bootToMonitor();
        TreeMap<Integer, Integer> pokes = pokes(read("07-type-in-listing.md"));
        StringBuilder all = new StringBuilder();
        Matcher mp = POKE.matcher(read("07-type-in-listing.md"));
        while (mp.find()) {
            all.append(mp.group()).append('\n');            // exactly as printed
        }
        type(all.toString());
        for (var e : pokes.entrySet()) {
            assertEquals(e.getValue(), m.bus().peek(e.getKey()), "house " + e.getKey());
        }
        for (char c : "RUN\n".toCharArray()) {
            m.input().pushKey(c == '\n' ? 10 : c);
        }
        frames(80);                                          // 16 rows, 4 frames each
        int[][] want = pascalTrits();
        assertTriangle(want);
        assertEquals("    TRIANGLE OF THREES", row(17));
        assertEquals(0, m.bus().peek(MemoryMap.IO_BASE + 11), "the rising tune has ended (V1_VOL)");

        // Half a second later every trit has turned up once.
        int[][] turned = new int[17][27];
        for (int r = 0; r < 17; r++) {
            for (int c = 0; c < 27; c++) {
                turned[r][c] = want[r][c] == 1 ? -1 : want[r][c] + 1;
            }
        }
        int f = 0;
        while (f < 40 && m.bus().peek(MemoryMap.MAP + 13) == 3) {
            frames(1);
            f++;
        }
        assertTriangle(turned);

        // Any key: back to the monitor, with a clean screen.
        int done = m.bus().peek(Rom.address("RUN_DONE"));
        m.input().pushKey('Q');
        waitForPrompt();
        assertEquals("READY.", row(0));
        assertEquals(done + 1, m.bus().peek(Rom.address("RUN_DONE")));
    }

    /** Pascal's triangle with every number reduced to a balanced trit, rows 0 ... 16, columns 0 ... 26. */
    private static int[][] pascalTrits() {
        int[][] t = new int[17][27];
        t[0][13] = 1;
        for (int r = 1; r < 17; r++) {
            for (int c = 1; c < 26; c++) {
                int s = Math.floorMod(t[r - 1][c - 1] + t[r - 1][c + 1], 3);   // 0, 1, 2
                t[r][c] = s == 2 ? -1 : s;
            }
        }
        return t;
    }

    private void assertTriangle(int[][] trits) {
        for (int r = 0; r < 17; r++) {
            StringBuilder want = new StringBuilder();
            StringBuilder got = new StringBuilder();
            for (int c = 0; c < 27; c++) {
                want.append("-0+".charAt(trits[r][c] + 1));
                int tile = m.bus().peek(MemoryMap.MAP + r * 27 + c);
                got.append(tile >= 1 && tile <= 3 ? "-0+".charAt(tile - 1) : '?');
            }
            assertEquals(want.toString(), got.toString(), "row " + r);
        }
    }

    private static TreeMap<Integer, Integer> pokes(String page) {
        TreeMap<Integer, Integer> pokes = new TreeMap<>();
        Matcher mp = POKE.matcher(page);
        while (mp.find()) {
            int address = Integer.parseInt(mp.group(1));
            assertTrue(pokes.put(address, Word.parse(mp.group(2).substring(2))) == null, "POKE " + address + " twice");
        }
        return pokes;
    }

    private static int[] assemble(Path file) throws IOException, AssemblyException {
        return new Assembler(new FileSourceLoader()).assemble(file.toString()).image().words();
    }
}
