package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.net.NetStore;
import org.triada.net.NetStoreContract;

/** The SQLite store keeps the same contract as the in-memory one (M18), and keeps what it is given. */
class SqliteNetStoreTest extends NetStoreContract {

    @TempDir
    Path dir;
    private int n;
    private final java.util.List<SqliteNetStore> opened = new java.util.ArrayList<>();

    @Override
    protected NetStore store() {
        try {
            SqliteNetStore s = new SqliteNetStore(dir.resolve("test" + n++ + ".db").toString());
            opened.add(s);
            return s;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Windows can't delete an open database file. */
    @org.junit.jupiter.api.AfterEach
    void closeThem() throws SQLException {
        for (SqliteNetStore s : opened) {
            s.close();
        }
    }

    @Test
    void whatIsWrittenSurvivesAReopen() throws SQLException {
        String file = dir.resolve("keep.db").toString();
        try (SqliteNetStore s = new SqliteNetStore(file)) {
            s.addUser(new NetStore.User("OLGA", "hash", 0, null, false, Instant.EPOCH));
            s.post(new NetStore.Message(0, "1-00-00", "GENERAL", "OLGA", "HI", "X", Instant.EPOCH, 0));
        }
        try (SqliteNetStore s = new SqliteNetStore(file)) {
            assertEquals("hash", s.user("OLGA").password());
            assertEquals(1, s.countMessages("1-00-00", "GENERAL"));
        }
    }

    @Test
    void quotesInTextAreJustText() throws SQLException {
        try (SqliteNetStore s = new SqliteNetStore(dir.resolve("q.db").toString())) {
            String evil = "X'); DROP TABLE messages; --";
            long id = s.post(new NetStore.Message(0, "1-00-00", "GENERAL", "OLGA", evil, evil, Instant.EPOCH, 0));
            assertEquals(evil, s.message(id).subject());
            assertEquals(1, s.countMessages("1-00-00", "GENERAL"));
        }
    }
}
