package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.triada.core.ternary.Word;

/** The monitor (SPEC 8.3), driven through the keyboard queue and read back from the screen. */
class MonitorTest {

    private final Triada t = Triada.booted();

    private List<String> run(String command) {
        t.type(command + "\n");
        return t.outputOf(command);
    }

    /** "0t" + trits without leading zeros, as the monitor prints them. */
    private static String tern(int v) {
        return "0t" + Word.toString(v).replaceFirst("^0+(?=.)", "");
    }

    // ================================================================ boot

    /** Since M14 the machine boots into TRIBASIC; MON brings the monitor. */
    @Test
    void monFromTribasicShowsTheMonitorPrompt() {
        assertEquals("TRIADA-85 NPO \"TRIADA\" 1985", t.row(0));
        assertEquals("TRIBASIC V1.0", t.row(1));
        assertEquals("39757 WORDS FREE", t.row(2));
        assertEquals("READY.", t.row(3));
        assertEquals("MON", t.row(4));
        assertEquals("READY.", t.row(5));
        assertEquals(">", t.row(6));
        assertEquals(2, t.cursorX());
        assertEquals(6, t.cursorY());
        assertEquals("", t.row(7));
    }

    @Test
    void basicGoesBackToTribasicWithTheProgramKept() {
        Triada b = Triada.basic();
        b.type("10 PRINT \"KEPT\"\nMON\n");
        b.type("BASIC\n");
        b.type("RUN\n");
        assertEquals("KEPT", b.row(b.cursorY() - 2));
        assertEquals("READY.", b.row(b.cursorY() - 1));
    }

    @Test
    void basicForgetsAProgramThatTheMonitorDestroyed() {
        Triada b = Triada.basic();
        b.type("10 PRINT \"KEPT\"\nMON\n");
        b.type("POKE 243 -5\n");                  // a record length can't be negative
        b.type("BASIC\n");
        b.type("LIST\n");
        assertEquals("LIST", b.row(b.cursorY() - 2), "nothing listed: NEW");
    }

    // ================================================================ commands

    @Test
    void help() {
        assertEquals(List.of("COMMANDS:", " PEEK ADDR [COUNT]", " POKE ADDR VALUE", " RUN [ADDR]", " CLS",
                " TERN N", " BASIC", " HELP", "NUMBERS: 123  -5  0T+-0"), run("HELP"));
    }

    @Test
    void pokeThenPeek() {
        assertEquals(List.of(), run("POKE 1000 42"));
        assertEquals(List.of("1000: 42 " + tern(42)), run("PEEK 1000"));
        assertEquals(42, t.m.bus().read(1000));
    }

    @Test
    void peekSeveralWordsAndNegativeValues() {
        run("POKE 2000 -5");
        run("POKE 2001 0T+-0");
        assertEquals(List.of("1999: 0 0t0", "2000: -5 " + tern(-5), "2001: 6 0t+-0"), run("PEEK 1999 3"));
    }

    @Test
    void peekReadsTheRom() {
        // The jump table's first word, JMP (-157,464). "-19683: -157464 0t-0+000000000" is
        // 30 characters, so on the 27-column screen it wraps onto a second line.
        int first = Rom.image().word(0);
        String line = "-19683: " + first + " " + tern(first);
        assertEquals(30, line.length());
        assertEquals(List.of(line.substring(0, 27), line.substring(27)), run("PEEK -19683"));
    }

    @Test
    void pokeIntoRomIsIgnored() {
        int before = t.m.bus().read(-19_683);
        run("POKE -19683 7");
        assertEquals(before, t.m.bus().read(-19_683));
    }

    @Test
    void ternShowsBalancedTernary() {
        assertEquals(List.of("6 = 0t+-0"), run("TERN 6"));
        assertEquals(List.of("-2 = 0t-+"), run("TERN -2"));
        assertEquals(List.of("0 = 0t0"), run("TERN 0"));
        assertEquals(List.of("6 = 0t+-0"), run("TERN 0T+-0"));
        assertEquals(List.of("42 = 0t+---0"), run("TERN 42"));
    }

    @Test
    void ternOfTheExtremes() {
        // 7 + 3 + 2 + 12 = 24 characters: fits on one line.
        assertEquals(List.of("265720 = 0t++++++++++++"), run("TERN 265720"));
        // "-265720 = 0t------------" is 25 characters, still one line.
        assertEquals(List.of("-265720 = 0t------------"), run("TERN 0T------------"));
    }

    @Test
    void lowerCaseWorks() {
        assertEquals(List.of("6 = 0t+-0"), run("tern 0t+-0"));
    }

