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
        BufferedImage image = toImage(video.frame(), video.frameWidth(), video.frameHeight());
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("no PNG writer available");
        }
    }

    /** A TRIADA-85 frame as a Java image. */
    static BufferedImage toImage(int[] frame) {
        return toImage(frame, VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT);
    }

    /** A frame as a Java image (TYPE_INT_RGB stores exactly our 0xRRGGBB ints). */
    static BufferedImage toImage(int[] frame, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, frame, 0, width);
        return image;
    }
}
