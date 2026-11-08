package org.triada.rom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.Assembler;
import org.triada.assembler.SourceLoader;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.core.memory.MemoryMap;

/**
 * Test helper: a TRIADA-85 with its ROM, driven like a person would drive it.
 * It types into the KEY queue (at most 27 keys per frame, the queue's size,
 * D-007), waits for the monitor's prompt, and reads the screen back as text
 * from the tile map, where every character is a tile number = ASCII code.
 */
final class Triada {

    final Machine m = Rom.newMachine();
    private final int cursorX = Rom.address("CURSOR_X");
    private final int cursorY = Rom.address("CURSOR_Y");

    /** Boots to the monitor prompt: since M14 that means TRIBASIC's READY., then MON. */
    static Triada booted() {
        Triada t = basic();
        t.type("MON\n");
        return t;
    }

    /** Boots to TRIBASIC's READY. (M14). */
    static Triada basic() {
        Triada t = new Triada();
        t.waitForPrompt();
        return t;
    }

    /** Boots with a program started through AUTORUN (assembled from {@code source}). */
    static Triada autorun(String source) {
        Triada t = new Triada();
        Rom.autorun(t.m, assemble(source));
        return t;
    }

    static TriImage assemble(String source) {
        try {
            return new Assembler(SourceLoader.NONE).assembleSource("test.tasm", source).image();
        } catch (AssemblyException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    void frames(int n) {
        for (int i = 0; i < n; i++) {
            m.runFrame();
        }
        assertFalse(m.cpu().isHalted(), "the CPU halted (fault: " + m.cpu().fault() + ") at PC " + m.cpu().pc());
    }

    /** Types {@code text} ('\n' = Enter), 27 keys per frame, then waits for the next prompt. */
    void type(String text) {
        typeOnly(text);
        waitForPrompt();
    }

    /** Types without waiting for a prompt (e.g. into a running program). */
    void typeOnly(String text) {
        int i = 0;
        while (i < text.length()) {
            for (int k = 0; k < 27 && i < text.length(); k++, i++) {
                char c = text.charAt(i);
                m.input().pushKey(c == '\n' ? 10 : c);
            }
            frames(1);
        }
    }

    /**
     * Runs frames until the machine waits for a command line: the monitor's
     * "> " with the cursor right after it, or TRIBASIC's "READY." with the
     * cursor at the start of the next line (M14) -- and every typed key has
     * been taken.
     */
    void waitForPrompt() {
        waitForPrompt(600);
    }

    void waitForPrompt(int maxFrames) {
        for (int f = 0; f < maxFrames; f++) {
            frames(1);
            if (atPrompt()) {
                return;
            }
        }
        fail("no prompt after " + maxFrames + " frames; screen:\n" + String.join("\n", screen()));
    }

    boolean atPrompt() {
        int x = m.bus().peek(cursorX);
        int y = m.bus().peek(cursorY);
        boolean keysTaken = m.input().queuedKeys() == 0 && m.bus().peek(Rom.address("B_KEYN")) == 0;
        boolean monitor = x == 2 && row(y).startsWith(">");
        // (a machine-code program may leave text before READY.: "7READY.")
        boolean basic = x == 0 && y > 0 && row(y - 1).endsWith("READY.") && row(y).isEmpty();
        return keysTaken && (monitor || basic);
    }

    /** Clears the screen behind the machine's back (tests only): spaces everywhere, cursor home. */
    void clearScreen() {
        for (int i = 0; i < 27 * 18; i++) {
            m.bus().write(MemoryMap.MAP + i, ' ');
        }
        m.bus().write(cursorX, 0);
        m.bus().write(cursorY, 0);
    }

    /** Text row r (0 ... 17), trailing spaces removed; the blinking cursor '_' is ignored. */
    String row(int r) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < 27; c++) {
            int v = m.bus().peek(MemoryMap.MAP + r * 27 + c);
            sb.append(v >= 32 && v <= 126 ? (char) v : '?');
        }
        String s = sb.toString().stripTrailing();
        boolean atCursor = r == m.bus().peek(cursorY) && s.length() == m.bus().peek(cursorX) + 1;
        return atCursor && s.endsWith("_") ? s.substring(0, s.length() - 1).stripTrailing() : s;
    }

    /** All 18 rows. */
    List<String> screen() {
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < 18; r++) {
            rows.add(row(r));
        }
        return rows;
    }

    /** The rows printed after the last command: from the line after the last "> CMD" to the prompt. */
    List<String> outputOf(String command) {
        List<String> rows = screen();
        int start = -1;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r).equals("> " + command.toUpperCase())) {
                start = r;
            }
        }
        if (start < 0) {
            fail("command '" + command + "' not on screen:\n" + String.join("\n", rows));
        }
        List<String> out = new ArrayList<>();
        for (int r = start + 1; r < rows.size() && !rows.get(r).startsWith(">"); r++) {
            out.add(rows.get(r));
        }
        return out;
    }

    int cursorX() {
        return m.bus().peek(cursorX);
    }

    int cursorY() {
        return m.bus().peek(cursorY);
    }
}
