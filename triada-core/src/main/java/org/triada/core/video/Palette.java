package org.triada.core.video;

/**
 * TRIADA's 27 colours (SPEC §4.2). A pixel value is a 3-trit number from −13
 * to +13, and its trits <em>are</em> its colour: the high trit is red, the
 * middle one green, the low one blue. Each trit sets one channel's
 * brightness: −1 → 0 (off), 0 → 128 (half), +1 → 255 (full).
 *
 * <pre>
 *   value   trits   R    G    B
 *    −13    ---     0    0    0     black
 *      0    000   128  128  128     gray (transparent in sprites)
 *      5    +--   255    0    0     red
 *     13    +++   255  255  255     white
 * </pre>
 */
public final class Palette {

    /** Lowest pixel value. */
    public static final int MIN = -13;
    /** Highest pixel value. */
    public static final int MAX = 13;
    /** Number of colours. */
    public static final int COUNT = 27;

    /** Channel brightness for trit −1, 0, +1. */
    private static final int[] LEVEL = {0, 128, 255};

    /** {@code RGB[v + 13]} = 0xRRGGBB for pixel value v. */
    private static final int[] RGB = new int[COUNT];

    static {
        for (int v = MIN; v <= MAX; v++) {
            int u = v + 13;                 // offset trick: digits 0..2 = trits + 1
            int r = LEVEL[u / 9];
            int g = LEVEL[u / 3 % 3];
            int b = LEVEL[u % 3];
            RGB[u] = r << 16 | g << 8 | b;
        }
    }

    private Palette() {
    }

    /**
     * The colour of pixel value {@code v} as 0xRRGGBB.
     *
     * @throws ArrayIndexOutOfBoundsException if v is outside −13 … 13
     */
    public static int rgb(int v) {
        return RGB[v + 13];
    }

    /**
     * Folds any word into a pixel value by keeping only its lowest three
     * trits, the way a chip with three colour wires would (DECISIONS.md D-021).
     * Used for the BORDER register, which holds a whole word.
     */
    public static int fold(int value) {
        return Math.floorMod(value + 13, COUNT) - 13;
    }
}
