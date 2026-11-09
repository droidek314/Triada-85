package org.triada.net;

import org.triada.core.ternary.Word;

/**
 * TRIMODEM, the way TRIADA-NET moves programs down a phone line (NET-SPEC §3, M17).
 *
 * <p><b>Three characters per word.</b> A word has 3^12 = 531,441 values, and
 * 3^12 = 81^3. So every word is exactly three "digits" in base 81, and each
 * digit is sent as one printable character, 33 ('!') … 113 ('q'). The word
 * is first shifted to 0 … 531,440 by adding 265,720, the same offset trick as
 * the pixel packing (SPEC §4.3).
 *
 * <pre>
 *   word 1000:  1000 + 265,720 = 266,720 = 40·81² + 52·81 + 68
 *               digits 40, 52, 68  ->  characters 73 'I', 85 'U', 101 'e'
 * </pre>
 *
 * <p><b>Messages</b> (each starts with ESC, 27):
 * <pre>
 *   ESC 'F' name ';' length ';' type ';' address    start: the file (length, address: 3 chars each)
 *   ESC 'B' number data checksum                     a block: 3 + 243 + 3 characters, 81 words
 *   ESC 'E'                                          end
 *   ESC 'X'                                          abort
 * </pre>
 * The receiver answers each start, block and end with ACK (6) or NAK (21).
 * The fields have fixed sizes and are found by position: ';' (59) is itself
 * a digit character, so a length or address may contain one.
 * The last block is padded with 0 words; the length says how many are real.
 *
 * <p><b>Checksum</b> of block n with words w0 … w80: n + 1·w0 + 2·w1 + … +
 * 81·w80, wrapped to a word like any TRIADA sum. Weighting by position
 * catches two swapped words, which a plain sum would miss, and the machine
 * can compute it with MUL and ADD (DECISIONS.md D-063).
 */
public final class Trimodem {

    public static final int ESC = 27;
    public static final int ACK = 6;
    public static final int NAK = 21;
    /** Message letters after ESC. */
    public static final int START = 'F';
    public static final int BLOCK = 'B';
    public static final int END = 'E';
    public static final int ABORT = 'X';

    /** Words per block (3^4), and the characters they take. */
    public static final int BLOCK_WORDS = 81;
    public static final int BLOCK_CHARS = 3 * BLOCK_WORDS;
    /** The lowest character a digit uses; digit d is character FIRST_CHAR + d. */
    public static final int FIRST_CHAR = 33;
    public static final int LAST_CHAR = FIRST_CHAR + 80;
    /** Tries per message: the third failure aborts the transfer (NET-SPEC §3). */
    public static final int MAX_TRIES = 3;

    /** The two kinds of file: a TRIBASIC program (.TB) or machine code (.TRI). */
    public enum Type { TB, TRI }

    private Trimodem() {
    }

    /** Appends the three characters of {@code word}. */
    public static void encode(int word, StringBuilder out) {
        int u = word + Word.MAX;                // 0 ... 531,440
        out.append((char) (FIRST_CHAR + u / 6_561));
        out.append((char) (FIRST_CHAR + u / 81 % 81));
        out.append((char) (FIRST_CHAR + u % 81));
    }

    /** The three characters of {@code word}, as a string. */
    public static String encode(int word) {
        StringBuilder b = new StringBuilder(3);
        encode(word, b);
        return b.toString();
    }

    /**
     * The word whose three characters start at {@code at}.
     *
     * @throws IllegalArgumentException if one of them isn't a digit character (33 … 113)
     */
    public static int decode(CharSequence s, int at) {
        int u = 0;
        for (int i = 0; i < 3; i++) {
            int d = s.charAt(at + i) - FIRST_CHAR;
            if (d < 0 || d > 80) {
                throw new IllegalArgumentException("not a TRIMODEM digit: " + (int) s.charAt(at + i));
            }
            u = u * 81 + d;
        }
        return u - Word.MAX;
    }

    /** True if {@code c} is one of the 81 digit characters. */
    public static boolean isDigit(int c) {
        return c >= FIRST_CHAR && c <= LAST_CHAR;
    }

    /** The checksum of block {@code number}: number + Σ (i+1)·words[from+i], wrapped to a word. */
    public static int checksum(int number, int[] words, int from) {
        long sum = number;
        for (int i = 0; i < BLOCK_WORDS; i++) {
            sum += (long) (i + 1) * words[from + i];
        }
        return Word.wrap(sum);
    }

    /** Blocks needed for {@code length} words. */
    public static int blocks(int length) {
        return (length + BLOCK_WORDS - 1) / BLOCK_WORDS;
    }
}
