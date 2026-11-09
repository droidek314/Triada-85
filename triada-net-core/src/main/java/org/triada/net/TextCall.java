package org.triada.net;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * What every TRIADA-NET screen session shares (M18): typing a line, picking
 * a menu key, paging long lists with "MORE? (Y/N)", and hanging up.
 *
 * <p>A session never waits. Each character the caller types goes to the
 * current {@link Input}; asking a question just installs a new one, with the
 * code to run when the answer is complete. So menus are written as a chain
 * of "ask this, then do that", and one server thread could serve any number
 * of callers.
 *
 * <p>It also guards the line: a caller who sends too fast is cut off
 * (flooding), and one who sends nothing for too long is hung up (idle).
 */
abstract class TextCall implements Call {

    /** What the next typed character is for. */
    interface Input {
        void key(int c);
    }

    /** A TRIMODEM transfer in progress: it gets every byte, and the clock's ticks. */
    interface Transfer {
        void receive(int c);

        void tick();
    }

    protected final Network net;
    protected final Caller caller;
    protected final TermOut out = new TermOut();
    protected Transfer transfer;
    private Wire line;
    private Input input = c -> { };
    private int lastKey;
    private boolean ended;
    private Instant lastInput;
    private Instant floodSecond = Instant.EPOCH;
    private int floodCount;

    TextCall(Network net, Caller caller) {
        this.net = net;
        this.caller = caller;
    }

    /** The screen's width and the lines of text per page. */
    protected int width() {
        return caller.columns();
    }

    protected int pageLines() {
        return Math.max(1, caller.rows() - 2);
    }

    @Override
    public final void start(Wire wire) {
        line = wire;
        lastInput = net.now();
        begin();
        flush();
    }

    /** The call was answered: show the first screen. */
    protected abstract void begin();

    @Override
    public final void receive(int c) {
        if (ended) {
            return;
        }
        Instant now = net.now();
        lastInput = now;
        if (transfer != null) {
            transfer.receive(c);
            flush();
            return;
        }
        if (flooding(now)) {
            net.log("flooding", "ip", caller.address());
            bye("FLOODING. GOODBYE.");
            return;
        }
        boolean lineFeedAfterReturn = c == 10 && lastKey == 13;
        lastKey = c;
        if (!lineFeedAfterReturn) {
            input.key(c);
        }
        flush();
    }

    /** True if more characters than the limit arrived within the current second. */
    private boolean flooding(Instant now) {
        if (Duration.between(floodSecond, now).compareTo(Duration.ofSeconds(1)) >= 0) {
            floodSecond = now;
            floodCount = 0;
        }
        return ++floodCount > net.limits().floodPerSecond();
    }

    @Override
    public final void tick() {
        if (ended) {
            return;
        }
        if (transfer != null) {
            transfer.tick();
        } else if (Duration.between(lastInput, net.now()).compareTo(net.limits().idle()) > 0) {
            bye("IDLE TOO LONG. GOODBYE.");
            return;
        } else {
            timeTick();
        }
        flush();
    }

    /** Once a second while no transfer runs: for time limits. */
    protected void timeTick() {
    }

    @Override
    public final void hungUp() {
        if (!ended) {
            ended = true;
            ended();
        }
    }

    /** The call is over, whoever ended it: bookkeeping. */
    protected void ended() {
    }

    /** Says {@code text} and hangs up. */
    protected void bye(String text) {
        out.freshLine().line(text);
        flush();
        if (!ended) {
            ended = true;
            ended();
            line.close();
        }
    }

    protected boolean isEnded() {
        return ended;
    }

    /** Sends what was written so far. */
    protected void flush() {
        if (!out.isEmpty() && line != null) {
            line.send(out.take());
        }
    }

    // ---------------------------------------------------------------- questions

    /**
     * Shows {@code prompt} and reads a line of at most {@code max} characters,
     * echoing each (or '*' if {@code masked}). Backspace works; Return ends it.
     */
    protected void ask(String prompt, int max, boolean masked, Consumer<String> then) {
        out.text(prompt);
        StringBuilder typed = new StringBuilder();
        input = c -> {
            if (c == 13 || c == 10) {
                out.newline();
                input = k -> { };
                then.accept(typed.toString());
            } else if (c == 8 || c == 127) {
                if (typed.length() > 0) {
                    typed.setLength(typed.length() - 1);
                    out.backspace();
                }
            } else if (c >= 32 && c <= 126) {
                if (typed.length() < max) {
                    typed.append((char) c);
                    out.text(masked ? "*" : String.valueOf((char) c));
                } else {
                    out.bell();             // the line is full
                }
            }
        };
    }

    /** Waits for one of {@code keys} (upper case; small letters count too); other keys ring the bell. */
    protected void choose(String keys, IntConsumer then) {
        input = c -> {
            int k = Character.toUpperCase(c);
            if (k < 128 && keys.indexOf(k) >= 0) {
                out.line(String.valueOf((char) k));
                input = x -> { };
                then.accept(k);
            } else if (c != 13 && c != 10) {
                out.bell();
            }
        };
    }

    /** Waits for any key. */
    protected void anyKey(Runnable then) {
        input = c -> {
            input = x -> { };
            then.run();
        };
    }

    /** Shows {@code lines} a page at a time, asking MORE? (Y/N) between pages, then runs {@code then}. */
    protected void page(List<String> lines, Runnable then) {
        pageFrom(lines, 0, then);
    }

    private void pageFrom(List<String> lines, int from, Runnable then) {
        int to = Math.min(lines.size(), from + pageLines());
        for (int i = from; i < to; i++) {
            out.line(Text.fit(lines.get(i), width()));
        }
        if (to >= lines.size()) {
            then.run();
            return;
        }
        out.text("MORE? (Y/N) ");
        choose("YN", k -> {
            if (k == 'Y') {
                pageFrom(lines, to, then);
            } else {
                then.run();
            }
        });
    }
}
