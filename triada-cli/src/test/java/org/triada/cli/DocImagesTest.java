package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.video.Palette;
import org.triada.rom.Rom;

/**
 * The pictures in the README and the M11 document ({@code docs/images/}),
 * made from the real machine so they can't drift away from it.
 *
 * <p>Run with {@code -Dtriada.updateDocImages=true} to (re)create them.
 * Normally the test only checks that every PNG still matches what the machine
 * draws today, and that the animation is there.
 */
class DocImagesTest {

    private static final Path DIR = Cli.root().resolve("docs/images");
    private static final boolean UPDATE = Boolean.getBoolean("triada.updateDocImages");
    private static final int SCALE = 3;

    @Test
    void bootScreen() throws IOException {
        Machine m = Rom.newMachine();
        for (int i = 0; i < 60; i++) {
            m.runFrame();
        }
        check("boot.png", ScreenDump.toImage(m.video().frame()));
    }

    /** M15: TRIBASIC's three-way IF, typed in and run on the boot screen. */
    @Test
    void tribasicSession() throws IOException {
        Machine m = typedIn("""
                10 FOR T = -1 TO 1
                20 IF T THEN PRINT "YES" MAYBE PRINT "MAYBE" ELSE PRINT "NO"
                30 NEXT
                RUN
                """, 200);
        check("tribasic.png", ScreenDump.toImage(m.video().frame()));
    }

    /** M15: the TERNCLOCK example after three seconds. */
    @Test
    void ternclock() throws IOException {
        String program = Files.readString(Cli.root().resolve("programs/basic/TERNCLOCK.bas"));
        Machine m = typedIn(program + "RUN\n", 60 * 3 + 120);
        check("ternclock.png", ScreenDump.toImage(m.video().frame()));
    }

    /** Boots TRIBASIC and types {@code text} in (paste-typing, M15), then runs {@code frames} frames more. */
    private static Machine typedIn(String text, int frames) {
        Machine m = Rom.newMachine();
        org.triada.core.io.KeyFeeder keys = new org.triada.core.io.KeyFeeder(text.replace("\r\n", "\n"));
        for (int i = 0; i < 60; i++) {
            m.runFrame();
        }
        while (keys.feed(m.input())) {
            m.runFrame();
        }
        for (int i = 0; i < frames; i++) {
            m.runFrame();
        }
        int cursor = Rom.address("CURSOR_X");
        int row = Rom.address("CURSOR_Y");
        m.bus().write(org.triada.core.memory.MemoryMap.MAP + m.bus().peek(row) * 27 + m.bus().peek(cursor), ' ');
        m.video().render();                         // the blinking cursor would make the picture flicker
        return m;
    }

    @Test
    void theTypeInListing() throws IOException {
        GameHarness g = new GameHarness("triangle");
        g.frames(80);
        check("triangle.png", g.screen());
    }

    @Test
    void trizmeyka() throws IOException {
        GameHarness g = new GameHarness("trizmeyka");
        g.tap('A');
        for (int f = 0; f < 1500; f++) {               // 25 seconds of the autopilot
            TrizmeykaTest.autopilot(g);
            g.frames(1);
        }
        check("trizmeyka.png", g.screen());
    }

    @Test
    void troika() throws IOException {
        GameHarness g = new GameHarness("troika");
        check("troika.png", g.screen());
    }

    @Test
    void oblaka() throws IOException {
        GameHarness g = new GameHarness("oblaka");
        g.tap('A');
        for (int f = 0; f < 400; f++) {
            OblakaTest.autopilot(g);
            g.frames(1);
        }
        check("oblaka.png", g.screen());
    }

