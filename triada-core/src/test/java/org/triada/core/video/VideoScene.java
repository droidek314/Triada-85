package org.triada.core.video;

import java.util.Random;
import org.triada.core.Machine;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/**
 * Test helper: builds video memory contents directly, so tests can say
 * "tile 3 looks like this, put it at map cell (2, 5)" in one line each.
 * The packing formulas are written out here independently of the chip.
 */
final class VideoScene {

    final Machine machine = new Machine();

    /** Pixel values → the word holding them: p0·19683 + p1·729 + p2·27 + p3 (SPEC §4.3). */
    static int pack(int p0, int p1, int p2, int p3) {
        return p0 * 19_683 + p1 * 729 + p2 * 27 + p3;
    }

    /** Defines tile {@code t} from 64 pixel values, row by row. */
    VideoScene tile(int t, int[] pixels) {
        for (int row = 0; row < 8; row++) {
            int r = row * 8;
            int address = MemoryMap.TILES + 16 * t + 2 * row;   // SPEC §4.4
            machine.bus().write(address, pack(pixels[r], pixels[r + 1], pixels[r + 2], pixels[r + 3]));
            machine.bus().write(address + 1, pack(pixels[r + 4], pixels[r + 5], pixels[r + 6], pixels[r + 7]));
        }
        return this;
    }

    /** A tile of one colour. */
    VideoScene solidTile(int t, int value) {
        int[] p = new int[64];
        java.util.Arrays.fill(p, value);
        return tile(t, p);
    }

    /** A tile of seeded random pixel values (good for spotting any misplaced pixel). */
    VideoScene randomTile(int t, Random rnd) {
        int[] p = new int[64];
        for (int i = 0; i < 64; i++) {
            p[i] = rnd.nextInt(27) - 13;
        }
        return tile(t, p);
    }

    /** Puts value {@code v} into tile map cell (col, row). */
    VideoScene map(int col, int row, int v) {
        machine.bus().write(MemoryMap.MAP + row * 27 + col, v);       // SPEC §4.5
        return this;
    }

    VideoScene fillMap(int v) {
        for (int row = 0; row < 18; row++) {
            for (int col = 0; col < 27; col++) {
                map(col, row, v);
            }
        }
        return this;
    }

    /** Sets sprite i (SPEC §4.7). */
    VideoScene sprite(int i, int x, int y, int tile, int flags) {
        int base = MemoryMap.SPRITES + 4 * i;
        machine.bus().write(base, x);
        machine.bus().write(base + 1, y);
        machine.bus().write(base + 2, tile);
        machine.bus().write(base + 3, flags);
        return this;
    }

    VideoScene register(int k, int value) {
        machine.bus().write(MemoryMap.IO_BASE + k, value);
        return this;
    }

    VideoScene scroll(int x, int y) {
        return register(IoRegisters.SCROLL_X, x).register(IoRegisters.SCROLL_Y, y);
    }

    VideoScene border(int value) {
        return register(IoRegisters.BORDER, value);
    }

    VideoChip render() {
        machine.video().render();
        return machine.video();
    }

    /** The RGB of the frame pixel at (x, y), border coordinates included. */
    static int frameAt(VideoChip v, int x, int y) {
        return v.frame()[y * VideoChip.FRAME_WIDTH + x];
    }
}
