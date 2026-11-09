package org.triada.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;

/**
 * What every {@link NetStore} must do (M18). The in-memory store and the
 * SQLite store run these same tests, so they can't drift apart.
 */
public abstract class NetStoreContract {

    private static final Instant T = Instant.parse("2026-11-09T12:00:00Z");

    /** A fresh, empty store. */
    protected abstract NetStore store();

    @Test
    void accounts() {
        NetStore s = store();
        assertNull(s.user("PETYA"));
        assertTrue(s.addUser(new NetStore.User("PETYA", "hash", 0, null, false, T)));
        assertFalse(s.addUser(new NetStore.User("PETYA", "other", 0, null, false, T)), "the handle is taken");
        s.updateUser(new NetStore.User("PETYA", "hash", 2, T.plusSeconds(60), true, T));
        assertEquals(new NetStore.User("PETYA", "hash", 2, T.plusSeconds(60), true, T), s.user("PETYA"));
    }

    @Test
    void messagesNewestFirstAndHidden() {
        NetStore s = store();
        long a = s.post(new NetStore.Message(0, "1-00-00", "GENERAL", "PETYA", "FIRST", "ONE", T, 0));
        long b = s.post(new NetStore.Message(0, "1-00-00", "GENERAL", "OLGA", "SECOND", "TWO\nLINES", T, a));
        s.post(new NetStore.Message(0, "1-00-00", "OTHER", "OLGA", "ELSEWHERE", "X", T, 0));
        s.post(new NetStore.Message(0, "2-00-00", "GENERAL", "OLGA", "ANOTHER BOARD", "X", T, 0));
        assertEquals(2, s.countMessages("1-00-00", "GENERAL"));
        List<NetStore.Message> list = s.messages("1-00-00", "GENERAL", 0, 10);
        assertEquals(List.of("SECOND", "FIRST"), list.stream().map(NetStore.Message::subject).toList());
        assertEquals(new NetStore.Message(b, "1-00-00", "GENERAL", "OLGA", "SECOND", "TWO\nLINES", T, a), s.message(b));
        assertEquals(List.of("FIRST"), s.messages("1-00-00", "GENERAL", 1, 10).stream()
                .map(NetStore.Message::subject).toList());
        assertTrue(s.hideMessage(a));
        assertFalse(s.hideMessage(a), "already hidden");
        assertNull(s.message(a));
        assertEquals(1, s.countMessages("1-00-00", "GENERAL"));
        assertFalse(s.hideMessage(9_999));
    }

    @Test
    void reports() {
        NetStore s = store();
        s.report(5, "OLGA", T);
        s.report(6, "PETYA", T.plusSeconds(1));
        List<NetStore.Report> r = s.reports();
        assertEquals(List.of(6L, 5L), r.stream().map(NetStore.Report::messageId).toList());
        assertEquals("PETYA", r.get(0).reporter());
    }

    @Test
    void mail() {
        NetStore s = store();
        long a = s.sendMail(new NetStore.Mail(0, "OLGA", "PETYA", "HI", "HELLO", T, false));
        s.sendMail(new NetStore.Mail(0, "OLGA", "IVAN", "HI", "HELLO", T, false));
        long c = s.sendMail(new NetStore.Mail(0, "IVAN", "PETYA", "RE", "X", T, false));
        assertEquals(List.of(c, a), s.mailFor("PETYA").stream().map(NetStore.Mail::id).toList());
        s.markRead(a);
        assertTrue(s.mailFor("PETYA").get(1).read());
        assertFalse(s.mailFor("PETYA").get(0).read());
    }

    @Test
    void filesAndTheQueue() {
        NetStore s = store();
        TriImage game = new TriImage(243, new int[] {1, -2, 265_720});
        long q = s.addFile(new NetStore.FileEntry(0, "1-00-00", "PROGRAMS", "GAME.TRI", Trimodem.Type.TRI, "A GAME",
                game, "PETYA", NetStore.FileStatus.QUEUED, T));
        long p = s.addFile(new NetStore.FileEntry(0, "1-00-00", "PROGRAMS", "AAA.TB", Trimodem.Type.TB, "FIRST",
                new TriImage(243, new int[5]), "OLGA", NetStore.FileStatus.PUBLIC, T));
        assertEquals(List.of("AAA.TB"), s.files("1-00-00", "PROGRAMS").stream().map(NetStore.FileEntry::name).toList());
        assertEquals(List.of(q), s.queue().stream().map(NetStore.FileEntry::id).toList());
        assertEquals(3, s.wordsUploaded("PETYA"));
        assertTrue(s.setFileStatus(q, NetStore.FileStatus.PUBLIC));
        assertEquals(List.of("AAA.TB", "GAME.TRI"), s.files("1-00-00", "PROGRAMS").stream()
                .map(NetStore.FileEntry::name).toList());
        NetStore.FileEntry f = s.file(q);
        assertEquals(243, f.file().loadAddress());
        assertEquals(List.of(1, -2, 265_720), java.util.Arrays.stream(f.file().words()).boxed().toList());
        assertEquals(Trimodem.Type.TRI, f.type());
        assertTrue(s.queue().isEmpty());
        s.setFileStatus(p, NetStore.FileStatus.REJECTED);
        assertEquals(0, s.wordsUploaded("OLGA"), "rejected files don't count");
        assertFalse(s.setFileStatus(9_999, NetStore.FileStatus.PUBLIC));
        assertNull(s.file(9_999));
    }

    @Test
    void callersOneLinersTimeAndBans() {
        NetStore s = store();
        for (int i = 0; i < 12; i++) {
            s.logCall("1-00-00", "C" + i, T.plusSeconds(i));
        }
        s.logCall("2-00-00", "ELSEWHERE", T);
        assertEquals(List.of("C11", "C10", "C9"), s.lastCallers("1-00-00", 3).stream()
                .map(NetStore.CallLog::handle).toList());
        s.addOneLiner("1-00-00", "OLGA", "HELLO WALL", T);
        s.addOneLiner("1-00-00", "PETYA", "HI OLGA", T.plusSeconds(1));
        assertEquals(List.of("HI OLGA", "HELLO WALL"), s.oneLiners("1-00-00", 10).stream()
                .map(NetStore.OneLiner::text).toList());
        LocalDate day = LocalDate.of(2026, 11, 9);
        assertEquals(0, s.secondsUsed("1-00-00", "OLGA", day));
        s.addSeconds("1-00-00", "OLGA", day, 100);
        s.addSeconds("1-00-00", "OLGA", day, 50);
        s.addSeconds("1-00-00", "OLGA", day.plusDays(1), 7);
        assertEquals(150, s.secondsUsed("1-00-00", "OLGA", day));
        assertEquals(0, s.secondsUsed("2-00-00", "OLGA", day));
        assertFalse(s.ipBanned("10.0.0.9"));
        s.banIp("10.0.0.9", true);
        assertTrue(s.ipBanned("10.0.0.9"));
        s.banIp("10.0.0.9", false);
        assertFalse(s.ipBanned("10.0.0.9"));
        assertTrue(s.markSeeded("post 1-85-01 #1"));
        assertFalse(s.markSeeded("post 1-85-01 #1"), "seeded once only");
        assertTrue(s.markSeeded("post 1-85-01 #2"));
    }
}
