package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;
import org.triada.core.modem.ModemDevice;
import org.triada.core.modem.WireModemPort;
import org.triada.net.EchoExchange;
import org.triada.net.LoopbackNetwork;

/**
 * Smoke tests for the TRIBASIC example programs in {@code programs/basic/}
 * (M15): each is typed in, run, played a little, and must not stop with an
 * error.
 */
class BasicExamplesTest {

    private static String source(String name) throws IOException {
        return Files.readString(Path.of(System.getProperty("triada.root", "..")).resolve("programs/basic/" + name),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Boots TRIBASIC and types the program in. */
    private static Triada typedIn(String name) throws IOException {
        String text = source(name);
        for (String line : text.split("\n")) {
            assertTrue(line.length() <= 80, name + ": a line longer than 80 characters: " + line);
        }
        Triada t = Triada.basic();
        t.typeOnly(text);
        t.frames(2);
        return t;
    }

    private static String screen(Triada t) {
        return String.join("\n", t.screen());
    }

    private static void noError(Triada t) {
        assertFalse(screen(t).contains("ERROR"), screen(t));
    }

    @Test
    void oracleAnswersAndSleeps() throws IOException {
        Triada t = typedIn("ORACLE.bas");
        t.typeOnly("RUN\nYNN");
        t.waitForPrompt(600);
        noError(t);
        String s = screen(t);
        assertTrue(s.contains("BELIEVE IN TRITS (Y/N/?) Y"), s);
        assertTrue(s.contains("WILL YOU WORK (Y/N/?) N"), s);
        assertTrue(s.contains("YES. THE TRITS SMILE.") || s.contains("THE TRITS DO NOT KNOW.")
                || s.contains("NOT THIS TIME."), s);
        assertTrue(s.contains("THE ORACLE SLEEPS."), s);
    }

    /** The test plays GUESS by halving the range after every hint. */
    @Test
    void guessCanBeWonByHalving() throws IOException {
        Triada t = typedIn("GUESS.bas");
        t.typeOnly("RUN\n");
        int low = 1;
        int high = 100;
        for (int tries = 1; tries <= 7; tries++) {
            int guess = (low + high) / 2;
            t.typeOnly(guess + "\n");
            t.frames(5);
            String last = lastLine(t.screen());
            if (last.startsWith("RIGHT IN ")) {
                assertEquals("RIGHT IN " + tries + " TRIES!", last);
                t.waitForPrompt(60);
                noError(t);
                return;
            } else if (last.equals("TOO HIGH")) {
                high = guess - 1;
            } else if (last.equals("TOO LOW")) {
                low = guess + 1;
            } else {
                fail("unexpected answer '" + last + "':\n" + screen(t));
            }
        }
        fail("not found in 7 tries:\n" + screen(t));
    }

    /** The last row with text that isn't the INPUT prompt. */
    private static String lastLine(List<String> rows) {
        for (int r = rows.size() - 1; r >= 0; r--) {
            String row = rows.get(r);
            if (!row.isEmpty() && !row.startsWith("YOUR GUESS?") && !row.equals("READY.")) {
                return row;
            }
        }
        return "";
    }

    @Test
    void ternclockTicksInTrits() throws IOException {
        Triada t = typedIn("TERNCLOCK.bas");
        t.typeOnly("RUN\n");
        t.frames(60 * 3 + 30);                     // three seconds and a bit
        noError(t);
        assertEquals("HOURS", t.row(4).substring(0, 5));
        // 12 in trits is ++0 (9 + 3 + 0): columns 12, 13, 14 show red, red, grey (tiles 3, 3, 2)
        assertEquals(3, cell(t, 12, 4));
        assertEquals(3, cell(t, 13, 4));
        assertEquals(2, cell(t, 14, 4));
        assertTrue(t.row(4).endsWith("++0"), t.row(4));
        t.m.input().pushKey('Q');
        t.waitForPrompt(200);
        assertTrue(screen(t).contains("STOPPED AT 12:0:"), screen(t));
    }

    @Test
    void zmeykaRunsIntoTheWallAndShowsTheScore() throws IOException {
        Triada t = typedIn("ZMEYKA.bas");
        t.typeOnly("RUN\n");
        t.waitForPrompt(3_000);                    // nobody steers: it reaches the right wall
        noError(t);
        assertTrue(t.row(0).startsWith("ZMEYKA SCORE: "), screen(t));
    }

    @Test
    void zmeykaSteersWithTheJoystick() throws IOException {
        Triada t = typedIn("ZMEYKA.bas");
        t.typeOnly("RUN\n");
        t.frames(30);
        t.m.input().setJoyY(-1);                   // up
        t.frames(20);
        t.m.input().setJoyY(0);
        t.frames(10);
        noError(t);
        int bodyAbove = 0;
        for (int row = 2; row < 9; row++) {
            for (int col = 1; col < 26; col++) {
                bodyAbove += cell(t, col, row) == 1 ? 1 : 0;
            }
        }
        assertTrue(bodyAbove > 0, "the snake went up:\n" + screen(t));
    }

    @Test
    void typeinWeavesItsTapestry() throws IOException {
        Triada t = typedIn("TYPEIN.bas");
        t.typeOnly("RUN\n");
        t.frames(120);
        noError(t);
        for (int x = 0; x < 27; x++) {
            int tile = cell(t, x, 5);
            assertTrue(tile >= 1 && tile <= 3, "column " + x + " has tile " + tile);
        }
        t.m.input().pushKey('Q');
        t.waitForPrompt(600);
        assertEquals("THANK YOU FOR TYPING!", t.row(0));
    }

    /** AUTOCALL (M17) calls the echo board, prints its answer and hangs up. */
    @Test
    void autocallTalksToTheEchoBoard() throws IOException {
        Triada t = new Triada();
        t.m.modem().setPort(new WireModemPort(new LoopbackNetwork(new EchoExchange()), t.m.profile()));
        t.waitForPrompt();
        t.typeOnly(source("AUTOCALL.bas"));
        t.frames(2);
        t.clearScreen();
        t.typeOnly("RUN\n");
        t.waitForPrompt(600);
        noError(t);
        assertEquals(List.of("RUN", "DIALING 1-11-11", "CONNECT 1200", "ECHO READY", "HELLO FROM TRIADA",
                "HUNG UP.", "READY."), t.screen().subList(0, 7));
        assertEquals(ModemDevice.NO_CARRIER, t.m.bus().read(MemoryMap.IO_BASE + IoRegisters.MDM_STATUS));
    }

    /** Without a network (the machine's default), AUTOCALL says so. */
    @Test
    void autocallWithoutANetwork() throws IOException {
        Triada t = typedIn("AUTOCALL.bas");
        t.clearScreen();
        t.typeOnly("RUN\n");
        t.waitForPrompt(600);
        noError(t);
        assertEquals(List.of("RUN", "DIALING 1-11-11", "TRIADA-NET IS OFFLINE", "READY."),
                t.screen().subList(0, 4));
    }

    private static int cell(Triada t, int col, int row) {
        return t.m.bus().read(MemoryMap.MAP + row * 27 + col);
    }
}
