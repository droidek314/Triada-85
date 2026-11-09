package org.triada.net;

import java.util.List;
import java.util.Locale;

/**
 * The sysop's tools (NET-SPEC §5, M18): the upload queue, reported messages,
 * deleting posts, banning handles and addresses. The admin web page and the
 * command line both use this, so they do exactly the same things, and every
 * action is written to the log with who did it.
 */
public final class Moderation {

    private final NetStore store;
    private final NetLog log;
    private final String by;

    /** {@code by} names the tool in the log, e.g. "admin page" or "command line". */
    public Moderation(NetStore store, NetLog log, String by) {
        this.store = store;
        this.log = log;
        this.by = by;
    }

    /** Uploads waiting for a decision. */
    public List<NetStore.FileEntry> queue() {
        return store.queue();
    }

    /** Makes a queued upload public. */
    public boolean approve(long fileId) {
        return decide(fileId, NetStore.FileStatus.PUBLIC, "approve upload");
    }

    /** Refuses a queued upload; it no longer counts against the uploader's space. */
    public boolean reject(long fileId) {
        return decide(fileId, NetStore.FileStatus.REJECTED, "reject upload");
    }

    private boolean decide(long id, NetStore.FileStatus status, String action) {
        NetStore.FileEntry f = store.file(id);
        if (f == null || f.status() != NetStore.FileStatus.QUEUED) {
            return false;
        }
        store.setFileStatus(id, status);
        log.event("moderation", "action", action, "file", id, "name", f.name(), "by", by);
        return true;
    }

    /** A report and the message it is about (null if the message is gone). */
    public record ReportedMessage(NetStore.Report report, NetStore.Message message) {
    }

    /** Reported messages, newest report first. */
    public List<ReportedMessage> reports() {
        return store.reports().stream().map(r -> new ReportedMessage(r, store.message(r.messageId()))).toList();
    }

    /** A message, for reading before deciding. */
    public NetStore.Message message(long id) {
        return store.message(id);
    }

    /** Deletes (hides) a message. */
    public boolean deleteMessage(long id) {
        boolean done = store.hideMessage(id);
        if (done) {
            log.event("moderation", "action", "delete message", "message", id, "by", by);
        }
        return done;
    }

    /** Bans or unbans a handle; false if there is no such caller. */
    public boolean banHandle(String handle, boolean banned) {
        NetStore.User u = store.user(handle.toUpperCase(Locale.ROOT));
        if (u == null) {
            return false;
        }
        store.updateUser(new NetStore.User(u.handle(), u.password(), u.failures(), u.lockedUntil(), banned,
                u.created()));
        log.event("moderation", "action", banned ? "ban handle" : "unban handle", "handle", u.handle(), "by", by);
        return true;
    }

    /** Bans or unbans a network address: its calls hear NO CARRIER. */
    public void banIp(String ip, boolean banned) {
        store.banIp(ip, banned);
        log.event("moderation", "action", banned ? "ban address" : "unban address", "ip", ip, "by", by);
    }
}
