package org.triada.net;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TRIADA-NET itself (NET-SPEC §4, M18): the {@link Exchange} that knows every
 * board. Dialing a board's number answers with a {@link BoardSession}, unless
 * all its lines are busy; {@code 0-00-00} answers with the directory; a banned
 * address hears NO CARRIER. It also knows who is online, keeps the shared
 * rate limits, and holds the store, the limits, the clock and the log that
 * every session uses.
 */
public final class Network implements Exchange {

    public static final String INFO_NUMBER = "0-00-00";

    private final Map<String, BoardDef> boards = new LinkedHashMap<>();
    private final NetStore store;
    private final Limits limits;
    private final Clock clock;
    private final NetLog log;
    private final Passwords passwords;
    private final RateLimiter limiter = new RateLimiter();
    private final Set<BoardSession> online = ConcurrentHashMap.newKeySet();

    /**
     * @throws IllegalArgumentException if two boards share a number, or one uses 0-00-00
     */
    public Network(List<BoardDef> boards, NetStore store, Limits limits, Clock clock, NetLog log) {
        List<BoardDef> sorted = new ArrayList<>(boards);
        sorted.sort(Comparator.comparing(BoardDef::number));
        for (BoardDef b : sorted) {
            if (b.number().equals(INFO_NUMBER) || this.boards.putIfAbsent(b.number(), b) != null) {
                throw new IllegalArgumentException("the number " + b.number() + " is taken");
            }
        }
        this.store = store;
        this.limits = limits;
        this.clock = clock;
        this.log = log;
        this.passwords = new Passwords(limits.passwordIterations());
    }

    @Override
    public synchronized Answer dial(String number, Caller caller) {
        if (store.ipBanned(caller.address())) {
            log("refused", "ip", caller.address(), "reason", "banned address");
            return Answer.noCarrier();
        }
        if (number.equals(INFO_NUMBER)) {
            return Answer.connect(new InfoCall(this, caller));
        }
        BoardDef board = boards.get(number);
        if (board == null) {
            return Answer.noNumber();
        }
        if (linesInUse(board) >= board.lines()) {
            return Answer.busy();
        }
        BoardSession s = new BoardSession(this, board, caller);
        online.add(s);              // the line is taken from the moment it is answered
        log("call", "board", board.number(), "ip", caller.address());
        return Answer.connect(s);
    }

    private int linesInUse(BoardDef board) {
        int n = 0;
        for (BoardSession s : online) {
            n += s.board() == board ? 1 : 0;
        }
        return n;
    }

    void leave(BoardSession s) {
        online.remove(s);
    }

    /** Someone on a board now: their handle (GUEST, or "" while still logging in) and the board's name. */
    public record Online(String handle, String board) {
    }

    /** Everyone on any board now. */
    public List<Online> online() {
        List<Online> out = new ArrayList<>();
        for (BoardSession s : online) {
            out.add(new Online(s.handleShown(), s.board().name()));
        }
        out.sort(Comparator.comparing(Online::board).thenComparing(Online::handle));
        return out;
    }

    /** The boards in the 0-00-00 directory (not the unlisted ones), by number. */
    public List<BoardDef> directory() {
        return boards.values().stream().filter(b -> !b.unlisted()).toList();
    }

    public List<BoardDef> boards() {
        return List.copyOf(boards.values());
    }

    public NetStore store() {
        return store;
    }

    public Limits limits() {
        return limits;
    }

    Passwords passwords() {
        return passwords;
    }

    RateLimiter limiter() {
        return limiter;
    }

    public Instant now() {
        return clock.instant();
    }

    /** The real day (UTC), for daily time limits. */
    LocalDate today() {
        return LocalDate.ofInstant(now(), ZoneOffset.UTC);
    }

    /** A moment as an in-world date, e.g. 1985-11-09. */
    String inWorldDate(Instant t) {
        return InWorld.date(LocalDate.ofInstant(t, ZoneOffset.UTC));
    }

    void log(String event, Object... fields) {
        log.event(event, fields);
    }
}
