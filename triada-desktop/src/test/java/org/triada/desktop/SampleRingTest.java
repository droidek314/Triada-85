package org.triada.desktop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SampleRingTest {

    private static short[] seq(int from, int count) {
        short[] s = new short[count];
        for (int i = 0; i < count; i++) {
            s[i] = (short) (from + i);
        }
        return s;
    }

    @Test
    void samplesComeOutInOrder() {
        SampleRing ring = new SampleRing(10);
        ring.write(seq(1, 4), 0, 4);
        ring.write(seq(5, 3), 0, 3);
        short[] out = new short[7];
        assertEquals(7, ring.read(out, 0, 7));
        assertArrayEquals(seq(1, 7), out);
        assertEquals(0, ring.size());
    }

    @Test
    void whenFullTheOldestSamplesMakeWay() {
        SampleRing ring = new SampleRing(5);
        ring.write(seq(1, 8), 0, 8);               // 1..8 into room for 5
        assertEquals(5, ring.size());
        assertEquals(3, ring.dropped());
        short[] out = new short[5];
        ring.read(out, 0, 5);
        assertArrayEquals(seq(4, 5), out, "the newest five survive: shortest delay");
    }

    @Test
    void whenEmptyTheLastSampleIsHeldNotZero() {
        SampleRing ring = new SampleRing(10);
        ring.write(new short[] {100, 200, 300}, 0, 3);
        short[] out = new short[6];
        assertEquals(3, ring.read(out, 0, 6));
        assertArrayEquals(new short[] {100, 200, 300, 300, 300, 300}, out, "no jump to 0: no click");
        assertEquals(1, ring.underruns());
        short[] more = new short[2];
        assertEquals(0, ring.read(more, 0, 2));
        assertArrayEquals(new short[] {300, 300}, more);
    }

    @Test
    void wrapsAroundManyTimes() {
        SampleRing ring = new SampleRing(7);
        short[] out = new short[3];
        int next = 0;
        int expected = 0;
        for (int round = 0; round < 1000; round++) {
            ring.write(seq(next, 3), 0, 3);
            next += 3;
            ring.read(out, 0, 3);
            for (short s : out) {
                assertEquals((short) expected++, s);
            }
        }
    }

    @Test
    void twoThreadsLoseNothingWhenThereIsRoom() throws Exception {
        // A writer and a reader at the same time: every sample arrives once, in order.
        // The ring holds everything, so even a writer that races far ahead drops nothing.
        int total = 200_000;
        SampleRing ring = new SampleRing(total);
        Thread writer = new Thread(() -> {
            for (int i = 0; i < total; i += 735) {
                int n = Math.min(735, total - i);
                ring.write(seq(i, n), 0, n);
            }
        });
        writer.start();
        short[] block = new short[512];
        int received = 0;
        while (received < total) {
            int n = ring.read(block, 0, Math.min(512, total - received));
            for (int i = 0; i < n; i++) {
                assertEquals((short) (received + i), block[i]);
            }
            received += n;
        }
        writer.join();
        assertEquals(0, ring.dropped());
    }
}
