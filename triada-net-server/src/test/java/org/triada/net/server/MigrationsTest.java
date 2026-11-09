package org.triada.net.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The migration runner (M18, D-066): new databases, up-to-date ones, upgrades and failures. */
class MigrationsTest {

    @TempDir
    Path dir;

    private Connection open(String name) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dir.resolve(name));
    }

    private static List<String> tables(Connection db) throws SQLException {
        List<String> t = new java.util.ArrayList<>();
        try (Statement s = db.createStatement();
                ResultSet r = s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'"
                        + " AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (r.next()) {
                t.add(r.getString(1));
            }
        }
        return t;
    }

    @Test
    void aNewDatabaseGetsEveryTable() throws SQLException {
        try (Connection db = open("new.db")) {
            try (Statement s = db.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            }
            assertEquals(0, Migrations.version(db));
            assertEquals(Migrations.SCRIPTS.size(), Migrations.run(db));
            assertEquals(List.of("banned_ips", "calls", "files", "mail", "messages", "one_liners", "reports",
                    "schema_version", "seeds", "time_used", "users"), tables(db));
        }
    }

    @Test
    void runningAgainChangesNothing() throws SQLException {
        try (Connection db = open("twice.db")) {
            Migrations.run(db);
            try (Statement s = db.createStatement()) {
                s.execute("INSERT INTO banned_ips (ip) VALUES ('10.0.0.1')");
            }
            assertEquals(Migrations.SCRIPTS.size(), Migrations.run(db));
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM banned_ips")) {
                r.next();
                assertEquals(1, r.getInt(1), "the data is still there");
            }
        }
    }

    @Test
    void anOldDatabaseIsUpgradedKeepingItsData() throws SQLException {
        try (Connection db = open("old.db")) {
            Migrations.run(db, List.of("V001__schema.sql"));
            try (Statement s = db.createStatement()) {
                s.execute("INSERT INTO users (handle, password, created) VALUES ('OLGA', 'x', 0)");
            }
            assertEquals(2, Migrations.run(db, List.of("V001__schema.sql", "test/V002__nicknames.sql")));
            try (Statement s = db.createStatement();
                    ResultSet r = s.executeQuery("SELECT handle, nickname FROM users")) {
                r.next();
                assertEquals("OLGA", r.getString(1));
                assertEquals("COMRADE", r.getString(2));
            }
        }
    }

    @Test
    void aBrokenScriptChangesNothing() throws SQLException {
        try (Connection db = open("broken.db")) {
            Migrations.run(db);
            SQLException e = assertThrows(SQLException.class,
                    () -> Migrations.run(db, List.of("V001__schema.sql", "V002__seeds.sql", "test/V003__broken.sql")));
            assertTrue(e.getMessage().contains("V003__broken.sql"), e.getMessage());
            assertEquals(Migrations.SCRIPTS.size(), Migrations.version(db));
            assertTrue(!tables(db).contains("half_done"), "the whole script was rolled back");
        }
    }

    @Test
    void commentsAndStatements() {
        assertEquals(List.of("CREATE TABLE a (x INTEGER)", "CREATE TABLE b (y TEXT)"),
                Migrations.statements("-- two tables\nCREATE TABLE a (x INTEGER); -- one\nCREATE TABLE b (y TEXT);\n"));
    }
}
