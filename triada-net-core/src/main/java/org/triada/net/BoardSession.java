package org.triada.net;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.triada.core.TriImage;
import org.triada.core.triskette.TrisketteNames;

/**
 * One caller on one board (NET-SPEC §4, §5; M18): the welcome screen, the
 * login, the main menu and everything behind it.
 *
 * <pre>
 *   welcome ─▶ HANDLE? ─┬─ GUEST ──────────────────────────┐
 *                       ├─ NEW ─▶ SYSOP RULES ─▶ handle,    │
 *                       │         password twice ───────────┤
 *                       └─ a handle ─▶ PASSWORD ────────────┴─▶ main menu
 *
 *   main menu:  M messages   E mail   F files   O one-liners
 *               L last callers   W who is on   T time   G goodbye
 * </pre>
 *
 * Guests may read and download; writing, mail and uploads need an account.
 * Every screen fits 27 columns (the caller's width, in fact) and pages long
 * lists. Rate limits, the daily time limit and the size limits come from
 * {@link Limits}.
 */
final class BoardSession extends TextCall {

    private static final Pattern HANDLE = Pattern.compile("[A-Z0-9]{2,12}");
    private static final List<String> RESERVED = List.of("GUEST", "NEW", "SYSOP", "ALL");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final Duration UPLOAD_SILENCE = Duration.ofSeconds(60);

    private record Draft(String subject, String body) {
    }

    private final BoardDef board;
    private final NetStore store;
    private final Limits limits;
    /** Null until logged in, then the handle; GUEST for a guest. */
    private String handle;
    private boolean guest;
    private int wrongPasswords;
    private Instant loginTime;
    private LocalDate loginDay;
    private long secondsBefore;

    BoardSession(Network net, BoardDef board, Caller caller) {
        super(net, caller);
        this.board = board;
        this.store = net.store();
        this.limits = net.limits();
    }

    BoardDef board() {
        return board;
    }

    /** The handle for "who is on": GUEST, the handle, or "" while logging in. */
    String handleShown() {
        return handle == null ? "" : handle;
    }

    // ================================================================ welcome and login

    @Override
    protected void begin() {
        out.clear().color(board.foreground(), board.background());
        for (String w : board.welcome()) {
            out.line(w);
        }
        out.line(board.name());
        out.line("SYSOP: " + board.sysop());
        out.line(net.inWorldDate(net.now()));
        askHandle();
    }

    private void askHandle() {
        ask("HANDLE (GUEST, NEW): ", 12, false, h -> {
            String name = h.strip().toUpperCase(Locale.ROOT);
            switch (name) {
                case "" -> askHandle();
                case "GUEST" -> {
                    guest = true;
                    loggedIn("GUEST");
                }
                case "NEW" -> rules();
                default -> askPassword(name);
            }
        });
    }

    private void askPassword(String name) {
        ask("PASSWORD: ", 24, true, password -> {
            Instant now = net.now();
            NetStore.User user = store.user(name);
            if (user != null && user.banned()) {
                net.log("refused", "handle", name, "ip", caller.address(), "reason", "banned handle");
                bye("THIS HANDLE IS BANNED.");
                return;
            }
            if (user != null && user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
                bye("LOCKED. CALL AGAIN LATER.");
                return;
            }
            boolean ok = user != null ? net.passwords().verify(password, user.password())
                    : net.passwords().verifyNobody(password);
            if (ok) {
                store.updateUser(new NetStore.User(user.handle(), user.password(), 0, null, false, user.created()));
                loggedIn(user.handle());
                return;
            }
            wrongPassword(user, name, now);
        });
    }

    private void wrongPassword(NetStore.User user, String name, Instant now) {
        if (user != null) {
            int failures = user.failures() + 1;
            Instant locked = null;
            if (failures >= limits.lockoutFailures()) {
                locked = now.plus(limits.lockout());
                failures = 0;
                net.log("locked", "handle", name, "ip", caller.address());
            }
            store.updateUser(new NetStore.User(user.handle(), user.password(), failures, locked, false,
                    user.created()));
        }
        net.limiter().record("loginfail:" + caller.address(), now);
        net.log("login failed", "handle", name, "ip", caller.address());
        wrongPasswords++;
        if (wrongPasswords >= limits.loginTries()
                || net.limiter().count("loginfail:" + caller.address(), limits.failureWindow(), now)
                        >= limits.failuresPerIp()) {
            bye("WRONG. GOODBYE.");
            return;
        }
        out.line("WRONG.");
        askHandle();
    }

