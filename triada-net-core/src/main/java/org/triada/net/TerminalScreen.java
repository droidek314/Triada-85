package org.triada.net;

import java.util.ArrayList;
import java.util.List;

/**
 * A pretend TRIADA terminal (M18): it takes what a board sends and keeps the
 * screen it would show, 27 × 18 by default, so tests can read it as text. It
 * follows the control codes of SPEC §11.1 the way TERM.TRI (M19) will:
 * text wraps at the right edge and the screen scrolls up at the bottom.
 * Colours are noted ({@link #foreground()}) but not drawn; a line feed (10)
 * is ignored, since 13 is the new line.
 */
public final class TerminalScreen {

    private final int cols;
    private final int rows;
    private final char[][] cells;
    private int x;
    private int y;
    private int foreground = 4;
    private int background = -12;
    private int bells;
    /** Where an ESC sequence stands: 0 = none, 1 = after ESC, 2/3 = parameters to come. */
    private int esc;
    private int escKind;
    private int escFirst;

    public TerminalScreen() {
        this(27, 18);
    }

    public TerminalScreen(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        cells = new char[rows][cols];
        clear();
    }

    /** Takes bytes from the line. */
    public void receive(byte[] bytes) {
        for (byte b : bytes) {
            receive(b & 0xFF);
        }
    }

    public void receive(int c) {
        if (esc == 1) {
            escKind = c;
            esc = c == 'C' || c == 'L' ? 2 : 0;
            return;
        }
        if (esc == 2) {
            escFirst = c - TermOut.OFFSET;
            esc = 3;
            return;
        }
        if (esc == 3) {
            int second = c - TermOut.OFFSET;
            esc = 0;
            if (escKind == 'C') {
                foreground = escFirst;
                background = second;
            } else {
                x = Math.max(0, Math.min(cols - 1, escFirst));
                y = Math.max(0, Math.min(rows - 1, second));
            }
            return;
        }
        switch (c) {
            case TermOut.ESC -> esc = 1;
            case TermOut.CLEAR -> clear();
            case TermOut.NEWLINE -> newline();
            case TermOut.BACKSPACE -> {
                if (x > 0) {
                    x--;
                    cells[y][x] = ' ';
                }
            }
            case TermOut.BELL -> bells++;
            case 10 -> {
                // line feed: 13 already started the new line
            }
            default -> {
                if (c >= 32 && c <= 126) {
                    if (x >= cols) {
                        newline();
                    }
                    cells[y][x++] = (char) c;
                }
            }
        }
    }

    private void clear() {
        for (char[] row : cells) {
            java.util.Arrays.fill(row, ' ');
        }
        x = 0;
        y = 0;
    }

    private void newline() {
        x = 0;
        if (y < rows - 1) {
            y++;
            return;
        }
        for (int r = 1; r < rows; r++) {
            cells[r - 1] = cells[r];
        }
        cells[rows - 1] = new char[cols];
        java.util.Arrays.fill(cells[rows - 1], ' ');
    }

    /** Row {@code r} without trailing spaces. */
    public String row(int r) {
        return new String(cells[r]).stripTrailing();
    }

    /** All rows, trailing empty rows left out. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            out.add(row(r));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** The whole screen as text, one line per row. */
    public String text() {
        return String.join("\n", lines());
    }

    public int cursorX() {
        return x;
    }

    public int cursorY() {
        return y;
    }

    public int foreground() {
        return foreground;
    }

    public int background() {
        return background;
    }

    /** How often the bell rang. */
    public int bells() {
        return bells;
    }
}
