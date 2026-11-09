package org.triada.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;

/** Every screen and menu action of a board (NET-SPEC §4, §5; M18), with a scripted caller. */
class BoardSessionTest {

    static final String BOARD = """
            NUMBER 1-00-00
            NAME TEST BOARD
            SYSOP TESTER
            LINES 2
            MESSAGES GENERAL GENERAL TALK
            MESSAGES TECH TECHNICAL
            FILES PROGRAMS PROGRAMS
            WELCOME
             * WELCOME *
            END
            """;
    static final String HIDDEN = """
            NUMBER 9-99-99
            NAME THE BACK ROOM
            UNLISTED
            MESSAGES GENERAL WHISPERS
            """;

    private final TestClock clock = new TestClock();
    private final MemoryNetStore store = new MemoryNetStore();
    private final List<String> log = new ArrayList<>();
    private final Network net = new Network(List.of(BoardDef.parse(BOARD), BoardDef.parse(HIDDEN)), store,
            Limits.DEFAULT.withPasswordIterations(10), clock,
            (event, fields) -> log.add(event + " " + java.util.Arrays.toString(fields)));

    private TestCaller call() {
        return new TestCaller(net, "1-00-00");
    }

    private TestCaller guest() {
        return call().type("GUEST\n");
    }

    private TestCaller register(String handle, String password) {
        return call().type("NEW\nY\n" + handle + "\n" + password + "\n" + password + "\n");
    }

    private TestCaller login(String handle, String password) {
        return call().type(handle + "\n" + password + "\n");
    }

    private static void contains(TestCaller c, String text) {
        assertTrue(c.text().contains(text), "expected '" + text + "' on screen:\n" + c.text());
    }

    // ================================================================ welcome and login

    @Test
    void theWelcomeScreen() {
        TestCaller c = call();
        assertEquals("CONNECT 1200", c.answer());
        assertEquals(List.of(" * WELCOME *", "TEST BOARD", "SYSOP: TESTER", "1985-11-09", "HANDLE (GUEST, NEW):"),
                c.screen.lines());
    }

    @Test
    void guestsReadButDontWrite() {
        TestCaller c = guest();
        contains(c, "M MESSAGES   E MAIL");
        contains(c, "30 MIN LEFT. COMMAND?");
        c.type("E");
        contains(c, "GUESTS CAN ONLY READ.");
        c.type(" M1P");                                           // messages, area 1, post
        contains(c, "GUESTS CAN ONLY READ.");
        assertEquals("GUEST", store.lastCallers("1-00-00", 1).get(0).handle());
    }

    @Test
    void aNewCallerAgreesToTheRulesAndRegisters() {
        TestCaller c = call().type("NEW\n");
        contains(c, "SYSOP RULES");
        contains(c, "NO REAL NAMES, NO");
        c.type("Y\nGUEST\n");
        contains(c, "2-12 LETTERS AND DIGITS.");
        c.type("A\n");
        contains(c, "2-12 LETTERS AND DIGITS.");
        c.type("olga\nabc\n");
        contains(c, "TOO SHORT.");
        c.type("secret1\nsecret2\n");
        contains(c, "NOT THE SAME.");
        c.type("secret1\nsecret1\n");
        contains(c, "M MESSAGES");
        NetStore.User olga = store.user("OLGA");
        assertNotNull(olga);
        assertFalse(olga.password().contains("secret1"), "only a hash is stored");
        assertTrue(log.stream().anyMatch(l -> l.startsWith("registered [handle, OLGA")));
        assertTrue(log.stream().noneMatch(l -> l.contains("secret1")), "no password in the log");
        assertTrue(c.text().contains("*******") || !c.text().contains("secret1"), "the password is masked");
        assertTrue(register("OLGA", "another1").text().contains("TAKEN."));
    }

    @Test
    void refusingTheRulesHangsUp() {
        TestCaller c = call().type("NEW\nN");
        contains(c, "GOODBYE.");
        assertTrue(c.closed());
    }

