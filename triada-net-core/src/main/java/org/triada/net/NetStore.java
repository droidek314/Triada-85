package org.triada.net;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.triada.core.TriImage;

/**
 * Everything TRIADA-NET remembers between calls (NET-SPEC §4, M18): callers'
 * accounts, messages and reports, mail, files and the upload queue, the
 * callers' log, one-liners, the time used each day, and banned addresses.
 * The server keeps it in SQLite; tests use {@link MemoryNetStore}. Every
 * method must be safe to call from many callers' threads at once.
 *
 * <p>Handles are stored in upper case. Times are real instants; boards show
 * them as 1985 dates ({@link InWorld}).
 */
public interface NetStore {

    /** An account. {@code password} is the encoded hash from {@link Passwords}, never the password. */
    record User(String handle, String password, int failures, Instant lockedUntil, boolean banned, Instant created) {
    }

    record Message(long id, String board, String area, String author, String subject, String body, Instant time,
            long replyTo) {
    }

    record Mail(long id, String from, String to, String subject, String body, Instant time, boolean read) {
    }

    /** QUEUED: uploaded, waiting for the sysop. PUBLIC: in the file list. REJECTED: refused. */
    enum FileStatus { QUEUED, PUBLIC, REJECTED }

    record FileEntry(long id, String board, String area, String name, Trimodem.Type type, String description,
            TriImage file, String uploader, FileStatus status, Instant time) {
    }

    record Report(long id, long messageId, String reporter, Instant time) {
    }

    record CallLog(String handle, Instant time) {
    }

    record OneLiner(String handle, String text, Instant time) {
    }

    // ------------------------------------------------------------ accounts

    /** The account called {@code handle}, or null. */
    User user(String handle);

    /** Adds an account; false if the handle is taken. */
    boolean addUser(User user);

    /** Saves changed failures, lock or ban. */
    void updateUser(User user);

    // ------------------------------------------------------------ messages

    /** Stores a message (its id is ignored) and returns the new id. */
    long post(Message message);

    /** Visible messages in an area. */
    int countMessages(String board, String area);

    /** Visible messages in an area, newest first. */
    List<Message> messages(String board, String area, int skip, int limit);

    /** A visible message, or null. */
    Message message(long id);

    /** Hides a message (the sysop deleted it); false if there was none. */
    boolean hideMessage(long id);

    void report(long messageId, String reporter, Instant time);

    /** All reports, newest first. */
    List<Report> reports();

    // ------------------------------------------------------------ mail

    long sendMail(Mail mail);

    /** Mail to {@code handle}, newest first. */
    List<Mail> mailFor(String handle);

    void markRead(long id);

    // ------------------------------------------------------------ files

    long addFile(FileEntry file);

    /** Public files of an area, by name. */
    List<FileEntry> files(String board, String area);

    /** Any file, whatever its status, or null. */
    FileEntry file(long id);

    /** Uploads waiting for the sysop, oldest first. */
    List<FileEntry> queue();

    boolean setFileStatus(long id, FileStatus status);

    /** Words of every file a caller uploaded that wasn't rejected: their share of the storage. */
    long wordsUploaded(String handle);

    // ------------------------------------------------------------ the board's life

    void logCall(String board, String handle, Instant time);

    /** The last callers of a board, newest first. */
    List<CallLog> lastCallers(String board, int limit);

    void addOneLiner(String board, String handle, String text, Instant time);

    /** A board's one-liners, newest first. */
    List<OneLiner> oneLiners(String board, int limit);

    /** Seconds {@code who} (a handle, or GUEST@address) spent on a board on {@code day}. */
    long secondsUsed(String board, String who, LocalDate day);

    void addSeconds(String board, String who, LocalDate day, long seconds);

    // ------------------------------------------------------------ addresses

    boolean ipBanned(String ip);

    void banIp(String ip, boolean banned);
}
