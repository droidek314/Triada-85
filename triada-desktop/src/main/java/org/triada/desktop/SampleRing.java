package org.triada.desktop;

/**
 * A thread-safe waiting room for sound samples between the two threads of the
 * desktop frontend:
 * <ul>
 * <li>the <b>render thread</b> runs the machine and {@link #write}s each frame's
 *     735 samples;</li>
 * <li>the <b>audio thread</b> {@link #read}s fixed-size blocks and hands them to
 *     the sound card, which takes exactly 44,100 per second.</li>
 * </ul>
 * The two clocks (screen refresh and sound card) never agree perfectly, so the
 * buffer has two safety rules:
 * <ul>
 * <li><b>Too full</b> (the machine ran ahead, e.g. catching up after a pause):
 *     the oldest samples are dropped, which keeps the delay between key press
 *     and sound short.</li>
 * <li><b>Empty</b> (the machine fell behind): the last sample value is
 *     repeated instead of jumping to 0, because a sudden jump is an audible click.</li>
 * </ul>
 * Like the KEY queue, it is a ring buffer: a fixed array whose ends chase each
 * other, so nothing is allocated while sound plays.
 */
final class SampleRing {

    private final short[] buffer;
    private int head;        // oldest sample
    private int size;        // samples waiting
    private short last;      // the most recently read sample, repeated on underrun
    private long dropped;    // statistics, for the docs and tests
    private long underruns;

    /** @param capacity the most samples that may wait; this is the maximum added delay */
    SampleRing(int capacity) {
        buffer = new short[capacity];
    }

    /** Adds samples; if there's no room, the oldest waiting samples make way. */
    synchronized void write(short[] src, int offset, int length) {
        for (int i = 0; i < length; i++) {
            if (size == buffer.length) {
                head = (head + 1) % buffer.length;
                size--;
                dropped++;
            }
            buffer[(head + size) % buffer.length] = src[offset + i];
            size++;
        }
    }

    /**
     * Takes exactly {@code length} samples into {@code dst}. If fewer are
     * waiting, the rest are copies of the last sample.
     *
     * @return how many real samples were available
     */
    synchronized int read(short[] dst, int offset, int length) {
        int n = Math.min(length, size);
        for (int i = 0; i < n; i++) {
            last = buffer[head];
            dst[offset + i] = last;
            head = (head + 1) % buffer.length;
        }
        size -= n;
        if (n < length) {
            underruns++;
            for (int i = n; i < length; i++) {
                dst[offset + i] = last;
            }
        }
        return n;
    }

    synchronized int size() {
        return size;
    }

    synchronized long dropped() {
        return dropped;
    }

    synchronized long underruns() {
        return underruns;
    }
}
