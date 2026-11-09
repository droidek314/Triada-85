package org.triada.net.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Brings a TRIADA-NET database up to date (M18, D-066). Each change to the
 * tables is a numbered SQL script in {@code src/main/resources/db}, and the
 * database remembers in {@code schema_version} which number it has reached.
 * Starting the server runs every script it hasn't seen yet, each in one
 * transaction, oldest first. Scripts are never edited once released: a
 * change is always a new script.
 */
public final class Migrations {

    /** The scripts, in order. Script i (from 1) brings the database to version i. */
    public static final List<String> SCRIPTS = List.of("V001__schema.sql");

    private Migrations() {
    }

    /** Runs the scripts the database hasn't had yet; returns the version it is at now. */
    public static int run(Connection db) throws SQLException {
        return run(db, SCRIPTS);
    }

    /** As {@link #run(Connection)}, with another list of scripts (resource names under {@code /db/}). */
    public static int run(Connection db, List<String> scripts) throws SQLException {
        try (Statement s = db.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
        int version = version(db);
        boolean auto = db.getAutoCommit();
        db.setAutoCommit(false);
        try {
            for (int v = version + 1; v <= scripts.size(); v++) {
                try (Statement s = db.createStatement()) {
                    for (String statement : statements(read(scripts.get(v - 1)))) {
                        s.execute(statement);
                    }
                    s.execute("DELETE FROM schema_version");
                    s.execute("INSERT INTO schema_version (version) VALUES (" + v + ")");
                    db.commit();
                } catch (SQLException | RuntimeException e) {
                    db.rollback();
                    throw new SQLException("migration " + scripts.get(v - 1) + " failed: " + e.getMessage(), e);
                }
            }
        } finally {
            db.setAutoCommit(auto);
        }
        return version(db);
    }

    /** The database's version: 0 for a new one. */
    public static int version(Connection db) throws SQLException {
        try (Statement s = db.createStatement();
                ResultSet r = s.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    private static String read(String name) {
        try (InputStream in = Migrations.class.getResourceAsStream("/db/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no migration script /db/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("can't read /db/" + name, e);
        }
    }

    /** The script's statements: split at ';', with "--" comments removed. (Our scripts have no ';' in text.) */
    static List<String> statements(String script) {
        StringBuilder clean = new StringBuilder();
        for (String line : script.split("\n")) {
            int comment = line.indexOf("--");
            clean.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return java.util.Arrays.stream(clean.toString().split(";")).map(String::strip).filter(s -> !s.isEmpty())
                .toList();
    }
}
