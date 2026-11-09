package org.triada.net.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.triada.core.TriImage;
import org.triada.net.NetStore;
import org.triada.net.Trimodem;

/**
 * The server's {@link NetStore}: one SQLite file, through JDBC (M18). One
 * connection, and every method synchronized on it: SQLite writes one thing at
 * a time anyway, and each call here is short. Every value goes in through a
 * prepared statement, never pasted into SQL, so a handle or a subject can't
 * change a query. Files are kept in the database as {@code .tri} bytes; no
 * caller-chosen name ever becomes a path.
 */
public final class SqliteNetStore implements NetStore, AutoCloseable {

    private final Connection db;

    /** Opens (or creates) the database at {@code path} and brings it up to date. */
    public SqliteNetStore(String path) throws SQLException {
        db = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA journal_mode = WAL");          // readers don't wait for writers
            s.execute("PRAGMA busy_timeout = 5000");
        }
        Migrations.run(db);
    }

    @Override
    public synchronized void close() throws SQLException {
        db.close();
    }

    /** A failed query is a broken server, not a caller's mistake. */
    private static IllegalStateException fail(SQLException e) {
        return new IllegalStateException("database: " + e.getMessage(), e);
    }

    private PreparedStatement prepare(String sql, Object... args) throws SQLException {
        PreparedStatement p = db.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            p.setObject(i + 1, args[i]);
        }
        return p;
    }

    private void update(String sql, Object... args) {
        try (PreparedStatement p = prepare(sql, args)) {
            p.executeUpdate();
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    private long insert(String sql, Object... args) {
        try (PreparedStatement p = prepare(sql, args)) {
            p.executeUpdate();
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
                r.next();
                return r.getLong(1);
            }
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    private interface Row<T> {
        T read(ResultSet r) throws SQLException;
    }

    private <T> List<T> query(Row<T> row, String sql, Object... args) {
        try (PreparedStatement p = prepare(sql, args); ResultSet r = p.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (r.next()) {
                out.add(row.read(r));
            }
            return out;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    private static Instant time(ResultSet r, String column) throws SQLException {
        long ms = r.getLong(column);
        return r.wasNull() ? null : Instant.ofEpochMilli(ms);
    }

    private static Long ms(Instant t) {
        return t == null ? null : t.toEpochMilli();
    }

    // ------------------------------------------------------------ accounts

    private static User user(ResultSet r) throws SQLException {
        return new User(r.getString("handle"), r.getString("password"), r.getInt("failures"),
                time(r, "locked_until"), r.getInt("banned") != 0, time(r, "created"));
    }

    @Override
    public synchronized User user(String handle) {
        List<User> u = query(SqliteNetStore::user, "SELECT * FROM users WHERE handle = ?", handle);
        return u.isEmpty() ? null : u.get(0);
    }

    @Override
    public synchronized boolean addUser(User u) {
        try (PreparedStatement p = prepare("INSERT OR IGNORE INTO users (handle, password, failures, locked_until,"
                + " banned, created) VALUES (?, ?, ?, ?, ?, ?)", u.handle(), u.password(), u.failures(),
                ms(u.lockedUntil()), u.banned() ? 1 : 0, ms(u.created()))) {
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    @Override
    public synchronized void updateUser(User u) {
        update("UPDATE users SET password = ?, failures = ?, locked_until = ?, banned = ? WHERE handle = ?",
                u.password(), u.failures(), ms(u.lockedUntil()), u.banned() ? 1 : 0, u.handle());
    }

    // ------------------------------------------------------------ messages

    private static Message message(ResultSet r) throws SQLException {
        return new Message(r.getLong("id"), r.getString("board"), r.getString("area"), r.getString("author"),
                r.getString("subject"), r.getString("body"), time(r, "time"), r.getLong("reply_to"));
    }

    @Override
    public synchronized long post(Message m) {
        return insert("INSERT INTO messages (board, area, author, subject, body, time, reply_to)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)", m.board(), m.area(), m.author(), m.subject(), m.body(),
                ms(m.time()), m.replyTo());
    }

    @Override
    public synchronized int countMessages(String board, String area) {
        return query(r -> r.getInt(1), "SELECT COUNT(*) FROM messages WHERE board = ? AND area = ? AND hidden = 0",
                board, area).get(0);
    }

    @Override
    public synchronized List<Message> messages(String board, String area, int skip, int limit) {
        return query(SqliteNetStore::message, "SELECT * FROM messages WHERE board = ? AND area = ? AND hidden = 0"
                + " ORDER BY id DESC LIMIT ? OFFSET ?", board, area, limit, skip);
    }

    @Override
    public synchronized Message message(long id) {
        List<Message> m = query(SqliteNetStore::message, "SELECT * FROM messages WHERE id = ? AND hidden = 0", id);
        return m.isEmpty() ? null : m.get(0);
    }

    @Override
    public synchronized boolean hideMessage(long id) {
        try (PreparedStatement p = prepare("UPDATE messages SET hidden = 1 WHERE id = ? AND hidden = 0", id)) {
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    @Override
    public synchronized void report(long messageId, String reporter, Instant time) {
        insert("INSERT INTO reports (message_id, reporter, time) VALUES (?, ?, ?)", messageId, reporter, ms(time));
    }

    @Override
    public synchronized List<Report> reports() {
        return query(r -> new Report(r.getLong("id"), r.getLong("message_id"), r.getString("reporter"),
                time(r, "time")), "SELECT * FROM reports ORDER BY id DESC");
    }

    // ------------------------------------------------------------ mail

    @Override
    public synchronized long sendMail(Mail m) {
        return insert("INSERT INTO mail (sender, recipient, subject, body, time) VALUES (?, ?, ?, ?, ?)",
                m.from(), m.to(), m.subject(), m.body(), ms(m.time()));
    }

    @Override
    public synchronized List<Mail> mailFor(String handle) {
        return query(r -> new Mail(r.getLong("id"), r.getString("sender"), r.getString("recipient"),
                r.getString("subject"), r.getString("body"), time(r, "time"), r.getInt("seen") != 0),
                "SELECT * FROM mail WHERE recipient = ? ORDER BY id DESC", handle);
    }

    @Override
    public synchronized void markRead(long id) {
        update("UPDATE mail SET seen = 1 WHERE id = ?", id);
    }

    // ------------------------------------------------------------ files

    private static FileEntry file(ResultSet r) throws SQLException {
        return new FileEntry(r.getLong("id"), r.getString("board"), r.getString("area"), r.getString("name"),
                Trimodem.Type.valueOf(r.getString("type")), r.getString("description"),
                TriImage.fromBytes(r.getBytes("data")), r.getString("uploader"),
                FileStatus.valueOf(r.getString("status")), time(r, "time"));
    }

    @Override
    public synchronized long addFile(FileEntry f) {
        return insert("INSERT INTO files (board, area, name, type, description, data, words, uploader, status, time)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", f.board(), f.area(), f.name(), f.type().name(),
                f.description(), f.file().toBytes(), f.file().size(), f.uploader(), f.status().name(), ms(f.time()));
    }

    @Override
    public synchronized List<FileEntry> files(String board, String area) {
        return query(SqliteNetStore::file, "SELECT * FROM files WHERE board = ? AND area = ? AND status = 'PUBLIC'"
                + " ORDER BY name", board, area);
    }

    @Override
    public synchronized FileEntry file(long id) {
        List<FileEntry> f = query(SqliteNetStore::file, "SELECT * FROM files WHERE id = ?", id);
        return f.isEmpty() ? null : f.get(0);
    }

    @Override
    public synchronized List<FileEntry> queue() {
        return query(SqliteNetStore::file, "SELECT * FROM files WHERE status = 'QUEUED' ORDER BY id");
    }

    @Override
    public synchronized boolean setFileStatus(long id, FileStatus status) {
        try (PreparedStatement p = prepare("UPDATE files SET status = ? WHERE id = ?", status.name(), id)) {
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            throw fail(e);
        }
    }

    @Override
    public synchronized long wordsUploaded(String handle) {
        return query(r -> r.getLong(1), "SELECT COALESCE(SUM(words), 0) FROM files WHERE uploader = ?"
                + " AND status <> 'REJECTED'", handle).get(0);
    }

    // ------------------------------------------------------------ the board's life

    @Override
    public synchronized void logCall(String board, String handle, Instant time) {
        insert("INSERT INTO calls (board, handle, time) VALUES (?, ?, ?)", board, handle, ms(time));
    }

    @Override
    public synchronized List<CallLog> lastCallers(String board, int limit) {
        return query(r -> new CallLog(r.getString("handle"), time(r, "time")),
                "SELECT * FROM calls WHERE board = ? ORDER BY id DESC LIMIT ?", board, limit);
    }

    @Override
    public synchronized void addOneLiner(String board, String handle, String text, Instant time) {
        insert("INSERT INTO one_liners (board, handle, text, time) VALUES (?, ?, ?, ?)", board, handle, text, ms(time));
    }

    @Override
    public synchronized List<OneLiner> oneLiners(String board, int limit) {
        return query(r -> new OneLiner(r.getString("handle"), r.getString("text"), time(r, "time")),
                "SELECT * FROM one_liners WHERE board = ? ORDER BY id DESC LIMIT ?", board, limit);
    }

    @Override
    public synchronized long secondsUsed(String board, String who, LocalDate day) {
        List<Long> s = query(r -> r.getLong(1), "SELECT seconds FROM time_used WHERE board = ? AND who = ? AND day = ?",
                board, who, day.toString());
        return s.isEmpty() ? 0 : s.get(0);
    }

    @Override
    public synchronized void addSeconds(String board, String who, LocalDate day, long seconds) {
        update("INSERT INTO time_used (board, who, day, seconds) VALUES (?, ?, ?, ?)"
                + " ON CONFLICT (board, who, day) DO UPDATE SET seconds = seconds + excluded.seconds",
                board, who, day.toString(), seconds);
    }

    // ------------------------------------------------------------ addresses

    @Override
    public synchronized boolean ipBanned(String ip) {
        return !query(r -> r.getString(1), "SELECT ip FROM banned_ips WHERE ip = ?", ip).isEmpty();
    }

    @Override
    public synchronized void banIp(String ip, boolean banned) {
        update(banned ? "INSERT OR IGNORE INTO banned_ips (ip) VALUES (?)" : "DELETE FROM banned_ips WHERE ip = ?",
                ip);
    }

    // ------------------------------------------------------------ content (M19)

    @Override
    public synchronized boolean markSeeded(String key) {
        try (PreparedStatement p = prepare("INSERT OR IGNORE INTO seeds (key) VALUES (?)", key)) {
            return p.executeUpdate() == 1;
        } catch (SQLException e) {
            throw fail(e);
        }
    }
}