    @Test
    void clsClearsTheScreen() {
        t.type("CLS\n");
        assertEquals(">", t.row(0));
        assertEquals("", t.row(1));
        assertEquals(0, t.cursorY());
    }

    @Test
    void anEmptyLineJustPromptsAgain() {
        t.type("\n");
        assertEquals(">", t.row(6));
        assertEquals(">", t.row(7));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
        "FOO", "HELPX", "HELP ME", "PEEK", "PEEK X", "PEEK 12X", "PEEK 5 0", "PEEK 5 -1", "PEEK 1 2 3",
        "POKE 5", "POKE 5 6 7", "RUN X", "CLS 1", "TERN", "TERN 265721", "TERN -265721", "TERN 999999",
        "TERN 0T", "TERN 0T+++++++++++++", "TERN 0T+-2", "TERN --5", "TERN 1-",
    })
    void nonsenseGetsSyntaxError(String command) {
        assertEquals(List.of("?SYNTAX ERROR"), run(command));
    }

    // ================================================================ RUN

    @Test
    void runCallsAProgramAndComesBack() {
        // A program at 300 that prints "HI!" with the kernel's PRINT and returns.
        var program = Triada.assemble("""
                        .org 300
                        LDI R0, msg
                        JSR PRINT
                        RET
                msg:    .str "HI!\\n"
                """);
        t.m.bus().loadRam(program.loadAddress(), program.words());
        int doneBefore = t.m.bus().read(Rom.address("RUN_DONE"));
        assertEquals(List.of("HI!", "READY."), run("RUN 300"));
        assertEquals(doneBefore + 1, t.m.bus().read(Rom.address("RUN_DONE")), "RUN_DONE counts finished programs");
    }

    @Test
    void runWithoutAnAddressStartsAt243() {
        var program = Triada.assemble("""
                        LDI R0, 7
                        JSR PRINTNUM
                        RET
                """);
        t.m.bus().loadRam(program.loadAddress(), program.words());
        assertEquals(List.of("7READY."), run("RUN"));
    }

    // ================================================================ line editing

    @Test
    void backspaceEditsTheLine() {
        t.type("TERM\b\bRN 5\n");
        assertEquals(List.of("5 = 0t+--"), t.outputOf("TERN 5"));
    }

    @Test
    void backspaceAtTheStartOfTheLineDoesNothing() {
        t.type("\b\b\bTERN 1\n");
        assertEquals(List.of("1 = 0t+"), t.outputOf("TERN 1"));
    }

    @Test
    void linesAreLimitedTo24Characters() {
        String tooLong = "TERN 1" + " ".repeat(30) + "XYZ";
        t.type(tooLong + "\n");
        // Only the first 24 characters were taken: "TERN 1" and 18 spaces.
        assertEquals(List.of("1 = 0t+"), t.outputOf("TERN 1"));
    }

    @Test
    void keysOutsidePrintableAsciiAreIgnored() {
        t.m.input().pushKey(200);
        t.m.input().pushKey(9);         // Tab
        t.type("TERN 2\n");
        assertEquals(List.of("2 = 0t+-"), t.outputOf("TERN 2"));
    }

    // ================================================================ scrolling

    @Test
    void theScreenScrollsWhenFull() {
        for (int i = 0; i < 12; i++) {
            t.type("TERN " + i + "\n");
        }
        // 7 lines of boot and MON + 12 x 2 lines > 18 rows: the boot banner has scrolled away.
        assertTrue(!t.row(0).startsWith("TRIADA"), t.row(0));
        assertEquals(17, t.cursorY());
        assertEquals("> TERN 11", t.row(15));
        assertEquals("11 = 0t++-", t.row(16));
        assertEquals(">", t.row(17));
    }

    // ================================================================ autorun

    @Test
    void autorunStartsAProgramAfterBootLikeRun() {
        Triada a = Triada.autorun("""
                        LDI R0, msg
                        JSR PRINT
                        RET
                msg:    .str "AUTORUN!\\n"
                """);
        a.waitForPrompt();
        assertEquals("TRIADA-85 NPO \"TRIADA\" 1985", a.row(0));
        assertEquals("TRIBASIC V1.0", a.row(1));
        assertEquals("39757 WORDS FREE", a.row(2));
        assertEquals("AUTORUN!", a.row(3));
        assertEquals("READY.", a.row(4));
        assertEquals(5, a.cursorY(), "back in TRIBASIC (M14), at the start of the next line");
        assertEquals(0, a.cursorX());
        assertEquals(0, a.m.bus().read(Rom.address("AUTORUN")), "cleared, so it runs only once");
    }

}
