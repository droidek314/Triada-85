package org.triada.core.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.cpu.CpuBenchmark;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;
import org.triada.core.ternary.Ternary;
import org.triada.core.ternary.Word;

/**
 * The video chip, rule by rule (SPEC §4). Every expected value is worked out
 * by hand or from the spec's formulas, never taken from the chip itself.
 */
class VideoChipTest {

    private final VideoScene scene = new VideoScene();

    // ================================================================ palette (§4.2)

    @Test
    void paletteTableFromTheSpec() {
        assertEquals(0x000000, Palette.rgb(-13), "black");
        assertEquals(0xFFFFFF, Palette.rgb(13), "white");
        assertEquals(0x808080, Palette.rgb(0), "gray");
        assertEquals(0xFF0000, Palette.rgb(5), "red");
        assertEquals(0x00FF00, Palette.rgb(-7), "green");
        assertEquals(0x0000FF, Palette.rgb(-11), "blue");
        assertEquals(0xFFFF00, Palette.rgb(11), "yellow");
    }

    @Test
    void everyColourIsItsTritsAsRgb() {
        int[] level = {0, 128, 255};
        for (int v = -13; v <= 13; v++) {
            String trits = Ternary.toString(v, 3);            // e.g. "+-0"
            int r = level[trits.charAt(0) == '-' ? 0 : trits.charAt(0) == '0' ? 1 : 2];
            int g = level[trits.charAt(1) == '-' ? 0 : trits.charAt(1) == '0' ? 1 : 2];
            int b = level[trits.charAt(2) == '-' ? 0 : trits.charAt(2) == '0' ? 1 : 2];
            assertEquals(r << 16 | g << 8 | b, Palette.rgb(v), "value " + v + " = " + trits);
        }
    }

    @Test
    void foldKeepsTheLowestThreeTrits() {
        assertEquals(5, Palette.fold(5));
        assertEquals(5, Palette.fold(5 + 27));
        assertEquals(-13, Palette.fold(14));
        assertEquals(13, Palette.fold(-14));
        assertEquals(Ternary.parse("++0"), Palette.fold(Word.parse("-0-0-0-0-++0")));
    }

    // ================================================================ power-on

    @Test
    void powerOnPictureIsAllGray() {
        VideoChip v = scene.render();
        assertEquals(240 * 168, v.frame().length);
        for (int rgb : v.frame()) {
            if (rgb != 0x808080) {
                fail("power-on picture should be gray everywhere: RAM, BORDER and the tile map are all 0");
            }
        }
    }

    // ================================================================ tiles and packing (§4.3, §4.4)

    @Test
    void fourPixelsPerWordInTheRightOrder() {
        int[] p = new int[64];
        p[0] = -13;  // row 0, word 0: pixels 0..3
        p[1] = 5;
        p[2] = -7;
        p[3] = 13;
        p[4] = 11;   // row 0, word 1: pixels 4..7
        p[5] = -11;
        p[6] = 1;
        p[7] = -1;
        scene.tile(1, p).map(0, 0, 1);
        VideoChip v = scene.render();
        for (int x = 0; x < 8; x++) {
            assertEquals(p[x], v.screenPixel(x, 0), "pixel " + x);
        }
    }

    @Test
    void tileRowsAreTwoWordsEach() {
        int[] p = new int[64];
        for (int row = 0; row < 8; row++) {
            for (int x = 0; x < 8; x++) {
                p[row * 8 + x] = row - 4 + (x < 4 ? 0 : 8);   // rows differ, halves differ
            }
        }
        scene.tile(242, p).map(0, 0, 242);
        VideoChip v = scene.render();
        for (int row = 0; row < 8; row++) {
            assertEquals(row - 4, v.screenPixel(0, row), "left half of row " + row);
            assertEquals(row + 4, v.screenPixel(7, row), "right half of row " + row);
        }
    }

