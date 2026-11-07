package org.triada.desktop.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import org.junit.jupiter.api.Test;

/**
 * The font description is checked with libGDX's own parser (BitmapFontData
 * only parses text; it needs no window). The atlas picture needs OpenGL, so
 * it is checked by looking at the running debugger.
 */
class TriadaFontTest {

    @Test
    void libgdxParsesTheDescriptionIntoMonospacedGlyphs() {
        BitmapFont.BitmapFontData data =
                new BitmapFont.BitmapFontData(new TriadaFont.MemoryFile("t.fnt", TriadaFont.description()), false);
        for (char c = 32; c <= 126; c++) {
            BitmapFont.Glyph g = data.getGlyph(c);
            assertNotNull(g, "glyph " + (int) c);
            assertEquals(16, g.width);
            assertEquals(16, g.xadvance, "monospaced");
        }
        BitmapFont.Glyph a = data.getGlyph('A');           // 65 - 32 = 33: column 1, row 2
        assertEquals(16, a.srcX);
        assertEquals(32, a.srcY);
        assertEquals(-18, data.down, "18-pixel lines");
    }

    @Test
    void fontRowsComeFromTheRom() {
        int[] rows = TriadaFont.fontRows();
        assertEquals(760, rows.length);
        int a = ('A' - 32) * 8;
        assertEquals(0x0C, rows[a]);                       // font8x8's 'A': 0C 1E 33 33 3F 33 33 00
        assertEquals(0x3F, rows[a + 4]);
        assertEquals(0, rows[a + 7]);
    }
}
