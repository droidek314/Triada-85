package org.triada.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.triada.core.ternary.Word;

class TriImageTest {

    @Test
    void byteLayoutFollowsSpec() {
        byte[] b = new TriImage(243, new int[] {1, -2}).toBytes();
        assertEquals(16 + 8, b.length);
        assertEquals("TRIADA85", new String(b, 0, 8, java.nio.charset.StandardCharsets.US_ASCII));
        // 243 = 0x000000F3, least significant byte first
        assertArrayEquals(new byte[] {(byte) 0xF3, 0, 0, 0}, java.util.Arrays.copyOfRange(b, 8, 12));
        assertArrayEquals(new byte[] {2, 0, 0, 0}, java.util.Arrays.copyOfRange(b, 12, 16));
        assertArrayEquals(new byte[] {1, 0, 0, 0}, java.util.Arrays.copyOfRange(b, 16, 20));
        // −2 = 0xFFFFFFFE
        assertArrayEquals(new byte[] {(byte) 0xFE, -1, -1, -1}, java.util.Arrays.copyOfRange(b, 20, 24));
    }

    @Test
    void roundTripThroughBytes() {
        TriImage image = new TriImage(-19_683, new int[] {Word.MIN, 0, Word.MAX, 12_345});
        TriImage back = TriImage.fromBytes(image.toBytes());
        assertEquals(image, back);
        assertEquals(-19_683, back.loadAddress());
        assertEquals(4, back.size());
        assertEquals(Word.MAX, back.word(2));
    }

    @Test
    void emptyImageIsJustTheHeader() {
        assertEquals(16, new TriImage(243, new int[0]).toBytes().length);
    }

    @Test
    void badFilesAreRejectedWithAReason() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TriImage.fromBytes("NOTATRIFILE12345".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertTrue(e.getMessage().contains("not a .tri file"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> TriImage.fromBytes(new byte[5]));

        byte[] truncated = new TriImage(0, new int[] {1, 2, 3}).toBytes();
        byte[] cut = java.util.Arrays.copyOf(truncated, truncated.length - 4);
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> TriImage.fromBytes(cut));
        assertTrue(e2.getMessage().contains("header says 3 words"), e2.getMessage());

        byte[] badWord = new TriImage(0, new int[] {0}).toBytes();
        badWord[16] = 0;
        badWord[17] = 0;
        badWord[18] = 0x10;   // 0x00100000 = 1,048,576: not a word
        assertThrows(IllegalArgumentException.class, () -> TriImage.fromBytes(badWord));
    }

    @Test
    void imagesMustFitTheAddressRange() {
        assertThrows(IllegalArgumentException.class, () -> new TriImage(Word.MAX, new int[2]));
        assertThrows(IllegalArgumentException.class, () -> new TriImage(Word.MAX + 1, new int[0]));
        new TriImage(Word.MAX, new int[1]);   // exactly fits
    }

    @Test
    void wordsAreCopiedSoTheImageCannotChange() {
        int[] w = {1, 2};
        TriImage image = new TriImage(0, w);
        w[0] = 99;
        image.words()[1] = 99;
        assertArrayEquals(new int[] {1, 2}, image.words());
    }
}