    @Test
    void loginAndWrongPasswords() {
        register("OLGA", "secret1").hangUp();
        TestCaller ok = login("olga", "secret1");
        contains(ok, "M MESSAGES");
        TestCaller wrong = login("OLGA", "nope");
        contains(wrong, "WRONG.");
        wrong.type("NOBODY\nsecret1\n");                         // an unknown handle reads the same
        contains(wrong, "WRONG.");
        assertFalse(wrong.closed());
        wrong.type("OLGA\nnope\n");                               // the third wrong try in one call
        contains(wrong, "WRONG. GOODBYE.");
        assertTrue(wrong.closed());
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() {
        register("OLGA", "secret1").hangUp();
        for (int i = 0; i < 5; i++) {
            login("OLGA", "bad" + i).hangUp();
        }
        TestCaller locked = login("OLGA", "secret1");
        contains(locked, "LOCKED. CALL AGAIN LATER.");
        clock.advance(Duration.ofMinutes(16));
        contains(login("OLGA", "secret1"), "M MESSAGES");
    }

    @Test
    void oneAddressCantGuessForever() {
        register("OLGA", "secret1").hangUp();
        for (int i = 0; i < 3; i++) {
            new TestCaller(net, "1-00-00", "10.0.0.7").type("A\nx\nB\nx\nC\nx\n");     // 9 failures
        }
        TestCaller c = new TestCaller(net, "1-00-00", "10.0.0.7").type("D\nx\n");     // the tenth
        contains(c, "WRONG. GOODBYE.");
        assertTrue(c.closed());
    }

    @Test
    void bannedHandlesAndAddresses() {
        register("OLGA", "secret1").hangUp();
        NetStore.User u = store.user("OLGA");
        store.updateUser(new NetStore.User("OLGA", u.password(), 0, null, true, u.created()));
        contains(login("OLGA", "secret1"), "THIS HANDLE IS BANNED.");
        store.banIp("10.0.0.66", true);
        assertEquals("NO CARRIER", new TestCaller(net, "1-00-00", "10.0.0.66").answer());
    }

    @Test
    void threeAccountsPerAddressPerDay() {
        for (String h : List.of("AAA", "BBB", "CCC")) {
            register(h, "secret1").hangUp();
        }
        TestCaller fourth = register("DDD", "secret1");
        contains(fourth, "NO MORE NEW CALLERS FROM");
        assertNull(store.user("DDD"));
    }

    // ================================================================ messages

    private TestCaller post(TestCaller c, String subject, String body) {
        return c.type("M1P" + subject + "\n" + body + "\n.\nS");
    }

    @Test
    void postListReadAnswerAndReport() {
        TestCaller olga = register("OLGA", "secret1");
        post(olga, "HELLO", "FIRST POST ON THE BOARD.");
        contains(olga, "SAVED AS #1.");
        olga.type(" ");
        post(olga, "SECOND", "MORE TEXT");
        olga.type(" L");
        contains(olga, "#2 SECOND");
        int second = olga.text().indexOf("#2 SECOND");
        assertTrue(second >= 0 && second < olga.text().indexOf("#1 HELLO"), "newest first");
        olga.type(" R2\n");
        contains(olga, "#2 FROM OLGA");
        contains(olga, "1985-11-09 12:00");
        contains(olga, "MORE TEXT");
        olga.type("N");                                           // the next, older one
        contains(olga, "FIRST POST ON THE BOARD.");
        olga.type("A");
        contains(olga, "SUBJECT: RE: HELLO");
        olga.type("I AGREE\n.\nS");
        NetStore.Message reply = store.messages("1-00-00", "GENERAL", 0, 1).get(0);
        assertEquals("RE: HELLO", reply.subject());
        assertEquals(1, reply.replyTo());
        olga.type(" R1\nR");
        contains(olga, "THANK YOU. THE SYSOP");
        assertEquals(1, store.reports().get(0).messageId());
        olga.type(" R99\n");
        contains(olga, "NO SUCH MESSAGE.");
    }

    @Test
    void messagesStayInTheirArea() {
        TestCaller olga = register("OLGA", "secret1");
        post(olga, "IN GENERAL", "X");
        olga.type(" Q");
        olga.type("M2R1\n");                                      // area 2 (TECH) has no #1
        contains(olga, "NO SUCH MESSAGE.");
    }

    @Test
    void longListsArePaged() {
        for (int i = 1; i <= 20; i++) {
            store.post(new NetStore.Message(0, "1-00-00", "GENERAL", "BOT", "POST " + i, "X", clock.instant(), 0));
        }
        TestCaller c = guest().type("M1L");
        contains(c, "MORE? (Y/N)");
        contains(c, "#20 POST 20");
        assertFalse(c.text().contains("#1 POST 1\n"));
        c.type("Y");
        contains(c, "#1 POST 1");
        contains(c, "PRESS ANY KEY.");
    }

    @Test
    void messageLimits() {
        TestCaller olga = register("OLGA", "secret1");
        olga.type("M1P" + "S".repeat(40) + "\n");                 // the subject stops at 24
        assertTrue(olga.screen.bells() > 0);
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 45; i++) {
            lines.append("LINE ").append(i).append('\n');
        }
        olga.type(lines.toString());
        contains(olga, "THAT IS THE LIMIT.");
        olga.type("S");
        NetStore.Message m = store.messages("1-00-00", "GENERAL", 0, 1).get(0);
        assertEquals(24, m.subject().length());
        assertEquals(40, m.body().split("\n").length);
        olga.type(" P" + "X\n" + ("Y".repeat(80) + "\n").repeat(30));
        olga.type("S");
        assertTrue(store.messages("1-00-00", "GENERAL", 0, 1).get(0).body().length() <= 2_000);
    }

