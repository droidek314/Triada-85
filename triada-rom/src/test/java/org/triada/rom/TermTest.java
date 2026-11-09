package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;
import org.triada.core.modem.WireModemPort;
import org.triada.net.Boards;
import org.triada.net.LocalNode;
import org.triada.net.LoopbackNetwork;
import org.triada.net.NetStore;
import org.triada.net.Network;

/**
 * TERM.TRI (SPEC 11.1, M19) and TRIBASIC's DIAL, on a headless TRIADA-85
 * whose modem is connected to TRIADA-NET inside the same program.
 */
class TermTest {

    /** A booted TRIBASIC with TERM.TRI on its triskette and a line to {@code net} (or no line). */
    private static Triada machine(Network net) {
        Triada t = Triada.basic();
        Rom.prepareTriskette(t.m.triskette().port());
        if (net != null) {
            t.m.modem().setPort(new WireModemPort(new LoopbackNetwork(net), t.m.profile()));
        }
        return t;
    }

    /** Runs frames until {@code text} is on the screen. */
    private static void waitFor(Triada t, String text, int maxFrames) {
        for (int f = 0; f < maxFrames; f++) {
            if (String.join("\n", t.screen()).contains(text)) {
                return;
            }
            t.frames(1);
        }
        fail("no '" + text + "' after " + maxFrames + " frames; screen:\n" + String.join("\n", t.screen()));
    }

    private static long messageId(Network net, String board, String area, String subject) {
        for (NetStore.Message m : net.store().messages(board, area, 0, 100)) {
            if (m.subject().equals(subject)) {
                return m.id();
            }
        }
        throw new AssertionError("no message " + subject);
    }

    /**
     * The end-to-end test of M19: boot, DIAL a board, log in as GUEST, read a
     * message, download a game with TRIMODEM onto the triskette, hang up, BRUN it.
     */
    @Test
    void callReadDownloadHangUpAndRun() {
        Network net = LocalNode.network();
        Triada t = machine(net);
        t.typeOnly("DIAL \"3-43-21\"\n");
        waitFor(t, "DIALING 3-43-21", 60);
        waitFor(t, "HANDLE (GUEST, NEW):", 3_000);
        assertTrue(String.join("\n", t.screen()).contains("PIONEER PALACE CLUB"));

        t.typeOnly("GUEST\n");
        waitFor(t, "MIN LEFT. COMMAND?", 2_000);
        long id = messageId(net, "3-43-21", "CLUB", "TRIZMEYKA RECORD");
        t.typeOnly("M1R" + id + "\n");                       // messages, area 1, read
        waitFor(t, "MY SNAKE ATE 61 TRITS", 2_000);
        waitFor(t, "N NEXT A ANSWER R REPORT Q", 1_000);
        t.typeOnly("QQ");                                    // back to the main menu
        waitFor(t, "MIN LEFT. COMMAND?", 2_000);

        t.typeOnly("F");
        waitFor(t, "TRIZMEYK.TRI", 2_000);
        waitFor(t, "U UPLOAD    Q BACK", 2_000);
        t.typeOnly("DTRIZMEYK.TRI\n");
        waitFor(t, "RECEIVING TRIZMEYK.TRI", 2_000);
        waitFor(t, "SAVED TRIZMEYK.TRI", 10_000);
        waitFor(t, "PRESS ANY KEY.", 1_000);

        TriImage got = t.m.triskette().port().read("TRIZMEYK.TRI");
        assertNotNull(got, "the game is on the triskette");
        TriImage sent = Boards.file("TRIZMEYK.TRI");
        assertEquals(sent.loadAddress(), got.loadAddress());
        assertArrayEquals(sent.words(), got.words());

        t.typeOnly("\u001bH");                               // Esc H: hang up, back to TRIBASIC
        waitFor(t, "HUNG UP.", 300);
        t.waitForPrompt(600);
        assertTrue(net.online().isEmpty(), "the board saw the hang-up");

        t.clearScreen();
        t.typeOnly("BRUN \"TRIZMEYK\"\n");
        waitFor(t, "PRESS Z TO START", 600);
        assertTrue(t.row(0).startsWith("TRIZMEYKA  SCORE 0"), t.row(0));
    }

    @Test
    void aDownloadedTribasicProgramLoadsAndRuns() {
        Network net = LocalNode.network();
        Triada t = machine(net);
        t.typeOnly("DIAL \"2-27-81\"\n");
        waitFor(t, "HANDLE (GUEST, NEW):", 3_000);
        t.typeOnly("GUEST\n");
        waitFor(t, "MIN LEFT. COMMAND?", 2_000);
        t.typeOnly("F");
        waitFor(t, "U UPLOAD    Q BACK", 3_000);
        t.typeOnly("DORACLE.TB\n");
        waitFor(t, "SAVED ORACLE.TB", 10_000);
        waitFor(t, "PRESS ANY KEY.", 1_000);
        t.typeOnly("\u001bH");
        t.waitForPrompt(600);
        t.clearScreen();
        t.typeOnly("LOAD \"ORACLE\"\nRUN\n");
        waitFor(t, "(Y/N/?)", 600);
    }

