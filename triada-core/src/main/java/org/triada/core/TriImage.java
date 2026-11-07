package org.triada.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.triada.core.ternary.Word;

/**
 * A program image: a block of words and the address where it belongs. This is
 * what a {@code .tri} file ("triskette" file) holds (SPEC §9.5):
 *
 * <pre>
 *   bytes 0–7      ASCII "TRIADA85"     (the "magic" that identifies the format)
 *   int32 LE       load address
 *   int32 LE       word count N
 *   N × int32 LE   the words
 * </pre>
 *
 * <p>"int32 LE" means a 4-byte signed integer, least significant byte first.
 * Converting to and from bytes is pure computation; reading and writing the
 * actual files is the job of the CLI and the frontends.
 */
public final class TriImage {

    /** The first eight bytes of every {@code .tri} file. */
    public static final String MAGIC = "TRIADA85";

    private static final int HEADER_BYTES = 16;

    private final int loadAddress;
    private final int[] words;

    /**
     * @throws IllegalArgumentException if an address or word is outside the
     *     word range, or the block runs past the top of the address range
     */
    public TriImage(int loadAddress, int[] words) {
        if (!Word.isValid(loadAddress)) {
            throw new IllegalArgumentException("load address " + loadAddress + " is not a word");
        }
        if ((long) loadAddress + words.length - 1 > Word.MAX) {
            throw new IllegalArgumentException(words.length + " words at " + loadAddress
                    + " run past the last address " + Word.MAX);
        }
        for (int i = 0; i < words.length; i++) {
            if (!Word.isValid(words[i])) {
                throw new IllegalArgumentException("word " + i + " = " + words[i] + " is not a word");
            }
        }
        this.loadAddress = loadAddress;
        this.words = words.clone();
    }

    public int loadAddress() {
        return loadAddress;
    }

    /** A copy of the words. */
    public int[] words() {
        return words.clone();
    }

    public int size() {
        return words.length;
    }

    /** The word at {@code index} (0 = the word at the load address). */
    public int word(int index) {
        return words[index];
    }

    /** The image as the bytes of a {@code .tri} file. */
    public byte[] toBytes() {
        byte[] out = new byte[HEADER_BYTES + 4 * words.length];
        byte[] magic = MAGIC.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, out, 0, magic.length);
        putInt(out, 8, loadAddress);
        putInt(out, 12, words.length);
        for (int i = 0; i < words.length; i++) {
            putInt(out, HEADER_BYTES + 4 * i, words[i]);
        }
        return out;
    }

    /**
     * Reads the bytes of a {@code .tri} file.
     *
     * @throws IllegalArgumentException with a readable reason if the bytes are
     *     not a valid {@code .tri} file
     */
    public static TriImage fromBytes(byte[] bytes) {
        if (bytes.length < HEADER_BYTES) {
            throw new IllegalArgumentException("not a .tri file: only " + bytes.length
                    + " bytes, the header alone is " + HEADER_BYTES);
        }
        String magic = new String(bytes, 0, 8, StandardCharsets.US_ASCII);
        if (!magic.equals(MAGIC)) {
            throw new IllegalArgumentException("not a .tri file: it starts with \"" + magic
                    + "\" instead of \"" + MAGIC + "\"");
        }
        int load = getInt(bytes, 8);
        int count = getInt(bytes, 12);
        if (count < 0 || (long) HEADER_BYTES + 4L * count != bytes.length) {
            throw new IllegalArgumentException("broken .tri file: header says " + count
                    + " words, but the file has room for " + (bytes.length - HEADER_BYTES) / 4.0);
        }
        int[] words = new int[count];
        for (int i = 0; i < count; i++) {
            words[i] = getInt(bytes, HEADER_BYTES + 4 * i);
        }
        return new TriImage(load, words);
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    private static int getInt(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | b[at + 3] << 24;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TriImage t && t.loadAddress == loadAddress && Arrays.equals(t.words, words);
    }

    @Override
    public int hashCode() {
        return 31 * loadAddress + Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        return "TriImage[" + words.length + " words at " + loadAddress + "]";
    }
}