    @Test
    void writingTooFast() {
        TestCaller olga = register("OLGA", "secret1");
        for (int i = 0; i < 5; i++) {
            post(olga, "P" + i, "X").type(" ");
        }
        olga.type("P");
        contains(olga, "YOU WRITE TOO FAST.");
        assertEquals(5, store.countMessages("1-00-00", "GENERAL"));
    }

    @Test
    void anEmptyMessageOrAnAbortSavesNothing() {
        TestCaller olga = register("OLGA", "secret1");
        olga.type("M1P\n");                                       // no subject
        olga.type("PHELLO\n.\n");                                 // no text
        olga.type("PHELLO\nTEXT\n.\nA");                          // aborted
        assertEquals(0, store.countMessages("1-00-00", "GENERAL"));
    }

    // ================================================================ mail

    @Test
    void privateMail() {
        register("PETYA", "secret1").hangUp();
        TestCaller olga = register("OLGA", "secret1");
        olga.type("ESNOBODY\n");
        contains(olga, "NO SUCH CALLER.");
        olga.type(" SPETYA\nMEET AT SIX\nBRING THE TAPE.\n.\nS");
        contains(olga, "SENT.");
        TestCaller petya = login("PETYA", "secret1").type("E");
        contains(petya, "*#");
        contains(petya, "OLGA: MEET AT SIX");
        long id = store.mailFor("PETYA").get(0).id();
        petya.type("R" + id + "\n");
        contains(petya, "BRING THE TAPE.");
        assertTrue(store.mailFor("PETYA").get(0).read());
        olga.type(" R" + id + "\n");
        contains(olga, "NO SUCH MAIL.");                          // not hers to read
    }

    // ================================================================ files

    private long publicFile(String name, int words) {
        return store.addFile(new NetStore.FileEntry(0, "1-00-00", "PROGRAMS", name, Trimodem.Type.TRI,
                "A FINE GAME", new TriImage(243, new int[words]), "SYSOP", NetStore.FileStatus.PUBLIC,
                clock.instant()));
    }