    @Test
    void mapCellsAddressTheRightScreenSquares() {
        scene.solidTile(1, 5).solidTile(2, -11);
        scene.map(0, 0, 1).map(26, 0, 2).map(0, 17, 2).map(26, 17, 1).map(13, 9, 1);
        VideoChip v = scene.render();
        assertEquals(5, v.screenPixel(0, 0));
        assertEquals(-11, v.screenPixel(26 * 8 + 7, 0));
        assertEquals(-11, v.screenPixel(0, 17 * 8 + 7));
        assertEquals(5, v.screenPixel(215, 143));
        assertEquals(5, v.screenPixel(13 * 8, 9 * 8));
        assertEquals(0, v.screenPixel(12 * 8 + 7, 9 * 8), "neighbouring cell is still tile 0");
        assertEquals(0, v.screenPixel(13 * 8 + 8, 9 * 8));
    }

    @Test
    void negativeMapValuesMirrorTheTile() {
        int[] p = new int[64];
        for (int x = 0; x < 8; x++) {
            p[x] = x - 4;              // row 0: -4 -3 -2 -1 0 1 2 3
        }
        scene.tile(7, p).map(0, 0, 7).map(1, 0, -7);
        VideoChip v = scene.render();
        for (int x = 0; x < 8; x++) {
            assertEquals(x - 4, v.screenPixel(x, 0), "normal");
            assertEquals(3 - x, v.screenPixel(8 + x, 0), "mirrored");
        }
    }

    @Test
    void valuesBeyond242DrawTile0() {
        scene.solidTile(0, -13).solidTile(1, 5);
        scene.fillMap(1).map(0, 0, 243).map(1, 0, -243).map(2, 0, Word.MIN).map(3, 0, 242);
        VideoChip v = scene.render();
        assertEquals(-13, v.screenPixel(0, 0));
        assertEquals(-13, v.screenPixel(8, 0));
        assertEquals(-13, v.screenPixel(16, 0));
        assertEquals(0, v.screenPixel(24, 0), "tile 242 exists (it is empty here)");
        assertEquals(5, v.screenPixel(32, 0));
    }

    @Test
    void grayIsNotTransparentInTheBackground() {
        scene.solidTile(0, 0).border(-13);
        assertEquals(0x808080, VideoScene.frameAt(scene.render(), 12, 12));
    }

    // ================================================================ scrolling (§4.6)

    /** Every screen pixel must equal background pixel ((x+sx) mod 216, (y+sy) mod 144). */
    private void checkScroll(int sx, int sy) {
        Random rnd = new Random(42);
        for (int t = 1; t <= 30; t++) {
            scene.randomTile(t, rnd);
        }
        for (int row = 0; row < 18; row++) {
            for (int col = 0; col < 27; col++) {
                scene.map(col, row, rnd.nextInt(61) - 30);    // tiles 1..30, half of them mirrored
            }
        }
        VideoChip v = scene.scroll(0, 0).render();
        int[][] bg = new int[216][144];
        for (int y = 0; y < 144; y++) {
            for (int x = 0; x < 216; x++) {
                bg[x][y] = v.screenPixel(x, y);
            }
        }
        scene.scroll(sx, sy).render();
        for (int y = 0; y < 144; y++) {
            for (int x = 0; x < 216; x++) {
                int ex = Math.floorMod(x + sx, 216);
                int ey = Math.floorMod(y + sy, 144);
                if (v.screenPixel(x, y) != bg[ex][ey]) {
                    fail("scroll (" + sx + ", " + sy + "): screen (" + x + ", " + y + ") should show background ("
                            + ex + ", " + ey + ")");
                }
            }
        }
    }

    @Test
    void scrollRight() {
        checkScroll(1, 0);
    }

    @Test
    void scrollDown() {
        checkScroll(0, 1);
    }

    @Test
    void scrollWrapsAtTheEdges() {
        checkScroll(215, 143);
    }

