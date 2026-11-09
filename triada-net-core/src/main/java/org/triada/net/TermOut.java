package org.triada.net;

import java.nio.charset.StandardCharsets;

/**
 * Builds what a board sends to the caller's terminal (SPEC §11.1, M18):
 * text plus the terminal's control codes. Characters are 0 … 255, one byte each.
 *
 * <pre>
 *   12            clear the screen (cursor to the top left)
 *   13            new line
 *    8            backspace
 *    7            bell
 *   ESC 'C' f b   text colour: foreground f, background b (pixel values −13 … 13, each + 32)
 *   ESC 'L' c r   cursor to column c, row r (each + 32)
 * </pre>
 */
public final class TermOut {

    public static final int BELL = 7;
    public static final int BACKSPACE = 8;
    public static final int CLEAR = 12;
    public static final int NEWLINE = 13;
    public static final int ESC = 27;
    /** Added to the parameters of ESC 'C' and ESC 'L', so they are never control codes. */
    public static final int OFFSET = 32;

    private final StringBuilder b = new StringBuilder();
    /** The last character written, kept across {@link #take()}: is the cursor at the start of a line? */
    private char last = CLEAR;

    public TermOut clear() {
        b.append((char) CLEAR);
        return this;
    }

    /** Text, then a new line. */
    public TermOut line(String text) {
        return text(text).newline();
    }

    public TermOut newline() {
        b.append((char) NEWLINE);
        return this;
    }

    /** Text as it is; only characters 32 … 126 (anything else becomes '?'). */
    public TermOut text(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            b.append(c >= 32 && c <= 126 ? c : '?');
        }
        return this;
    }

    public TermOut bell() {
        b.append((char) BELL);
        return this;
    }

    public TermOut backspace() {
        b.append((char) BACKSPACE);
        return this;
    }

    /** Text colour: foreground and background are pixel values −13 … 13 (SPEC §4.2). */
    public TermOut color(int foreground, int background) {
        b.append((char) ESC).append('C').append((char) (foreground + OFFSET)).append((char) (background + OFFSET));
        return this;
    }

    /** Cursor to column {@code col}, row {@code row} (from 0). */
    public TermOut at(int col, int row) {
        b.append((char) ESC).append('L').append((char) (col + OFFSET)).append((char) (row + OFFSET));
        return this;
    }

    /** Raw characters, e.g. a TRIMODEM message. */
    public TermOut raw(byte[] bytes) {
        for (byte x : bytes) {
            b.append((char) (x & 0xFF));
        }
        return this;
    }

    /** A new line, unless the cursor is already at the start of one. */
    public TermOut freshLine() {
        char end = b.length() > 0 ? b.charAt(b.length() - 1) : last;
        if (end != NEWLINE && end != CLEAR) {
            newline();
        }
        return this;
    }

    public boolean isEmpty() {
        return b.length() == 0;
    }

    /** The bytes so far; the builder is emptied. */
    public byte[] take() {
        if (b.length() > 0) {
            last = b.charAt(b.length() - 1);
        }
        byte[] bytes = b.toString().getBytes(StandardCharsets.ISO_8859_1);
        b.setLength(0);
        return bytes;
    }
}