    private void rules() {
        out.clear();
        out.line("SYSOP RULES");
        out.line("");
        out.line("1. BE KIND.");
        out.line("2. NO REAL NAMES, NO");
        out.line("   ADDRESSES, NO PHONE");
        out.line("   NUMBERS. YOU ARE YOUR");
        out.line("   HANDLE HERE.");
        out.line("3. NOTHING ILLEGAL.");
        out.line("");
        out.line("THE SYSOP MAY DELETE POSTS");
        out.line("AND BAN CALLERS.");
        out.line("");
        out.text("AGREE? (Y/N) ");
        choose("YN", k -> {
            if (k == 'N') {
                bye("GOODBYE.");
            } else {
                newHandle();
            }
        });
    }

    private void newHandle() {
        ask("NEW HANDLE: ", 12, false, h -> {
            String name = h.strip().toUpperCase(Locale.ROOT);
            if (!HANDLE.matcher(name).matches() || RESERVED.contains(name)) {
                out.line("2-12 LETTERS AND DIGITS.");
                newHandle();
            } else if (store.user(name) != null) {
                out.line("TAKEN.");
                newHandle();
            } else {
                newPassword(name);
            }
        });
    }

    private void newPassword(String name) {
        ask("PASSWORD (6-24): ", 24, true, p1 -> {
            if (p1.length() < 6) {
                out.line("TOO SHORT.");
                newPassword(name);
                return;
            }
            ask("AGAIN: ", 24, true, p2 -> {
                if (!p1.equals(p2)) {
                    out.line("NOT THE SAME.");
                    newPassword(name);
                    return;
                }
                Instant now = net.now();
                if (!net.limiter().allow("register:" + caller.address(), limits.newUsersPerIp(),
                        Duration.ofDays(1), now)) {
                    bye("NO MORE NEW CALLERS FROM YOUR LINE TODAY.");
                    return;
                }
                if (!store.addUser(new NetStore.User(name, net.passwords().hash(p1), 0, null, false, now))) {
                    out.line("TAKEN.");
                    newHandle();
                    return;
                }
                net.log("registered", "handle", name, "ip", caller.address());
                out.line("WELCOME, " + name + "!");
                loggedIn(name);
            });
        });
    }

    /** The time-limit key: the handle, or GUEST@address. */
    private String who() {
        return guest ? "GUEST@" + caller.address() : handle;
    }

    private void loggedIn(String name) {
        handle = name;
        loginTime = net.now();
        loginDay = net.today();
        secondsBefore = store.secondsUsed(board.number(), who(), loginDay);
        if (secondsLeft() <= 0) {
            loginTime = null;           // nothing to add to today's time
            bye("YOUR TIME IS UP FOR TODAY.");
            return;
        }
        store.logCall(board.number(), name, loginTime);
        net.log("login", "board", board.number(), "handle", name, "ip", caller.address());
        mainMenu();
    }

    private long secondsLeft() {
        long used = secondsBefore + (loginTime == null ? 0 : Duration.between(loginTime, net.now()).getSeconds());
        return limits.minutesPerDay() * 60L - used;
    }

    @Override
    protected void timeTick() {
        if (loginTime != null && secondsLeft() <= 0) {
            bye("YOUR TIME IS UP. GOODBYE.");
        }
    }

    @Override
    protected void ended() {
        net.leave(this);
        if (loginTime != null) {
            store.addSeconds(board.number(), who(), loginDay, Duration.between(loginTime, net.now()).getSeconds());
            net.log("hangup", "board", board.number(), "handle", handle);
        }
    }

    // ================================================================ the main menu

    private void mainMenu() {
        out.clear().color(board.foreground(), board.background());
        out.line(board.name());
        out.line(rule());
        out.line("M MESSAGES   E MAIL");
        out.line("F FILES      O ONE-LINERS");
        out.line("L LAST CALLS W WHO IS ON");
        out.line("T TIME LEFT  G GOODBYE");
        out.line(rule());
        out.text(Math.max(0, secondsLeft() / 60) + " MIN LEFT. COMMAND? ");
        choose("MEFOLWTG", k -> {
            switch (k) {
                case 'M' -> messages();
                case 'E' -> mail();
                case 'F' -> files();
                case 'O' -> oneLiners();
                case 'L' -> lastCallers();
                case 'W' -> whoIsOn();
                case 'T' -> {
                    out.line(Math.max(0, secondsLeft() / 60) + " MINUTES LEFT TODAY.");
                    pause(this::mainMenu);
                }
                default -> {
                    out.line("THANK YOU FOR CALLING");
                    bye(board.name() + ".");
                }
            }
        });
    }