    @Test
    void thePhonebookAndAnOfflineCall() {
        Triada t = machine(null);                            // no network at all
        t.typeOnly("BRUN \"TERM\"\n");
        waitFor(t, "TRIADA TERM  PHONEBOOK", 300);
        List<String> s = t.screen();
        assertEquals("1 0-00-00 TRIADA-NET INFO", s.get(2));
        assertEquals("2 1-85-01 TRIADA SUPPORT", s.get(3));
        assertTrue(String.join("\n", s).contains("N NEW NUMBER   ESC QUIT"));
        t.typeOnly("1");
        waitFor(t, "DIALING 0-00-00", 60);
        waitFor(t, "TRIADA-NET IS OFFLINE", 600);
        assertTrue(String.join("\n", t.screen()).contains("NO CARRIER"));
        t.typeOnly(" ");                                     // any key: back to the phonebook
        waitFor(t, "TRIADA TERM  PHONEBOOK", 60);
        t.typeOnly("\u001b");                                // Esc: back to TRIBASIC
        t.waitForPrompt(600);
    }

    @Test
    void aNewNumberIsDialedAndKeptInThePhonebook() {
        Network net = LocalNode.network();
        Triada t = machine(net);
        t.typeOnly("BRUN \"TERM\"\n");
        waitFor(t, "N NEW NUMBER", 300);
        t.typeOnly("N9-13-13\n");
        waitFor(t, "THE QUIET LINE", 3_000);
        waitFor(t, "HANDLE (GUEST, NEW):", 3_000);
        t.typeOnly("\u001bH");
        t.waitForPrompt(600);
        TriImage book = t.m.triskette().port().read("PHONE.DAT");
        assertNotNull(book, "PHONE.DAT was saved");
        StringBuilder text = new StringBuilder();
        for (int w : book.words()) {
            text.append(w == 0 ? '|' : (char) w);
        }
        assertTrue(text.toString().endsWith("|9-13-13||"), text.toString());
    }

    @Test
    void busyAndNoSuchNumber() {
        Network net = LocalNode.network();
        Triada first = machine(net);
        first.typeOnly("DIAL \"9-13-13\"\n");                // the Quiet Line has one line
        waitFor(first, "HANDLE (GUEST, NEW):", 3_000);
        Triada second = machine(net);
        second.typeOnly("DIAL \"9-13-13\"\n");
        waitFor(second, "BUSY", 600);
        second.typeOnly(" ");
        second.waitForPrompt(600);
        second.clearScreen();
        second.typeOnly("DIAL \"5-55-55\"\n");
        waitFor(second, "NO SUCH NUMBER", 600);
    }

    @Test
    void anUploadFromTheTriskette() {
        Network net = LocalNode.network();
        Triada t = machine(net);
        t.typeOnly("10 PRINT \"HELLO FROM THE UPLOAD\"\nSAVE \"MINE\"\nNEW\n");
        t.waitForPrompt(600);
        t.typeOnly("DIAL \"3-43-21\"\n");
        waitFor(t, "HANDLE (GUEST, NEW):", 3_000);
        t.typeOnly("NEW\nY\nPETYA\nsecret1\nsecret1\n");
        waitFor(t, "MIN LEFT. COMMAND?", 3_000);
        t.typeOnly("F");
        waitFor(t, "U UPLOAD    Q BACK", 3_000);
        t.typeOnly("U");
        waitFor(t, "TRIMODEM NOW.", 600);
        t.typeOnly("\u001bUMINE.TB\n");                      // Esc U: send a file
        waitFor(t, "DESCRIPTION:", 10_000);
        t.typeOnly("MY FIRST PROGRAM\n");
        waitFor(t, "CHECK IT FIRST.", 600);
        List<NetStore.FileEntry> q = net.store().queue();
        assertEquals(1, q.size());
        assertEquals("MINE.TB", q.get(0).name());
        assertEquals("PETYA", q.get(0).uploader());
        assertArrayEquals(t.m.triskette().port().read("MINE.TB").words(), q.get(0).file().words());
    }

    @Test
    void theBoardsColourIsKeptOnlyWhileOnline() {
        Network net = LocalNode.network();
        Triada t = machine(net);
        int fg = t.m.bus().read(41);
        t.typeOnly("DIAL \"2-27-81\"\n");                    // the magazine: yellow text
        waitFor(t, "HANDLE (GUEST, NEW):", 3_000);
        assertEquals(11, t.m.bus().read(41), "ESC C changed the font's colour");
        t.typeOnly("\u001bH");
        t.waitForPrompt(600);
        assertEquals(fg, t.m.bus().read(41), "and TERM put it back");
    }
}
