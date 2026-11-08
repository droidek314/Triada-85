package org.triada.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The desktop's command line (M9, M11), the capture's row order, and the CRT shader's source. */
class LaunchOptionsTest {

    @Test
    void noArgumentsBootsToTheMonitor() {
        LaunchOptions o = LaunchOptions.parse();
        assertNull(o.program());
        assertFalse(o.crt());
        assertNull(o.capture());
        assertEquals(LaunchOptions.DEFAULT_CAPTURE_FRAMES, o.captureFrames());
    }

    @Test
    void aProgramWithOrWithoutLoad() {
        assertEquals("programs/piano.tasm", LaunchOptions.parse("--load", "programs/piano.tasm").program());
        assertEquals("programs/piano.tasm", LaunchOptions.parse("programs/piano.tasm").program());
    }

    @Test
    void allOptionsTogether() {
        LaunchOptions o = LaunchOptions.parse("--crt", "--capture", "out/shot.png", "--frames", "300",
                "--load", "programs/oblaka.tasm");
        assertTrue(o.crt());
        assertEquals(Path.of("out/shot.png"), o.capture());
        assertEquals(300, o.captureFrames());
        assertEquals("programs/oblaka.tasm", o.program());
    }

    @Test
    void mistakesAreExplained() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--load"))
                .getMessage().contains("needs a file"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--capture"))
                .getMessage().contains("PNG"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--frames", "0"))
                .getMessage().contains("at least 1"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--frames", "many"))
                .getMessage().contains("many"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--colour"))
                .getMessage().contains("unknown option --colour"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("a.tasm", "b.tasm"))
                .getMessage().contains("one program"));
    }

    @Test
    void aCaptureTurnsOpenGlsBottomUpRowsTheRightWayRound() {
        // 1 x 2 pixels as OpenGL returns them: the bottom row (blue) first, then the top (red).
        ByteBuffer rgba = ByteBuffer.wrap(new byte[] {0, 0, (byte) 255, (byte) 255, (byte) 255, 0, 0, (byte) 255});
        BufferedImage image = Capture.toImage(rgba, 1, 2);
        assertEquals(0xFF0000, image.getRGB(0, 0) & 0xFFFFFF, "top: red");
        assertEquals(0x0000FF, image.getRGB(0, 1) & 0xFFFFFF, "bottom: blue");
    }

    @Test
    void theCrtShaderDeclaresWhatTheCodeSets() {
        String frag = CrtEffect.source("crt.frag");
        String vert = CrtEffect.source("crt.vert");
        for (String uniform : new String[] {"u_sourceSize", "u_curvature", "u_scanlines", "u_texture"}) {
            assertTrue(frag.contains("uniform") && frag.contains(uniform), uniform);
        }
        // SpriteBatch feeds these names (libGDX's ShaderProgram constants).
        for (String attribute : new String[] {"a_position", "a_color", "a_texCoord0", "u_projTrans"}) {
            assertTrue(vert.contains(attribute), attribute);
        }
        assertThrows(IllegalStateException.class, () -> CrtEffect.source("missing.frag"));
    }
}