    @Test
    void negativeScrollUsesPositiveMod() {
        checkScroll(-1, -1);
    }

    @Test
    void hugeScrollValuesWrapToo() {
        checkScroll(Word.MAX, Word.MIN);
    }

    // ================================================================ sprites (§4.7)

    private static int[] arrowTile() {
        // Row y, column x: an asymmetric shape so every flip shows.
        int[] p = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x <= y; x++) {
                p[y * 8 + x] = x == 0 ? 13 : 5;   // white left edge, red body, gray (transparent) right part
            }
        }
        return p;
    }

    @Test
    void aSpriteIsDrawnAtItsScreenPosition() {
        scene.tile(50, arrowTile()).solidTile(0, -13).sprite(0, 100, 60, 50, 1);
        VideoChip v = scene.render();
        assertEquals(13, v.screenPixel(100, 60), "top-left pixel of the arrow");
        assertEquals(5, v.screenPixel(107, 67), "bottom-right pixel");
        assertEquals(-13, v.screenPixel(107, 60), "gray in a sprite is transparent: background shows");
        assertEquals(-13, v.screenPixel(99, 60), "outside the sprite");
    }

    @Test
    void spritesAreNotScrolled() {
        scene.tile(50, arrowTile()).sprite(0, 100, 60, 50, 1).scroll(37, 11);
        assertEquals(13, scene.render().screenPixel(100, 60));
    }

    @Test
    void negativeTileMirrorsAndFlagsMinusOneFlips() {
        scene.tile(50, arrowTile())
                .sprite(0, 0, 0, -50, 1)          // mirrored left-right
                .sprite(1, 20, 0, 50, -1)         // flipped upside down
                .sprite(2, 40, 0, -50, -1);       // both
        VideoChip v = scene.render();
        assertEquals(13, v.screenPixel(7, 0), "mirrored: white edge on the right");
        assertEquals(0, v.screenPixel(0, 0), "mirrored: top row has only the corner pixel");
        assertEquals(13, v.screenPixel(20, 7), "flipped: corner at the bottom");
        assertEquals(5, v.screenPixel(27, 0), "flipped: full row on top");
        assertEquals(13, v.screenPixel(47, 7), "both");
        assertEquals(5, v.screenPixel(40, 0), "both: full row on top");
    }

    @Test
    void laterSpritesAreOnTop() {
        scene.solidTile(60, 5).solidTile(61, -11)
                .sprite(3, 50, 50, 60, 1)
                .sprite(4, 54, 54, 61, 1);
        VideoChip v = scene.render();
        assertEquals(5, v.screenPixel(50, 50));
        assertEquals(-11, v.screenPixel(55, 55), "sprite 4 covers sprite 3");
        scene.sprite(3, 54, 54, 61, 1).sprite(4, 50, 50, 60, 1);
        assertEquals(5, scene.render().screenPixel(55, 55), "now sprite 4 (red) covers sprite 3 (blue)");
    }

    @Test
    void hiddenSpritesAreNotDrawn() {
        scene.solidTile(60, 5)
                .sprite(0, 0, 0, 60, 0)          // hidden
                .sprite(1, 10, 0, 60, 2)         // undefined flag: hidden (D-021)
                .sprite(26, 20, 0, 60, 1);       // last sprite, visible
        VideoChip v = scene.render();
        assertEquals(0, v.screenPixel(0, 0));
        assertEquals(0, v.screenPixel(10, 0));
        assertEquals(5, v.screenPixel(20, 0));
    }

    @Test
    void spritesAreClippedAtAllFourEdges() {
        scene.solidTile(60, 5).border(-13)
                .sprite(0, -4, -4, 60, 1)        // top-left corner: 4 × 4 pixels visible
                .sprite(1, 212, 140, 60, 1)      // bottom-right corner
                .sprite(2, -8, 50, 60, 1)        // completely off to the left
                .sprite(3, Word.MAX, Word.MIN, 60, 1);   // absurd coordinates: nothing, no crash
        VideoChip v = scene.render();
        assertEquals(5, v.screenPixel(0, 0));
        assertEquals(5, v.screenPixel(3, 3));
        assertEquals(0, v.screenPixel(4, 4));
        assertEquals(5, v.screenPixel(215, 143));
        assertEquals(0, v.screenPixel(211, 139));
        assertEquals(0, v.screenPixel(0, 50));
        // Clipped to the SCREEN: the border around it stays border-coloured.
        assertEquals(0x000000, VideoScene.frameAt(v, 11, 11));
        assertEquals(0x000000, VideoScene.frameAt(v, 228, 156));
    }

    @Test
    void spriteTileBeyond242IsTile0() {
        scene.solidTile(0, 5).sprite(0, 30, 30, 500, 1);
        assertEquals(5, scene.render().screenPixel(30, 30));
    }

    // ================================================================ border and registers

    @Test
    void borderSurroundsTheScreen() {
        scene.solidTile(0, -13).border(5);
        VideoChip v = scene.render();
        assertEquals(0xFF0000, VideoScene.frameAt(v, 0, 0));
        assertEquals(0xFF0000, VideoScene.frameAt(v, 11, 11), "last border pixel");
        assertEquals(0x000000, VideoScene.frameAt(v, 12, 12), "first screen pixel");
        assertEquals(0x000000, VideoScene.frameAt(v, 227, 155), "last screen pixel");
        assertEquals(0xFF0000, VideoScene.frameAt(v, 228, 156), "first border pixel after the screen");
        assertEquals(0xFF0000, VideoScene.frameAt(v, 239, 167));
    }

    @Test
    void borderValuesBeyondThePaletteFoldToThreeTrits() {
        scene.border(5 + 27);
        assertEquals(0xFF0000, VideoScene.frameAt(scene.render(), 0, 0));
    }

    @Test
    void videoRegistersReadBackWhatWasWritten() {
        scene.scroll(-5, 300).border(32);
        assertEquals(-5, scene.machine.bus().read(MemoryMap.IO_BASE + IoRegisters.SCROLL_X));
        assertEquals(300, scene.machine.bus().read(MemoryMap.IO_BASE + IoRegisters.SCROLL_Y));
        assertEquals(32, scene.machine.bus().read(MemoryMap.IO_BASE + IoRegisters.BORDER));
        scene.machine.powerOn();
        assertEquals(0, scene.machine.bus().read(MemoryMap.IO_BASE + IoRegisters.BORDER));
    }

    // ================================================================ machine integration

    @Test
    void theMachineRendersAfterEveryFrame() {
        Machine m = scene.machine;
        m.bus().write(MemoryMap.MAP, 1);
        scene.solidTile(1, 11);
        // The CPU halts at once (empty ROM); the picture is still drawn every frame.
        m.runFrame();
        assertEquals(11, m.video().screenPixel(0, 0));
        m.bus().write(MemoryMap.MAP, 0);
        assertEquals(11, m.video().screenPixel(0, 0), "not before the next frame");
        m.runFrame();
        assertEquals(0, m.video().screenPixel(0, 0));
    }

    @Test
    void renderingAllocatesNothing() {
        Random rnd = new Random(1);
        for (int t = 0; t < 30; t++) {
            scene.randomTile(t, rnd);
        }
        for (int i = 0; i < 27; i++) {
            scene.sprite(i, i * 8, i * 5, i, i % 2 == 0 ? 1 : -1);
        }
        VideoChip v = scene.machine.video();
        for (int i = 0; i < 100; i++) {
            v.render();                          // warm-up
        }
        long before = CpuBenchmark.allocatedBytes();
        for (int i = 0; i < 300; i++) {
            v.render();
        }
        long allocated = CpuBenchmark.allocatedBytes() - before;
        assertTrue(allocated < 10_000, allocated + " bytes allocated by 300 frames");
    }
}
