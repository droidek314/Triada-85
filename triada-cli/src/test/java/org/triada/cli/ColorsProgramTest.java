package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triada.core.video.Palette;

/**
 * {@code programs/colors.tasm} (CLAUDE.md M6: "shows all 27 colors plus a
 * moving sprite"), run through the real command line with --dump-screen.
 */
class ColorsProgramTest {

    @TempDir
    Path dir;

    private BufferedImage screenAfter(int frames) throws Exception {
        Path png = dir.resolve("colors-" + frames + ".png");
        Cli.Result r = Cli.run("run", Cli.root().resolve("programs/colors.tasm").toString(),
                "--frames", Integer.toString(frames), "--dump-screen", png.toString(), "--no-boot");
        assertEquals(0, r.code(), r.toString());
        return ImageIO.read(png.toFile());
    }

    @Test
    void all27ColoursAreOnTheScreen() throws Exception {
        BufferedImage img = screenAfter(2);
        Set<Integer> seen = new HashSet<>();
        for (int y = 12; y < 12 + 144; y++) {
            for (int x = 12; x < 12 + 216; x++) {
                seen.add(img.getRGB(x, y) & 0xFFFFFF);
            }
        }
        for (int v = -13; v <= 13; v++) {
            assertTrue(seen.contains(Palette.rgb(v)), String.format("colour %d (#%06X) missing", v, Palette.rgb(v)));
        }
        assertEquals(0x0000FF, img.getRGB(0, 0) & 0xFFFFFF, "blue border");
    }

    @Test
    void theBallMovesAndBouncesWithMirroring() throws Exception {
        // The ball starts at (20, 10) moving +1, +1 per frame; in frame f it is
        // drawn at (20 + f - 1, 10 + f - 1). Up to frame 23 it is still above the
        // bars, on black, where its white highlight is easy to find.
        int[] early = findHighlight(screenAfter(2));
        int[] later = findHighlight(screenAfter(22));
        assertEquals(20, later[0] - early[0], "moved right by 20 pixels in 20 frames");
        assertEquals(20, later[1] - early[1], "moved down by 20 pixels in 20 frames");
        // After the bottom bounce (y reaches 137 around frame 128) the ball is
        // shown upright instead of flipped, so the highlight jumps to the top.
        BufferedImage before = screenAfter(127);
        BufferedImage after = screenAfter(130);
        assertNotEquals(highlightRowInBall(before), highlightRowInBall(after));
    }

    /** Screen coordinates of the first white pixel outside the bar area (the highlight). */
    private static int[] findHighlight(BufferedImage img) {
        for (int y = 12; y < 12 + 144; y++) {
            for (int x = 12; x < 12 + 216; x++) {
                boolean barRows = y >= 12 + 40 && y < 12 + 104;     // rows 5..12 hold bars and white lines
                if (!barRows && (img.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF) {
                    return new int[] {x - 12, y - 12};
                }
            }
        }
        fail("ball highlight not found");
        return null;
    }

    /** Where, inside the ball's 8 rows, the white highlight is: smaller = nearer the top. */
    private static int highlightRowInBall(BufferedImage img) {
        int top = -1;
        int white = -1;
        for (int y = 12; y < 12 + 144 && white < 0; y++) {
            for (int x = 12; x < 12 + 216; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                boolean barRows = y >= 12 + 40 && y < 12 + 104;
                if (barRows) {
                    continue;
                }
                if (top < 0 && rgb == 0xFF0000) {
                    top = y;
                }
                if (top >= 0 && rgb == 0xFFFFFF) {
                    white = y;
                    break;
                }
            }
        }
        return white - top;
    }

    /** A golden screenshot of frame 60; see GoldenImages in triada-core for how to update it. */
    @Test
    void goldenScreenAtFrame60() throws Exception {
        BufferedImage actual = screenAfter(60);
        File golden = Cli.root().resolve("triada-cli/src/test/resources/golden/colors-60.png").toFile();
        if (Boolean.getBoolean("triada.updateGolden")) {
            golden.getParentFile().mkdirs();
            ImageIO.write(actual, "png", golden);
            return;
        }
        assertTrue(golden.exists(), "missing " + golden + "; create it with -Dtriada.updateGolden=true and inspect it");
        BufferedImage expected = ImageIO.read(golden);
        for (int y = 0; y < 168; y++) {
            for (int x = 0; x < 240; x++) {
                if ((expected.getRGB(x, y) & 0xFFFFFF) != (actual.getRGB(x, y) & 0xFFFFFF)) {
                    Path out = Path.of(System.getProperty("triada.diffDir", "build/golden-diff"));
                    Files.createDirectories(out);
                    ImageIO.write(actual, "png", out.resolve("colors-60-actual.png").toFile());
                    fail("colors.tasm frame 60 differs from the golden image at (" + x + ", " + y
                            + "); actual saved to " + out.resolve("colors-60-actual.png"));
                }
            }
        }
    }
}
