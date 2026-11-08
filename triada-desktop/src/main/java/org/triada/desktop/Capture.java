package org.triada.desktop;

import com.badlogic.gdx.graphics.Pixmap;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Saves what was drawn into an off-screen frame buffer as a PNG (M11, the
 * --capture option), with the JDK's own javax.imageio.
 */
final class Capture {

    private Capture() {
    }

    /**
     * Writes {@code shot} (read back from OpenGL) to {@code file}. OpenGL
     * returns the bottom row first, so the rows are turned upside down here.
     */
    static void writePng(Pixmap shot, Path file) throws IOException {
        int w = shot.getWidth();
        int h = shot.getHeight();
        BufferedImage image = toImage(shot.getPixels(), w, h);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            java.nio.file.Files.createDirectories(parent);
        }
        if (!ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("no PNG writer available");
        }
    }

    /** RGBA bytes, bottom row first, into an RGB image, top row first. */
    static BufferedImage toImage(ByteBuffer rgba, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                int r = rgba.get(i) & 0xFF;
                int g = rgba.get(i + 1) & 0xFF;
                int b = rgba.get(i + 2) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }
}