    private String rule() {
        return "-".repeat(Math.min(25, width()));
    }

    /** "PRESS ANY KEY.", then {@code then}. */
    private void pause(Runnable then) {
        out.text("PRESS ANY KEY.");
        anyKey(then);
    }

    /** Picks one of {@code areas} (straight away if there is only one), then {@code then}. */
    private void pickArea(List<BoardDef.Area> areas, Consumer<BoardDef.Area> then) {
        if (areas.size() == 1) {
            then.accept(areas.get(0));
            return;
        }
        out.clear();
        StringBuilder keys = new StringBuilder("Q");
        for (int i = 0; i < areas.size() && i < 9; i++) {
            out.line((i + 1) + " " + areas.get(i).title());
            keys.append((char) ('1' + i));
        }
        out.line("Q BACK");
        out.text("AREA? ");
        choose(keys.toString(), k -> {
            if (k == 'Q') {
                mainMenu();
            } else {
                then.accept(areas.get(k - '1'));
            }
        });
    }

    private boolean needsAccount(Runnable back) {
        if (guest) {
            out.line("GUESTS CAN ONLY READ.");
            out.line("CALL AGAIN AS NEW.");
            pause(back);
            return true;
        }
        return false;
    }

    /** Messages, mail and one-liners share one limit per caller. */
    private boolean writingTooFast(Runnable back) {
        if (!net.limiter().allow("write:" + handle, limits.postsPer10Min(), Duration.ofMinutes(10), net.now())) {
            out.line("YOU WRITE TOO FAST.");
            out.line("TRY AGAIN IN A WHILE.");
            pause(back);
            return true;
        }
        return false;
    }

    // ================================================================ messages

    private void messages() {
        pickArea(board.messageAreas(), this::messageArea);
    }

    private void messageArea(BoardDef.Area area) {
        out.clear();
        out.line(area.title() + ": " + store.countMessages(board.number(), area.id()) + " MESSAGES");
        out.line("L LIST  R READ  P POST");
        out.line("Q BACK");
        out.text("COMMAND? ");
        choose("LRPQ", k -> {
            switch (k) {
                case 'L' -> {
                    List<String> lines = new ArrayList<>();
                    for (NetStore.Message m : store.messages(board.number(), area.id(), 0, 100)) {
                        lines.add("#" + m.id() + " " + m.subject());
                    }
                    if (lines.isEmpty()) {
                        lines.add("NO MESSAGES YET.");
                    }
                    page(lines, () -> pause(() -> messageArea(area)));
                }
                case 'R' -> ask("MESSAGE NUMBER: ", 6, false, n -> {
                    NetStore.Message m = store.message(number(n));
                    if (m == null || !m.board().equals(board.number()) || !m.area().equals(area.id())) {
                        out.line("NO SUCH MESSAGE.");
                        pause(() -> messageArea(area));
                    } else {
                        read(m, area);
                    }
                });
                case 'P' -> {
                    Runnable back = () -> messageArea(area);
                    if (!needsAccount(back) && !writingTooFast(back)) {
                        compose(null, d -> {
                            long id = store.post(new NetStore.Message(0, board.number(), area.id(), handle,
                                    d.subject(), d.body(), net.now(), 0));
                            net.log("post", "board", board.number(), "id", id, "handle", handle);
                            out.line("SAVED AS #" + id + ".");
                            pause(back);
                        }, back);
                    }
                }
                default -> mainMenu();
            }
        });
    }

