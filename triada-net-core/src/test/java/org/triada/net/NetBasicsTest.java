package org.triada.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The small parts of the server (M18): text, the pretend terminal, board files, passwords, rate limits. */
class NetBasicsTest {

    @Test
    void wordWrapFor27Columns() {
        assertEquals(List.of("THE QUICK BROWN FOX JUMPS", "OVER THE LAZY DOG."),
                Text.wrap("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG.", 27));
        assertEquals(List.of("ONE", "", "TWO"), Text.wrap("ONE\n\nTWO", 27), "line breaks are kept");
        assertEquals(List.of("ABCDE", "FGHIJ", "K"), Text.wrap("ABCDEFGHIJK", 5), "a long word is cut");
        assertEquals("ABC", Text.fit("ABCDEF", 3));
        assertEquals("A?B", Text.clean("A?B\u0007") + "", "control characters go");
    }

    @Test
    void theTerminalFollowsTheControlCodes() {
        TerminalScreen t = new TerminalScreen();
        t.receive(new TermOut().text("JUNK").clear().line("HELLO").text("WORLD").backspace().bell()
                .color(-3, 5).at(5, 3).text("X").take());
        assertEquals(List.of("HELLO", "WORL", "", "     X"), t.lines());
        assertEquals(1, t.bells());
        assertEquals(-3, t.foreground());
        assertEquals(5, t.background());
        TerminalScreen wrap = new TerminalScreen();
        wrap.receive(new TermOut().text("A".repeat(27)).newline().text("B".repeat(30)).take());
        assertEquals(List.of("A".repeat(27), "B".repeat(27), "BBB"), wrap.lines(),
                "exactly 27 then a new line is one line; 30 wraps");
        TerminalScreen scroll = new TerminalScreen();
        for (int i = 0; i < 20; i++) {
            scroll.receive(new TermOut().line("LINE " + i).take());
        }
        assertEquals("LINE 3", scroll.row(0), "the screen scrolled up");
        assertEquals("LINE 19", scroll.row(16));
    }

    @Test
    void boardFiles() {
        BoardDef b = BoardDef.parse("""
                # a test board
                NUMBER 1-23-45
                NAME NPO "TRIADA" SUPPORT
                SYSOP K. VOLKOV
                COLORS 13 -13
                LINES 2
                UNLISTED
                MESSAGES GENERAL GENERAL TALK
                FILES PROGRAMS PROGRAMS AND PATCHES
                WELCOME
                  /\\
                 /  \\
                END
                """);
        assertEquals("1-23-45", b.number());
        assertEquals("NPO \"TRIADA\" SUPPORT", b.name());
        assertEquals(13, b.foreground());
        assertEquals(-13, b.background());
        assertEquals(2, b.lines());
        assertTrue(b.unlisted());
        assertEquals(new BoardDef.Area("GENERAL", "GENERAL TALK"), b.messageAreas().get(0));
        assertEquals("PROGRAMS AND PATCHES", b.fileAreas().get(0).title());
        assertEquals(List.of("  /\\", " /  \\"), b.welcome());
        String[] bad = {
            "NAME X\nMESSAGES A A",                        // no number
            "NUMBER 12-34-5\nNAME X\nMESSAGES A A",        // a badly written number
            "NUMBER 1-23-45\nNAME X",                      // no message area
            "NUMBER 1-23-45\nNAME X\nMESSAGES a/b A",      // a bad area id
            "NUMBER 1-23-45\nNAME X\nMESSAGES A A\nCOLORS 14 0",
            "NUMBER 1-23-45\nNAME X\nMESSAGES A A\nWELCOME\nno end",
            "NUMBER 1-23-45\nNAME X\nMESSAGES A A\nWHAT",
            "NUMBER 1-23-45\nNAME X\nMESSAGES A A\nWELCOME\n" + "X".repeat(28) + "\nEND",
        };
        for (String text : bad) {
            assertThrows(IllegalArgumentException.class, () -> BoardDef.parse(text), text);
        }
    }

    @Test
    void passwordsAreSaltedHashes() {
        Passwords p = new Passwords(1_000);
        String h = p.hash("SECRET1");
        assertTrue(h.startsWith("pbkdf2-sha256$1000$"));
        assertFalse(h.contains("SECRET1"));
        assertTrue(p.verify("SECRET1", h));
        assertFalse(p.verify("SECRET2", h));
        assertFalse(h.equals(p.hash("SECRET1")), "a new salt every time");
        assertFalse(p.verify("SECRET1", "garbage"));
        assertFalse(p.verify("SECRET1", "pbkdf2-sha256$x$y$z"));
        assertFalse(p.verify("SECRET1", null));
        assertFalse(p.verifyNobody("SECRET1"));
        assertTrue(new Passwords(2_000).verify("SECRET1", h), "the rounds are read from the stored hash");
        assertThrows(IllegalArgumentException.class, () -> new Passwords(0));
    }

    @Test
    void rateLimitsSlide() {
        RateLimiter r = new RateLimiter();
        Instant t = Instant.parse("2026-11-09T12:00:00Z");
        Duration ten = Duration.ofMinutes(10);
        assertTrue(r.allow("post:A", 2, ten, t));
        assertTrue(r.allow("post:A", 2, ten, t.plusSeconds(60)));
        assertFalse(r.allow("post:A", 2, ten, t.plusSeconds(120)));
        assertTrue(r.allow("post:B", 2, ten, t.plusSeconds(120)), "another key");
        assertTrue(r.allow("post:A", 2, ten, t.plusSeconds(601)), "the first one left the window");
        r.record("fail:X", t);
        r.record("fail:X", t);
        assertEquals(2, r.count("fail:X", ten, t));
        assertEquals(0, r.count("fail:X", ten, t.plusSeconds(601)));
    }
}
