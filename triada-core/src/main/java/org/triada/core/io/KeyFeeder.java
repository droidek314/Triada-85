package org.triada.core.io;

/**
 * Paste-typing (M15): types a text into the keyboard queue as if someone
 * typed it very fast. Each call to {@link #feed} fills the queue as far as it
 * has room (27 keys); the machine reads them during its frame, and the next
 * call goes on. The desktop (Ctrl+V), the web page (paste) and
 * {@code triada run --type FILE} all use it.
 *
 * <p>Line ends become Enter (10); a "\r\n" counts once. Tab is 9; printable
 * ASCII is itself; any other character (one the font doesn't have) is left out.
 */
public final class KeyFeeder {

    private final String text;
    private int next;

    public KeyFeeder(String text) {
        this.text = text;
    }

    /** Pushes as many keys as the queue takes; true while some are still to come. */
    public boolean feed(InputDevice input) {
        while (next < text.length() && input.queuedKeys() < InputDevice.KEY_QUEUE_CAPACITY) {
            int code = keyCode(text.charAt(next++));
            if (code != 0) {
                input.pushKey(code);
            }
        }
        return next < text.length();
    }

    /** True once every character has been typed. */
    public boolean done() {
        return next >= text.length();
    }

    /** The TRIADA key code of a pasted character, or 0 to leave it out. */
    static int keyCode(char c) {
        if (c >= 32 && c <= 126) {
            return c;
        }
        if (c == '\n') {
            return 10;
        }
        return c == '\t' ? 9 : 0;   // '\r' (of "\r\n") and anything else: left out
    }
}