    private static long number(String text) {
        String t = text.strip();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void read(NetStore.Message m, BoardDef.Area area) {
        out.clear();
        List<String> lines = new ArrayList<>();
        lines.add("#" + m.id() + " FROM " + m.author());
        lines.add(net.inWorldDate(m.time()) + " " + clock(m.time()));
        lines.add(m.subject());
        lines.add(rule());
        lines.addAll(Text.wrap(m.body(), width()));
        page(lines, () -> {
            out.text("N NEXT A ANSWER R REPORT Q ");
            choose("NARQ", k -> {
                switch (k) {
                    case 'N' -> {
                        NetStore.Message older = older(m, area);
                        if (older == null) {
                            out.line("NO OLDER MESSAGES.");
                            pause(() -> messageArea(area));
                        } else {
                            read(older, area);
                        }
                    }
                    case 'A' -> {
                        Runnable back = () -> messageArea(area);
                        if (!needsAccount(back) && !writingTooFast(back)) {
                            String re = Text.fit("RE: " + m.subject().replaceFirst("^RE: ", ""),
                                    limits.subjectLength());
                            compose(re, d -> {
                                long id = store.post(new NetStore.Message(0, board.number(), area.id(), handle,
                                        d.subject(), d.body(), net.now(), m.id()));
                                net.log("post", "board", board.number(), "id", id, "handle", handle);
                                out.line("SAVED AS #" + id + ".");
                                pause(back);
                            }, back);
                        }
                    }
                    case 'R' -> {
                        store.report(m.id(), handle, net.now());
                        net.log("report", "message", m.id(), "by", handle, "ip", caller.address());
                        out.line("THANK YOU. THE SYSOP");
                        out.line("WILL LOOK AT IT.");
                        pause(() -> messageArea(area));
                    }
                    default -> messageArea(area);
                }
            });
        });
    }

    private NetStore.Message older(NetStore.Message m, BoardDef.Area area) {
        for (NetStore.Message x : store.messages(board.number(), area.id(), 0, 1_000)) {
            if (x.id() < m.id()) {
                return x;               // newest first: the first older one is the next
            }
        }
        return null;
    }

    /** Asks for a subject (unless given) and the text, then hands over a draft; empty or aborted: {@code back}. */
    private void compose(String subject, Consumer<Draft> then, Runnable back) {
        if (subject == null) {
            ask("SUBJECT: ", limits.subjectLength(), false, s -> {
                String clean = Text.clean(s).strip();
                if (clean.isEmpty()) {
                    back.run();
                } else {
                    body(clean, then, back);
                }
            });
        } else {
            out.line("SUBJECT: " + subject);
            body(subject, then, back);
        }
    }

    private void body(String subject, Consumer<Draft> then, Runnable back) {
        out.line("TYPE YOUR TEXT. A LINE");
        out.line("WITH ONLY . ENDS IT.");
        bodyLine(subject, new ArrayList<>(), 0, then, back);
    }

    private void bodyLine(String subject, List<String> lines, int chars, Consumer<Draft> then, Runnable back) {
        ask("", limits.lineLength(), false, typed -> {
            String l = Text.clean(typed);
            if (l.equals(".")) {
                finishBody(subject, lines, then, back);
                return;
            }
            int room = limits.messageChars() - chars;
            if (l.length() >= room || lines.size() + 1 >= limits.messageLines()) {
                lines.add(l.substring(0, Math.min(l.length(), Math.max(0, room))));
                out.line("THAT IS THE LIMIT.");
                finishBody(subject, lines, then, back);
                return;
            }
            lines.add(l);
            bodyLine(subject, lines, chars + l.length() + 1, then, back);
        });
    }

    private void finishBody(String subject, List<String> lines, Consumer<Draft> then, Runnable back) {
        if (lines.stream().allMatch(String::isBlank)) {
            back.run();
            return;
        }
        out.text("S SAVE  A ABORT ");
        choose("SA", k -> {
            if (k == 'S') {
                then.accept(new Draft(subject, String.join("\n", lines)));
            } else {
                back.run();
            }
        });
    }

    // ================================================================ mail

    private void mail() {
        if (needsAccount(this::mainMenu)) {
            return;
        }
        List<NetStore.Mail> inbox = store.mailFor(handle);
        out.clear();
        out.line("MAIL FOR " + handle);
        int count = 0;
        for (NetStore.Mail m : inbox) {
            if (count++ >= pageLines() - 4) {
                break;
            }
            out.line(Text.fit((m.read() ? " #" : "*#") + m.id() + " " + m.from() + ": " + m.subject(), width()));
        }
        if (inbox.isEmpty()) {
            out.line("NO MAIL.");
        }
        out.line("R READ  S SEND  Q BACK");
        out.text("COMMAND? ");
        choose("RSQ", k -> {
            switch (k) {
                case 'R' -> ask("MAIL NUMBER: ", 6, false, n -> {
                    long id = number(n);
                    NetStore.Mail m = inbox.stream().filter(x -> x.id() == id).findFirst().orElse(null);
                    if (m == null) {
                        out.line("NO SUCH MAIL.");
                        pause(this::mail);
                        return;
                    }
                    store.markRead(m.id());
                    out.clear();
                    List<String> lines = new ArrayList<>();
                    lines.add("FROM " + m.from() + ", " + net.inWorldDate(m.time()));
                    lines.add(m.subject());
                    lines.add(rule());
                    lines.addAll(Text.wrap(m.body(), width()));
                    page(lines, () -> pause(this::mail));
                });
                case 'S' -> {
                    if (writingTooFast(this::mail)) {
                        return;
                    }
                    ask("TO: ", 12, false, to -> {
                        String name = to.strip().toUpperCase(Locale.ROOT);
                        if (store.user(name) == null) {
                            out.line("NO SUCH CALLER.");
                            pause(this::mail);
                            return;
                        }
                        compose(null, d -> {
                            store.sendMail(new NetStore.Mail(0, handle, name, d.subject(), d.body(), net.now(),
                                    false));
                            net.log("mail", "from", handle, "to", name);
                            out.line("SENT.");
                            pause(this::mail);
                        }, this::mail);
                    });
                }
                default -> mainMenu();
            }
        });
    }

    // ================================================================ files

    private void files() {
        if (board.fileAreas().isEmpty()) {
            out.line("NO FILES ON THIS BOARD.");
            pause(this::mainMenu);
            return;
        }
        pickArea(board.fileAreas(), this::fileArea);
    }

    private void fileArea(BoardDef.Area area) {
        out.clear();
        out.line(area.title());
        List<String> lines = new ArrayList<>();
        for (NetStore.FileEntry f : store.files(board.number(), area.id())) {
            lines.add(String.format(Locale.ROOT, "%-12s %6dW", f.name(), f.file().size()));
            lines.add("  " + f.description());
        }
        if (lines.isEmpty()) {
            lines.add("NO FILES YET.");
        }
        page(lines, () -> {
            out.line("D DOWNLOAD  I INFO");
            out.line("U UPLOAD    Q BACK");
            out.text("COMMAND? ");
            choose("DIUQ", k -> {
                switch (k) {
                    case 'D', 'I' -> ask("FILE NAME: ", 12, false, n -> {
                        NetStore.FileEntry f = find(area, n);
                        if (f == null) {
                            out.line("NO SUCH FILE.");
                            pause(() -> fileArea(area));
                        } else if (k == 'I') {
                            info(f, area);
                        } else {
                            download(f, area);
                        }
                    });
                    case 'U' -> upload(area);
                    default -> mainMenu();
                }
            });
        });
    }

    /** A public file of the area, by a name the caller typed; null if there is none (or the name is no name). */
    private NetStore.FileEntry find(BoardDef.Area area, String typed) {
        String name = TrisketteNames.normalize(typed.strip());
        if (name == null) {
            return null;
        }
        for (NetStore.FileEntry f : store.files(board.number(), area.id())) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private void info(NetStore.FileEntry f, BoardDef.Area area) {
        out.clear();
        List<String> lines = new ArrayList<>();
        lines.add(f.name() + ", " + f.file().size() + " WORDS");
        lines.add("FROM " + f.uploader() + ", " + net.inWorldDate(f.time()));
        lines.add(rule());
        lines.addAll(Text.wrap(f.description(), width()));
        page(lines, () -> pause(() -> fileArea(area)));
    }

    private void download(NetStore.FileEntry f, BoardDef.Area area) {
        out.line("SENDING " + f.name() + " BY");
        out.line("TRIMODEM.");
        TrimodemSender sender = new TrimodemSender(f.name(), f.type(), f.file());
        out.raw(sender.start());
        net.log("download", "board", board.number(), "file", f.name(), "handle", handle);
        Instant[] lastSent = {net.now()};
        transfer = new Transfer() {
            @Override
            public void receive(int c) {
                byte[] next = sender.receive(c);
                if (next.length > 0) {
                    out.raw(next);
                    lastSent[0] = net.now();
                }
                check();
            }

            @Override
            public void tick() {
                if (Duration.between(lastSent[0], net.now()).compareTo(limits.transferTimeout()) > 0) {
                    out.raw(sender.timeout());
                    lastSent[0] = net.now();
                    check();
                }
            }

            private void check() {
                if (sender.done() || sender.failed()) {
                    transfer = null;
                    out.newline().line(sender.done() ? "DONE." : "TRANSFER FAILED.");
                    pause(() -> fileArea(area));
                }
            }
        };
    }

    private void upload(BoardDef.Area area) {
        Runnable back = () -> fileArea(area);
        if (needsAccount(back)) {
            return;
        }
        if (store.wordsUploaded(handle) >= limits.storageWords()) {
            out.line("YOUR SPACE IS FULL.");
            pause(back);
            return;
        }
        if (!net.limiter().allow("upload:" + handle, limits.uploadsPerHour(), Duration.ofHours(1), net.now())) {
            out.line("TOO MANY UPLOADS. TRY");
            out.line("AGAIN IN AN HOUR.");
            pause(back);
            return;
        }
        out.line("SEND YOUR FILE BY");
        out.line("TRIMODEM NOW.");
        TrimodemReceiver receiver = new TrimodemReceiver();
        Instant[] lastHeard = {net.now()};
        transfer = new Transfer() {
            @Override
            public void receive(int c) {
                lastHeard[0] = net.now();
                out.raw(receiver.receive(c));
                if (receiver.done()) {
                    transfer = null;
                    received(area, receiver.name(), receiver.type(), receiver.file());
                } else if (receiver.failed()) {
                    transfer = null;
                    out.newline().line("TRANSFER FAILED.");
                    pause(back);
                }
            }

            @Override
            public void tick() {
                if (Duration.between(lastHeard[0], net.now()).compareTo(UPLOAD_SILENCE) > 0) {
                    transfer = null;
                    out.newline().line("NOTHING ARRIVED.");
                    pause(back);
                }
            }
        };
    }

    private void received(BoardDef.Area area, String name, Trimodem.Type type, TriImage file) {
        Runnable back = () -> fileArea(area);
        out.newline();
        if (file.size() > limits.fileWords()) {
            out.line("TOO BIG: AT MOST " + limits.fileWords());
            out.line("WORDS.");
            pause(back);
            return;
        }
        if (store.wordsUploaded(handle) + file.size() > limits.storageWords()) {
            out.line("NOT ENOUGH SPACE LEFT.");
            pause(back);
            return;
        }
        ask("DESCRIPTION: ", 40, false, d -> {
            String description = Text.clean(d).strip();
            long id = store.addFile(new NetStore.FileEntry(0, board.number(), area.id(), name, type,
                    description.isEmpty() ? "NO DESCRIPTION." : description, file, handle,
                    NetStore.FileStatus.QUEUED, net.now()));
            net.log("upload queued", "board", board.number(), "id", id, "file", name, "handle", handle);
            out.line("THANK YOU. THE SYSOP WILL");
            out.line("CHECK IT FIRST.");
            pause(back);
        });
    }

    // ================================================================ the rest

    private void oneLiners() {
        out.clear();
        out.line("ONE-LINERS");
        List<String> lines = new ArrayList<>();
        for (NetStore.OneLiner o : store.oneLiners(board.number(), 10)) {
            lines.addAll(Text.wrap(o.handle() + ": " + o.text(), width()));
        }
        if (lines.isEmpty()) {
            lines.add("THE WALL IS EMPTY.");
        }
        page(lines, () -> {
            out.text("A ADD  Q BACK ");
            choose("AQ", k -> {
                if (k == 'Q' || needsAccount(this::mainMenu) || writingTooFast(this::mainMenu)) {
                    if (k == 'Q') {
                        mainMenu();
                    }
                    return;
                }
                ask("YOUR LINE: ", limits.oneLinerLength(), false, t -> {
                    String text = Text.clean(t).strip();
                    if (!text.isEmpty()) {
                        store.addOneLiner(board.number(), handle, text, net.now());
                        net.log("one-liner", "board", board.number(), "handle", handle);
                    }
                    oneLiners();
                });
            });
        });
    }

    private void lastCallers() {
        out.clear();
        out.line("LAST CALLERS");
        List<String> lines = new ArrayList<>();
        for (NetStore.CallLog c : store.lastCallers(board.number(), 10)) {
            lines.add(String.format(Locale.ROOT, "%-12s %s %s", c.handle(), net.inWorldDate(c.time()).substring(5),
                    clock(c.time())));
        }
        page(lines, () -> pause(this::mainMenu));
    }

    private void whoIsOn() {
        out.clear();
        out.line("WHO IS ON TRIADA-NET");
        List<String> lines = new ArrayList<>();
        for (Network.Online o : net.online()) {
            lines.add(String.format(Locale.ROOT, "%-12s %s", o.handle().isEmpty() ? "..." : o.handle(), o.board()));
        }
        page(lines, () -> pause(this::mainMenu));
    }

    private static String clock(Instant t) {
        return CLOCK.format(t.atOffset(ZoneOffset.UTC));
    }
}
