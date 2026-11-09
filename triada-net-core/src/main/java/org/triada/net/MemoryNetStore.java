package org.triada.net;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link NetStore} in memory (M18): for tests and an offline node. Every
 * method is synchronized, which is plenty for a handful of callers.
 */
public final class MemoryNetStore implements NetStore {

    private final Map<String, User> users = new HashMap<>();
    private final List<Message> messages = new ArrayList<>();
    private final Set<Long> hidden = new HashSet<>();
    private final List<Report> reports = new ArrayList<>();
    private final List<Mail> mail = new ArrayList<>();
    private final List<FileEntry> files = new ArrayList<>();
    private final Map<String, List<CallLog>> calls = new HashMap<>();
    private final Map<String, List<OneLiner>> oneLiners = new HashMap<>();
    private final Map<String, Long> seconds = new HashMap<>();
    private final Set<String> bannedIps = new HashSet<>();
    private long nextId = 1;

    @Override
    public synchronized User user(String handle) {
        return users.get(handle);
    }

    @Override
    public synchronized boolean addUser(User user) {
        return users.putIfAbsent(user.handle(), user) == null;
    }

    @Override
    public synchronized void updateUser(User user) {
        users.replace(user.handle(), user);
    }

    @Override
    public synchronized long post(Message m) {
        long id = nextId++;
        messages.add(new Message(id, m.board(), m.area(), m.author(), m.subject(), m.body(), m.time(), m.replyTo()));
        return id;
    }

    private List<Message> visible(String board, String area) {
        List<Message> out = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.board().equals(board) && m.area().equals(area) && !hidden.contains(m.id())) {
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public synchronized int countMessages(String board, String area) {
        return visible(board, area).size();
    }

    @Override
    public synchronized List<Message> messages(String board, String area, int skip, int limit) {
        List<Message> all = visible(board, area);
        return List.copyOf(all.subList(Math.min(skip, all.size()), Math.min(skip + limit, all.size())));
    }

    @Override
    public synchronized Message message(long id) {
        for (Message m : messages) {
            if (m.id() == id && !hidden.contains(id)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public synchronized boolean hideMessage(long id) {
        return message(id) != null && hidden.add(id);
    }

    @Override
    public synchronized void report(long messageId, String reporter, Instant time) {
        reports.add(new Report(nextId++, messageId, reporter, time));
    }

    @Override
    public synchronized List<Report> reports() {
        List<Report> r = new ArrayList<>(reports);
        r.sort(Comparator.comparing(Report::id).reversed());
        return r;
    }

    @Override
    public synchronized long sendMail(Mail m) {
        long id = nextId++;
        mail.add(new Mail(id, m.from(), m.to(), m.subject(), m.body(), m.time(), false));
        return id;
    }

    @Override
    public synchronized List<Mail> mailFor(String handle) {
        List<Mail> out = new ArrayList<>();
        for (int i = mail.size() - 1; i >= 0; i--) {
            if (mail.get(i).to().equals(handle)) {
                out.add(mail.get(i));
            }
        }
        return out;
    }

    @Override
    public synchronized void markRead(long id) {
        for (int i = 0; i < mail.size(); i++) {
            Mail m = mail.get(i);
            if (m.id() == id) {
                mail.set(i, new Mail(m.id(), m.from(), m.to(), m.subject(), m.body(), m.time(), true));
            }
        }
    }

    @Override
    public synchronized long addFile(FileEntry f) {
        long id = nextId++;
        files.add(new FileEntry(id, f.board(), f.area(), f.name(), f.type(), f.description(), f.file(), f.uploader(),
                f.status(), f.time()));
        return id;
    }

    @Override
    public synchronized List<FileEntry> files(String board, String area) {
        List<FileEntry> out = new ArrayList<>();
        for (FileEntry f : files) {
            if (f.board().equals(board) && f.area().equals(area) && f.status() == FileStatus.PUBLIC) {
                out.add(f);
            }
        }
        out.sort(Comparator.comparing(FileEntry::name));
        return out;
    }

    @Override
    public synchronized FileEntry file(long id) {
        return files.stream().filter(f -> f.id() == id).findFirst().orElse(null);
    }

    @Override
    public synchronized List<FileEntry> queue() {
        return files.stream().filter(f -> f.status() == FileStatus.QUEUED).toList();
    }

    @Override
    public synchronized boolean setFileStatus(long id, FileStatus status) {
        for (int i = 0; i < files.size(); i++) {
            FileEntry f = files.get(i);
            if (f.id() == id) {
                files.set(i, new FileEntry(f.id(), f.board(), f.area(), f.name(), f.type(), f.description(), f.file(),
                        f.uploader(), status, f.time()));
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized long wordsUploaded(String handle) {
        return files.stream().filter(f -> handle.equals(f.uploader()) && f.status() != FileStatus.REJECTED)
                .mapToLong(f -> f.file().size()).sum();
    }

    @Override
    public synchronized void logCall(String board, String handle, Instant time) {
        calls.computeIfAbsent(board, b -> new ArrayList<>()).add(new CallLog(handle, time));
    }

    @Override
    public synchronized List<CallLog> lastCallers(String board, int limit) {
        return newestFirst(calls.getOrDefault(board, List.of()), limit);
    }

    @Override
    public synchronized void addOneLiner(String board, String handle, String text, Instant time) {
        oneLiners.computeIfAbsent(board, b -> new ArrayList<>()).add(new OneLiner(handle, text, time));
    }

    @Override
    public synchronized List<OneLiner> oneLiners(String board, int limit) {
        return newestFirst(oneLiners.getOrDefault(board, List.of()), limit);
    }

    private static <T> List<T> newestFirst(List<T> list, int limit) {
        List<T> out = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0 && out.size() < limit; i--) {
            out.add(list.get(i));
        }
        return out;
    }

    @Override
    public synchronized long secondsUsed(String board, String who, LocalDate day) {
        return seconds.getOrDefault(board + "|" + who + "|" + day, 0L);
    }

    @Override
    public synchronized void addSeconds(String board, String who, LocalDate day, long s) {
        seconds.merge(board + "|" + who + "|" + day, s, Long::sum);
    }

    @Override
    public synchronized boolean ipBanned(String ip) {
        return bannedIps.contains(ip);
    }

    @Override
    public synchronized void banIp(String ip, boolean banned) {
        if (banned) {
            bannedIps.add(ip);
        } else {
            bannedIps.remove(ip);
        }
    }
}
