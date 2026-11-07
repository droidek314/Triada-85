package org.triada.core.video;

import org.triada.core.io.IoDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.Bus;
import org.triada.core.memory.MemoryMap;
import org.triada.core.ternary.Word;

/**
 * The video chip (SPEC §4). Once per frame it reads the tile patterns, the
 * tile map and the sprite table from RAM and paints a picture: a 216 × 144
 * screen inside a {@link #BORDER}-pixel border, as an array of RGB pixels.
 *
 * <p>It also owns the I/O registers SCROLL_X, SCROLL_Y and BORDER (k = 7 … 9),
 * which simply hold the last word written to them.
 *
 * <p>Rendering happens in three steps, each into a preallocated buffer, so a
 * frame allocates nothing:
 * <ol>
 * <li><b>Background:</b> decode all 27 × 18 map cells into {@link #bg}, a
 *     216 × 144 array of pixel values (−13 … 13).</li>
 * <li><b>Screen:</b> copy the background into {@link #screen} with the scroll
 *     offset, wrapping around at the edges; then draw sprites 0 → 26 on top.</li>
 * <li><b>Output:</b> turn pixel values into RGB with {@link Palette} and
 *     surround the screen with the border colour, giving {@link #frame()}.</li>
 * </ol>
 */
public final class VideoChip implements IoDevice {

    /** Visible screen size in pixels (SPEC §4.1). */
    public static final int SCREEN_WIDTH = 216;
    public static final int SCREEN_HEIGHT = 144;
    /** Border width on each side, in pixels (DECISIONS.md D-020). */
    public static final int BORDER = 12;
    /** Size of the whole picture, border included. */
    public static final int FRAME_WIDTH = SCREEN_WIDTH + 2 * BORDER;    // 240
    public static final int FRAME_HEIGHT = SCREEN_HEIGHT + 2 * BORDER;  // 168

    /** Tile size, tile map size, counts (SPEC §4.4, §4.5, §4.7). */
    public static final int TILE_SIZE = 8;
    public static final int MAP_COLUMNS = 27;
    public static final int MAP_ROWS = 18;
    public static final int TILE_COUNT = 243;
    public static final int WORDS_PER_TILE = 16;
    public static final int SPRITE_COUNT = 27;
    public static final int WORDS_PER_SPRITE = 4;

    /** First I/O register this chip occupies, and how many. */
    public static final int FIRST_REGISTER = IoRegisters.SCROLL_X;
    public static final int REGISTER_COUNT = 3;

    private final Bus bus;

    private int scrollX;
    private int scrollY;
    private int border;

    /** Background, pixel values, 216 × 144, row by row. */
    private final int[] bg = new int[SCREEN_WIDTH * SCREEN_HEIGHT];
    /** Screen after scrolling and sprites, pixel values. */
    private final int[] screen = new int[SCREEN_WIDTH * SCREEN_HEIGHT];
    /** The finished picture, 0xRRGGBB, 240 × 168. */
    private final int[] frame = new int[FRAME_WIDTH * FRAME_HEIGHT];
    /** One decoded tile, 8 × 8 pixel values (reused). */
    private final int[] tile = new int[TILE_SIZE * TILE_SIZE];

    /** A video chip that reads video memory through {@code bus}. */
    public VideoChip(Bus bus) {
        this.bus = bus;
        render();   // so frame() shows the power-on picture straight away
    }

    // ================================================================ registers

    @Override
    public int read(int k) {
        switch (k) {
            case IoRegisters.SCROLL_X:
                return scrollX;
            case IoRegisters.SCROLL_Y:
                return scrollY;
            case IoRegisters.BORDER:
                return border;
            default:
                return 0;
        }
    }

    @Override
    public void write(int k, int value) {
        switch (k) {
            case IoRegisters.SCROLL_X:
                scrollX = value;
                break;
            case IoRegisters.SCROLL_Y:
                scrollY = value;
                break;
            case IoRegisters.BORDER:
                border = value;
                break;
            default:
                break;
        }
    }

    /** Power-on: registers 0. */
    public void reset() {
        scrollX = 0;
        scrollY = 0;
        border = 0;
    }

    // ================================================================ rendering

    /** Draws a new picture from the current memory and registers (SPEC §6: once per frame). */
    public void render() {
        drawBackground();
        scrollAndCompose();
        for (int i = 0; i < SPRITE_COUNT; i++) {          // later sprites end up on top
            drawSprite(i);
        }
        output();
    }

    /** The last picture: {@link #FRAME_WIDTH} × {@link #FRAME_HEIGHT} pixels, 0xRRGGBB, row by row. */
    public int[] frame() {
        return frame;
    }

