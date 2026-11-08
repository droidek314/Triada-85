package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.triada.assembler.Assembler;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.AssemblyResult;
import org.triada.core.Machine;
import org.triada.core.memory.MemoryMap;
import org.triada.rom.Rom;

/**
 * Test helper for the M10 games: a booted machine running one game, plus the
 * game's symbols, so a test can play it like a person (joystick, buttons) and
 * look inside it like the debugger (RAM, tile map, screen).
 *
 * <p>The games use the ROM's routines (PRINT, LOCATE ...), so they are started
 * the way the desktop starts them: power on, boot, then AUTORUN (D-028).
 */
final class GameHarness {

    /** Frames the boot takes before the game's first instruction (about 48; a little margin). */
    static final int BOOT_FRAMES = 60;

    final Machine m;
    final Map<String, Integer> symbols;
    private final String name;

    GameHarness(String program) {
        this.name = program;
        try {
            String path = Cli.root().resolve("programs/" + program + ".tasm").toString();
            AssemblyResult result = new Assembler(new FileSourceLoader()).assemble(path);
            symbols = result.symbols();
            m = Rom.newMachine();
            Rom.autorun(m, result.image());
        } catch (AssemblyException e) {
            throw new IllegalStateException(program + " doesn't assemble: " + e.getMessage(), e);
        }
        frames(BOOT_FRAMES);
    }

    /** Runs {@code n} frames; the game must neither fault nor halt. */
    void frames(int n) {
        for (int i = 0; i < n; i++) {
            m.runFrame();
            if (m.cpu().isHalted()) {
                fail(name + " stopped: " + (m.cpu().fault() != null
                        ? "fault " + m.cpu().fault() + " at " + m.cpu().faultPc() : "HLT"));
            }
        }
    }

    /** The value of a symbol of the game (a label or a constant). */
    int sym(String symbol) {
        Integer v = symbols.get(symbol);
        assertTrue(v != null, name + " has no symbol " + symbol);
        return v;
    }

    int ram(int address) {
        return m.bus().peek(address);
    }

    void poke(int address, int value) {
        m.bus().write(address, value);
    }

    /** Variable {@code offset} of the block at label {@code base} (the games' "vars"). */
    int var(String offset) {
        return ram(sym("vars") + sym(offset));
    }

    void setVar(String offset, int value) {
        poke(sym("vars") + sym(offset), value);
    }

    int io(int k) {
        return m.bus().peek(MemoryMap.IO_BASE + k);
    }

    /** The tile map entry at column {@code col}, row {@code row}. */
    int cell(int col, int row) {
        return ram(MemoryMap.MAP + row * 27 + col);
    }

    void setCell(int col, int row, int tile) {
        poke(MemoryMap.MAP + row * 27 + col, tile);
    }

    /** The text of tile map row {@code row} (font tiles as characters, others as '.'). */
    String row(int row) {
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < 27; col++) {
            int t = cell(col, row);
            sb.append(t >= 32 && t <= 126 ? (char) t : '.');
        }
        return sb.toString();
    }

    void joy(int x, int y) {
        m.input().setJoyX(x);
        m.input().setJoyY(y);
    }

    /** Presses and releases a button: held for one frame, released for one. */
    void tap(char button) {
        setButton(button, true);
        frames(1);
        setButton(button, false);
        frames(1);
    }

    void setButton(char button, boolean pressed) {
        switch (button) {
            case 'A' -> m.input().setButtonA(pressed);
            case 'B' -> m.input().setButtonB(pressed);
            case 'C' -> m.input().setButtonC(pressed);
            default -> throw new IllegalArgumentException("button " + button);
        }
    }

    BufferedImage screen() {
        return ScreenDump.toImage(m.video().frame());
    }

    /** Saves the screen into build/golden-diff, to look at it while writing a test. */
    void save(String file) throws IOException {
        Path dir = Path.of(System.getProperty("triada.diffDir", "build/golden-diff"));
        Files.createDirectories(dir);
        ImageIO.write(screen(), "png", dir.resolve(file).toFile());
    }

    /**
     * Compares the screen with {@code triada-cli/src/test/resources/golden/<file>}.
     * Run with -Dtriada.updateGolden=true to (re)create it, then look at it.
     */
    void assertGolden(String file) throws IOException {
        BufferedImage actual = screen();
        File golden = Cli.root().resolve("triada-cli/src/test/resources/golden/" + file).toFile();
        if (Boolean.getBoolean("triada.updateGolden")) {
            golden.getParentFile().mkdirs();
            ImageIO.write(actual, "png", golden);
        }
        assertTrue(golden.exists(), "missing " + golden + "; create it with -Dtriada.updateGolden=true and inspect it");
        BufferedImage expected = ImageIO.read(golden);
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                if ((expected.getRGB(x, y) & 0xFFFFFF) != (actual.getRGB(x, y) & 0xFFFFFF)) {
                    save("actual-" + file);
                    fail(file + " differs from the golden image at (" + x + ", " + y
                            + "); the actual screen is in build/golden-diff/actual-" + file);
                }
            }
        }
        assertFalse(m.cpu().isHalted());
    }
}