    @Test
    void listInfoAndDownload() {
        publicFile("SNAKE.TRI", 100);
        TestCaller c = guest().type("F");
        contains(c, "SNAKE.TRI       100W");
        contains(c, "A FINE GAME");
        c.type("Isnake.tri\n");
        contains(c, "SNAKE.TRI, 100 WORDS");
        c.type(" D../SNAKE.TRI\n");
        contains(c, "NO SUCH FILE.");                             // not a name: never a path
        c.type(" DSNAKE.TRI\n");
        contains(c, "SENDING SNAKE.TRI BY");
        TrimodemReceiver r = new TrimodemReceiver();
        for (int round = 0; round < 10 && !r.done(); round++) {
            for (byte b : c.takeRaw()) {
                for (byte answer : r.receive(b & 0xFF)) {
                    c.send(answer);
                }
            }
        }
        assertTrue(r.done());
        assertEquals("SNAKE.TRI", r.name());
        assertEquals(100, r.file().size());
        contains(c, "DONE.");
    }

    @Test
    void aSilentReceiverMakesTheDownloadFail() {
        publicFile("SNAKE.TRI", 10);
        TestCaller c = guest().type("FDSNAKE.TRI\n");
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofSeconds(11));
            c.tick();
        }
        contains(c, "TRANSFER FAILED.");
    }

    private TestCaller upload(TestCaller c, String name, int words) {
        c.type("FU");
        TrimodemSender s = new TrimodemSender(name, Trimodem.Type.TRI, new TriImage(243, new int[words]));
        byte[] next = s.start();
        for (int round = 0; round < 1_000 && next.length > 0; round++) {
            byte[] sending = next;
            next = new byte[0];
            for (byte b : sending) {
                c.takeRaw();
                c.send(b & 0xFF);
                for (byte answer : c.takeRaw()) {
                    if (answer == Trimodem.ACK || answer == Trimodem.NAK) {
                        next = s.receive(answer);
                    }
                }
            }
        }
        assertTrue(s.done(), "the upload went through");
        return c;
    }

    @Test
    void uploadsWaitForTheSysop() {
        TestCaller olga = upload(register("OLGA", "secret1"), "MYGAME.TRI", 200);
        contains(olga, "DESCRIPTION:");
        olga.type("MY FIRST GAME\n");
        contains(olga, "CHECK IT FIRST.");
        assertEquals(1, store.queue().size());
        assertEquals("MYGAME.TRI", store.queue().get(0).name());
        assertEquals("OLGA", store.queue().get(0).uploader());
        assertTrue(store.files("1-00-00", "PROGRAMS").isEmpty(), "not public yet");
        store.setFileStatus(store.queue().get(0).id(), NetStore.FileStatus.PUBLIC);
        contains(guest().type("F"), "MYGAME.TRI");
    }

    @Test
    void uploadLimits() {
        TestCaller guest = guest().type("FU");
        contains(guest, "GUESTS CAN ONLY READ.");
        guest.hangUp();                                           // the board has only two lines
        TestCaller big = upload(register("OLGA", "secret1"), "BIG.TRI", 20_000);
        contains(big, "TOO BIG: AT MOST 19683");
        assertTrue(store.queue().isEmpty());
        big.hangUp();
        store.addFile(new NetStore.FileEntry(0, "1-00-00", "PROGRAMS", "OLD.TRI", Trimodem.Type.TRI, "X",
                new TriImage(0, new int[59_049]), "PETYA", NetStore.FileStatus.QUEUED, clock.instant()));
        register("PETYA", "secret1").hangUp();
        TestCaller full = login("PETYA", "secret1").type("FU");
        contains(full, "YOUR SPACE IS FULL.");
        full.hangUp();
        TestCaller often = login("OLGA", "secret1");
        for (int i = 0; i < 2; i++) {
            upload(often, "F" + i + ".TRI", 5).type("X\n ");
        }
        often.type("U");
        contains(often, "TOO MANY UPLOADS.");
    }

    // ================================================================ the rest

    @Test
    void oneLinersLastCallersWhoAndTime() {
        TestCaller olga = register("OLGA", "secret1");
        olga.type("OAHELLO, TRIADA-NET!\n");
        contains(olga, "OLGA: HELLO, TRIADA-NET!");
        olga.type("Q");
        TestCaller g = guest().type("OA");
        contains(g, "GUESTS CAN ONLY READ.");
        g.type(" L");
        contains(g, "GUEST        11-09 12:00");
        contains(g, "OLGA         11-09 12:00");
        g.type(" W");
        contains(g, "WHO IS ON TRIADA-NET");
        contains(g, "OLGA         TEST BOARD");
        contains(g, "GUEST        TEST BOARD");
        clock.advance(Duration.ofMinutes(10));
        g.type(" T");
        contains(g, "20 MINUTES LEFT TODAY.");
    }

    @Test
    void goodbyeHangsUpAndCountsTheTime() {
        TestCaller olga = register("OLGA", "secret1");
        clock.advance(Duration.ofMinutes(7));
        olga.type("G");
        contains(olga, "THANK YOU FOR CALLING\nTEST BOARD.");
        assertTrue(olga.closed());
        assertEquals(420, store.secondsUsed("1-00-00", "OLGA", java.time.LocalDate.of(2026, 11, 9)));
        assertTrue(net.online().isEmpty());
    }

    @Test
    void theDailyTimeLimit() {
        TestCaller olga = register("OLGA", "secret1");
        for (int i = 0; i < 9; i++) {                             // keep typing, so not idle
            clock.advance(Duration.ofMinutes(3));
            olga.type("T ");
            olga.tick();
        }
        assertFalse(olga.closed());
        clock.advance(Duration.ofMinutes(3));
        olga.tick();
        contains(olga, "YOUR TIME IS UP. GOODBYE.");
        assertTrue(olga.closed());
        TestCaller again = login("OLGA", "secret1");
        contains(again, "YOUR TIME IS UP FOR TODAY.");
        clock.advance(Duration.ofDays(1));
        contains(login("OLGA", "secret1"), "M MESSAGES");
    }

    @Test
    void idleCallersAreHungUp() {
        TestCaller g = guest();
        clock.advance(Duration.ofMinutes(4));
        g.tick();
        assertFalse(g.closed());
        clock.advance(Duration.ofMinutes(2));
        g.tick();
        contains(g, "IDLE TOO LONG. GOODBYE.");
        assertTrue(g.closed());
    }

    @Test
    void floodingIsCutOff() {
        TestCaller g = guest();
        g.type("X".repeat(1_001));
        contains(g, "FLOODING. GOODBYE.");
        assertTrue(g.closed());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("flooding")));
    }

    @Test
    void anOverlongLineIsCutAndTheBellRings() {
        TestCaller g = call().type("H".repeat(100));
        assertEquals(88, g.screen.bells());                      // 12 characters fit
    }

    // ================================================================ the network

    @Test
    void busyLinesAndUnknownNumbers() {
        guest();
        guest();
        assertEquals("BUSY", call().answer());                    // the board has two lines
        assertEquals("NO NUMBER", new TestCaller(net, "5-55-55").answer());
    }

    @Test
    void theDirectoryListsPublicBoardsOnly() {
        TestCaller c = new TestCaller(net, Network.INFO_NUMBER);
        contains(c, "TRIADA-NET INFO");
        contains(c, "0-00-00 TRIADA-NET INFO");
        contains(c, "1-00-00 TEST BOARD");
        assertFalse(c.text().contains("BACK ROOM"));
        c.type("X");
        assertTrue(c.closed());
        TestCaller hidden = new TestCaller(net, "9-99-99");      // unlisted, but it answers
        contains(hidden, "THE BACK ROOM");
    }

    @Test
    void numbersMustBeUnique() {
        BoardDef b = BoardDef.parse(BOARD);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Network(List.of(b, b), store, Limits.DEFAULT, clock, NetLog.NONE));
    }
}