    /** An animation of OBLAKA: 3 seconds of the autopilot's flight, every 3rd frame, at 2x. */
    @Test
    void oblakaAnimation() throws IOException {
        File gif = DIR.resolve("oblaka.gif").toFile();
        if (UPDATE) {
            GameHarness g = new GameHarness("oblaka");
            g.tap('A');
            for (int f = 0; f < 300; f++) {
                OblakaTest.autopilot(g);
                g.frames(1);
            }
            List<BufferedImage> frames = new ArrayList<>();
            for (int f = 0; f < 180; f++) {
                OblakaTest.autopilot(g);
                g.frames(1);
                if (f % 3 == 0) {
                    frames.add(indexed(g.screen(), 2));
                }
            }
            writeGif(frames, gif, 5);                 // 5/100 s per picture: 20 per second
        }
        assertTrue(gif.exists(), "missing " + gif + "; create it with -Dtriada.updateDocImages=true");
        try (ImageInputStream in = ImageIO.createImageInputStream(gif)) {
            ImageReader r = ImageIO.getImageReadersByFormatName("gif").next();
            r.setInput(in);
            assertEquals(60, r.getNumImages(true), "60 pictures");
            assertEquals(480, r.getWidth(0));
        }
    }

    // --- helpers ---------------------------------------------------------------------

    /** Writes (when updating) or compares a 3x picture. */
    private static void check(String name, BufferedImage screen) throws IOException {
        BufferedImage big = scale(screen, SCALE);
        File file = DIR.resolve(name).toFile();
        if (UPDATE) {
            Files.createDirectories(DIR);
            ImageIO.write(big, "png", file);
        }
        assertTrue(file.exists(), "missing " + file + "; create it with -Dtriada.updateDocImages=true");
        BufferedImage stored = ImageIO.read(file);
        assertEquals(big.getWidth(), stored.getWidth());
        assertEquals(big.getHeight(), stored.getHeight());
        for (int y = 0; y < big.getHeight(); y++) {
            for (int x = 0; x < big.getWidth(); x++) {
                if ((big.getRGB(x, y) & 0xFFFFFF) != (stored.getRGB(x, y) & 0xFFFFFF)) {
                    fail(name + " is out of date at (" + x + ", " + y + "); recreate it with -Dtriada.updateDocImages=true");
                }
            }
        }
    }

    /** Nearest-neighbour enlargement: every pixel becomes a k x k square. */
    static BufferedImage scale(BufferedImage src, int k) {
        BufferedImage out = new BufferedImage(src.getWidth() * k, src.getHeight() * k, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x / k, y / k));
            }
        }
        return out;
    }

    /** The picture, enlarged, as a 27-colour indexed image: GIF needs a palette, and ours has exactly 27. */
    private static BufferedImage indexed(BufferedImage src, int k) {
        byte[] r = new byte[27];
        byte[] g = new byte[27];
        byte[] b = new byte[27];
        for (int v = -13; v <= 13; v++) {
            int rgb = Palette.rgb(v);
            r[v + 13] = (byte) (rgb >> 16);
            g[v + 13] = (byte) (rgb >> 8);
            b[v + 13] = (byte) rgb;
        }
        IndexColorModel model = new IndexColorModel(5, 27, r, g, b);
        BufferedImage out = new BufferedImage(src.getWidth() * k, src.getHeight() * k,
                BufferedImage.TYPE_BYTE_INDEXED, model);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x / k, y / k));      // an exact palette colour: its own index
            }
        }
        return out;
    }

    /** An endlessly repeating animated GIF, with javax.imageio only. */
    private static void writeGif(List<BufferedImage> frames, File file, int delayHundredths) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Files.deleteIfExists(file.toPath());
        ImageWriter w = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file)) {
            w.setOutput(out);
            w.prepareWriteSequence(null);
            boolean first = true;
            for (BufferedImage img : frames) {
                IIOMetadata meta = w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), null);
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("transparentColorIndex", "0");
                gce.setAttribute("delayTime", Integer.toString(delayHundredths));
                if (first) {
                    // The "NETSCAPE2.0" extension: loop forever (0 repeats = endless).
                    IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0});
                    child(root, "ApplicationExtensions").appendChild(app);
                    first = false;
                }
                meta.setFromTree(format, root);
                w.writeToSequence(new IIOImage(img, null, meta), null);
            }
            w.endWriteSequence();
        } finally {
            w.dispose();
        }
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
