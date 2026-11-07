package org.triada.core.video;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Whole scenes rendered from known memory contents and compared with stored
 * pictures (CLAUDE.md M6). Each scene exercises several rules at once; the
 * individual rules are pinned down in {@link VideoChipTest}.
 */
class GoldenImageTest {

    private static final File GOLDEN = new File(System.getProperty("triada.goldenDir", "src/test/resources/golden"));
    private static final File DIFF = new File(System.getProperty("triada.diffDir", "build/golden-diff"));

    private final VideoScene scene = new VideoScene();

    private void check(String name) throws IOException {
        GoldenImages.check(scene.render().frame(), name, GOLDEN, DIFF);
    }

    /** All 27 colours as vertical bars (−13 on the left … 13 on the right), on a white border. */
    @Test
    void palette() throws IOException {
        for (int v = -13; v <= 13; v++) {
            scene.solidTile(v + 14, v);
        }
        for (int row = 0; row < 18; row++) {
            for (int col = 0; col < 27; col++) {
                scene.map(col, row, col + 1);
            }
        }
        scene.border(13);
        check("palette");
    }

    /** Random tiles, half of them mirrored, scrolled by (13, 7) so the wrap seam is visible. */
    @Test
    void tilesMirroredAndScrolled() throws IOException {
        Random rnd = new Random(85);
        for (int t = 1; t <= 12; t++) {
            scene.randomTile(t, rnd);
        }
        for (int row = 0; row < 18; row++) {
            for (int col = 0; col < 27; col++) {
                scene.map(col, row, rnd.nextInt(25) - 12);
            }
        }
        scene.scroll(13, 7).border(-11);
        check("tiles-mirrored-scrolled");
    }

    /**
     * A striped background with sprites: an arrow in all four orientations,
     * overlapping squares (order), and sprites cut off at all four edges.
     */
    @Test
    void sprites() throws IOException {
        // Background: diagonal stripes of dark blue and dark green.
        int[] stripes = new int[64];
        for (int i = 0; i < 64; i++) {
            stripes[i] = (i / 8 + i % 8) % 4 < 2 ? -12 : -10;
        }
        scene.tile(1, stripes).fillMap(1).border(-13);

        int[] arrow = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x <= y; x++) {
                arrow[y * 8 + x] = x == 0 ? 13 : 11;      // white edge, yellow body, rest transparent
            }
        }
        scene.tile(2, arrow).solidTile(3, 5).solidTile(4, -7).solidTile(5, 13);

        scene.sprite(0, 40, 40, 2, 1)       // normal
                .sprite(1, 52, 40, -2, 1)   // mirrored
                .sprite(2, 40, 52, 2, -1)   // flipped
                .sprite(3, 52, 52, -2, -1)  // both
                .sprite(4, 100, 60, 3, 1)   // red
                .sprite(5, 104, 64, 4, 1)   // green over red
                .sprite(6, 108, 68, 5, 1)   // white over green
                .sprite(7, -4, -4, 3, 1)    // clipped top-left
                .sprite(8, 212, -4, 4, 1)   // clipped top-right
                .sprite(9, -4, 140, 5, 1)   // clipped bottom-left
                .sprite(10, 212, 140, 3, 1) // clipped bottom-right
                .sprite(11, 150, 100, 3, 0); // hidden
        check("sprites");
    }
}
