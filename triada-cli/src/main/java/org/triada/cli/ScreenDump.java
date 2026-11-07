package org.triada.cli;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.triada.core.video.VideoChip;

/**
 * Saves the video chip's latest picture as a PNG file, pixel for pixel
 * (240 × 168, border included). Uses the JDK's own {@code javax.imageio},
 * as CLAUDE.md asks: no extra library.
 */
final class ScreenDump {

    private ScreenDump() {
    }

    static void writePng(VideoChip video, Path file) throws IOException {
        BufferedImage image = toImage(video.frame());
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("no PNG writer available");
        }
    }

    /** The frame as a Java image (TYPE_INT_RGB stores exactly our 0xRRGGBB ints). */
    static BufferedImage toImage(int[] frame) {
        BufferedImage image = new BufferedImage(VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT,
                BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, frame, 0, VideoChip.FRAME_WIDTH);
        return image;
    }
}
