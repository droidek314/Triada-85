package org.triada.core.video;

import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Golden-image testing: a rendered frame is compared pixel by pixel with a
 * PNG stored in {@code src/test/resources/golden/} (the "golden" expected
 * picture). If they differ, the actual picture is written next to the build
 * output so a person can look at both.
 *
 * <p>To create or deliberately update the golden pictures, run the tests with
 * {@code ./gradlew :triada-core:test -Dtriada.updateGolden=true}, then LOOK at
 * the new PNGs before committing them. A golden image only proves "nothing
 * changed"; whether the picture is right is a human judgement, backed by the
 * hand-computed pixel tests in {@link VideoChipTest}.
 *
 * <p>Test code may use files and {@code javax.imageio}; only the main code of
 * triada-core must stay free of them.
 */
public final class GoldenImages {

    private GoldenImages() {
    }

    /**
     * Checks {@code frame} (240 × 168, 0xRRGGBB) against golden image {@code name}.
     *
     * @param goldenDir folder with the golden PNGs
     * @param diffDir   folder for "actual" pictures when a check fails
     */
    public static void check(int[] frame, String name, File goldenDir, File diffDir) throws IOException {
        int w = VideoChip.FRAME_WIDTH;
        int h = VideoChip.FRAME_HEIGHT;
        BufferedImage actual = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        actual.setRGB(0, 0, w, h, frame, 0, w);
        File golden = new File(goldenDir, name + ".png");

        if (Boolean.getBoolean("triada.updateGolden")) {
            goldenDir.mkdirs();
            ImageIO.write(actual, "png", golden);
            return;
        }
        if (!golden.exists()) {
            fail("no golden image " + golden + "; create it with -Dtriada.updateGolden=true and inspect it");
        }
        BufferedImage expected = ImageIO.read(golden);
        int differences = 0;
        String first = null;
        if (expected.getWidth() != w || expected.getHeight() != h) {
            first = "size " + expected.getWidth() + " x " + expected.getHeight();
            differences = 1;
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int e = expected.getRGB(x, y) & 0xFFFFFF;
                    int a = frame[y * w + x];
                    if (e != a) {
                        if (first == null) {
                            first = String.format("(%d, %d): expected #%06X, got #%06X", x, y, e, a);
                        }
                        differences++;
                    }
                }
            }
        }
        if (differences > 0) {
            diffDir.mkdirs();
            File out = new File(diffDir, name + "-actual.png");
            ImageIO.write(actual, "png", out);
            fail("golden image " + name + ": " + differences + " pixels differ, first at " + first
                    + "; actual picture saved to " + out);
        }
    }
}