    /** The pixel VALUE (−13 … 13) at screen position (x, y) of the last picture; for tests and the debugger. */
    public int screenPixel(int x, int y) {
        return screen[y * SCREEN_WIDTH + x];
    }

    /** Step 1: every map cell's tile into the background buffer. */
    private void drawBackground() {
        for (int row = 0; row < MAP_ROWS; row++) {
            for (int col = 0; col < MAP_COLUMNS; col++) {
                int v = bus.peek(MemoryMap.MAP + row * MAP_COLUMNS + col);
                decodeTile(v);
                int base = row * TILE_SIZE * SCREEN_WIDTH + col * TILE_SIZE;
                for (int y = 0; y < TILE_SIZE; y++) {
                    System.arraycopy(tile, y * TILE_SIZE, bg, base + y * SCREEN_WIDTH, TILE_SIZE);
                }
            }
        }
    }

    /** Step 2a: screen pixel (x, y) shows background pixel ((x + SCROLL_X) mod 216, (y + SCROLL_Y) mod 144). */
    private void scrollAndCompose() {
        int sx = Math.floorMod(scrollX, SCREEN_WIDTH);
        int sy = Math.floorMod(scrollY, SCREEN_HEIGHT);
        for (int y = 0; y < SCREEN_HEIGHT; y++) {
            int bgRow = (y + sy) % SCREEN_HEIGHT * SCREEN_WIDTH;
            int out = y * SCREEN_WIDTH;
            // A row is the background row rotated left by sx: two straight copies.
            int first = SCREEN_WIDTH - sx;
            System.arraycopy(bg, bgRow + sx, screen, out, first);
            System.arraycopy(bg, bgRow, screen, out + first, sx);
        }
    }

    /** Step 2b: one sprite (SPEC §4.7). Gray (value 0) is transparent; pixels off the screen are skipped. */
    private void drawSprite(int i) {
        int base = MemoryMap.SPRITES + WORDS_PER_SPRITE * i;
        int flags = bus.peek(base + 3);
        if (flags != 1 && flags != -1) {
            return;                                       // 0 = hidden (other values too, D-021)
        }
        int x0 = bus.peek(base);
        int y0 = bus.peek(base + 1);
        decodeTile(bus.peek(base + 2));                   // negative = mirrored horizontally
        boolean flipY = flags == -1;
        for (int y = 0; y < TILE_SIZE; y++) {
            int sy = y0 + y;
            if (sy < 0 || sy >= SCREEN_HEIGHT) {
                continue;
            }
            int src = (flipY ? TILE_SIZE - 1 - y : y) * TILE_SIZE;
            for (int x = 0; x < TILE_SIZE; x++) {
                int sx = x0 + x;
                int v = tile[src + x];
                if (v != 0 && sx >= 0 && sx < SCREEN_WIDTH) {
                    screen[sy * SCREEN_WIDTH + sx] = v;
                }
            }
        }
    }

    /** Step 3: pixel values to RGB, and the border around them. */
    private void output() {
        int borderRgb = Palette.rgb(Palette.fold(border));
        java.util.Arrays.fill(frame, borderRgb);
        for (int y = 0; y < SCREEN_HEIGHT; y++) {
            int in = y * SCREEN_WIDTH;
            int out = (y + BORDER) * FRAME_WIDTH + BORDER;
            for (int x = 0; x < SCREEN_WIDTH; x++) {
                frame[out + x] = Palette.rgb(screen[in + x]);
            }
        }
    }

    /**
     * Decodes tile {@code |v|} into {@link #tile}, mirrored left-right if v is
     * negative (SPEC §4.5). Values beyond ±242 mean tile 0. Each row of a tile
     * is two words of four pixels; a word unpacks with the offset trick:
     * u = word + MAX has base-27 digits that are each pixel + 13 (SPEC §4.3).
     */
    private void decodeTile(int v) {
        boolean mirror = v < 0;
        int t = Math.abs(v);
        if (t >= TILE_COUNT) {
            t = 0;
        }
        int address = MemoryMap.TILES + WORDS_PER_TILE * t;
        for (int row = 0; row < TILE_SIZE; row++) {
            for (int half = 0; half < 2; half++) {
                int u = bus.peek(address + 2 * row + half) + Word.MAX;
                int x = half * 4;
                put(row, x, u / 19_683 - 13, mirror);           // pixel 0: t11..t9
                put(row, x + 1, u / 729 % 27 - 13, mirror);     // pixel 1: t8..t6
                put(row, x + 2, u / 27 % 27 - 13, mirror);      // pixel 2: t5..t3
                put(row, x + 3, u % 27 - 13, mirror);           // pixel 3: t2..t0
            }
        }
    }

    private void put(int row, int x, int value, boolean mirror) {
        tile[row * TILE_SIZE + (mirror ? TILE_SIZE - 1 - x : x)] = value;
    }
}
