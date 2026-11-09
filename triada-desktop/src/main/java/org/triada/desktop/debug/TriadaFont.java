package org.triada.desktop.debug;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.triada.rom.Rom;

/**
 * The debugger's font: the TRIADA-85's own character set, taken from the
 * ROM (the font8x8 rows in rom/font.tasm), drawn at 2x. Every character is
 * equally wide, which keeps registers, trits and disassembly in neat columns,
 * and the debugger looks like part of the machine.
 *
 * <p>libGDX builds a {@link BitmapFont} from two things: a picture with all
 * glyphs (the "atlas") and a text description in the AngelCode BMFont format
 * saying where each glyph is. We make both in memory, so no asset files are
 * needed and libGDX's own, well-tested parser handles the font metrics.
 */
public final class TriadaFont {

    /** Pixels per font pixel. */
    static final int SCALE = 2;
    /** Size of one glyph cell in the atlas. */
    static final int CELL = 8 * SCALE;
    /** Glyphs per atlas row. */
    static final int COLUMNS = 16;
    static final int FIRST = 32;
    static final int LAST = 126;
    static final int LINE_HEIGHT = CELL + 2;

    private TriadaFont() {
    }

    /** The font, ready to use. Needs a running libGDX application (it creates a texture). */
    public static BitmapFont create() {
        Pixmap atlas = atlas();
        Texture texture = new Texture(atlas);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        atlas.dispose();
        BitmapFont font = new BitmapFont(new MemoryFile("triada.fnt", description()), new TextureRegion(texture), false);
        font.setUseIntegerPositions(true);
        return font;
    }

    /** The BMFont text description: one "char" line per glyph, in a 16-column grid. */
    static String description() {
        int rows = (LAST - FIRST + COLUMNS) / COLUMNS;
        StringBuilder sb = new StringBuilder();
        sb.append("info face=\"TRIADA\" size=").append(CELL)
                .append(" bold=0 italic=0 charset=\"\" unicode=0 stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=0,0\n");
        sb.append("common lineHeight=").append(LINE_HEIGHT).append(" base=").append(CELL)
                .append(" scaleW=").append(COLUMNS * CELL).append(" scaleH=").append(rows * CELL)
                .append(" pages=1 packed=0\n");
        sb.append("page id=0 file=\"triada.png\"\n");
        sb.append("chars count=").append(LAST - FIRST + 1).append('\n');
        for (int c = FIRST; c <= LAST; c++) {
            int i = c - FIRST;
            sb.append("char id=").append(c)
                    .append(" x=").append(i % COLUMNS * CELL)
                    .append(" y=").append(i / COLUMNS * CELL)
                    .append(" width=").append(CELL).append(" height=").append(CELL)
                    .append(" xoffset=0 yoffset=0 xadvance=").append(CELL)
                    .append(" page=0 chnl=0\n");
        }
        return sb.toString();
    }

    /** The glyph picture: white where a font bit is set, transparent elsewhere (labels tint it). */
    private static Pixmap atlas() {
        int rows = (LAST - FIRST + COLUMNS) / COLUMNS;
        Pixmap p = new Pixmap(COLUMNS * CELL, rows * CELL, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0);
        p.fill();
        p.setColor(1, 1, 1, 1);
        int[] font = fontRows();
        for (int c = FIRST; c <= LAST; c++) {
            int i = c - FIRST;
            for (int row = 0; row < 8; row++) {
                int bits = font[i * 8 + row];
                for (int x = 0; x < 8; x++) {
                    if ((bits >> x & 1) == 1) {                     // bit 0 = leftmost pixel
                        p.fillRectangle(i % COLUMNS * CELL + x * SCALE, i / COLUMNS * CELL + row * SCALE,
                                SCALE, SCALE);
                    }
                }
            }
        }
        return p;
    }

    /** The 760 font row values, read from the ROM image at its 'font' label. */
    static int[] fontRows() {
        int start = Rom.address("font") - Rom.image().loadAddress();
        int[] rows = new int[(LAST - FIRST + 1) * 8];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = Rom.image().word(start + i);
        }
        return rows;
    }

    /** A "file" that lives in memory: libGDX's font parser only needs read(). */
    static final class MemoryFile extends FileHandle {
        private final byte[] bytes;

        MemoryFile(String name, String text) {
            super(new File(name), FileType.Absolute);
            this.bytes = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream read() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
