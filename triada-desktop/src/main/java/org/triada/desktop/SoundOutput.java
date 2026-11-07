package org.triada.desktop;

import com.badlogic.gdx.audio.AudioDevice;
import org.triada.core.sound.SoundChip;

/**
 * Plays the sound chip's samples through libGDX's {@link AudioDevice} (raw
 * PCM) on a separate audio thread (CLAUDE.md M7).
 *
 * <p>Why a separate thread: {@code AudioDevice.writeSamples} <em>blocks</em>
 * until the sound card has room. If the render thread called it, every wait
 * would stall the picture, and every late frame would starve the sound card,
 * which gives a stutter. With the {@link SampleRing} in between, the render thread
 * only drops samples into the ring, and the audio thread is the one that waits.
 * The sound card's steady appetite paces it.
 */
final class SoundOutput {

    /** Samples per write to the device: 512 = 11.6 ms. */
    static final int BLOCK = 512;
    /** Most samples that may wait in the ring: 4 frames = 67 ms. */
    static final int RING_CAPACITY = 4 * SoundChip.SAMPLES_PER_FRAME;

    private final AudioDevice device;
    private final SampleRing ring = new SampleRing(RING_CAPACITY);
    private final Thread thread;
    private volatile boolean running = true;

    SoundOutput(AudioDevice device) {
        this.device = device;
        thread = new Thread(this::play, "triada-audio");
        thread.setDaemon(true);   // never keeps the program alive on its own
        thread.start();
    }

    /** Called on the render thread after every machine frame. */
    void submit(short[] frameSamples) {
        ring.write(frameSamples, 0, frameSamples.length);
    }

    private void play() {
        short[] block = new short[BLOCK];
        while (running) {
            ring.read(block, 0, BLOCK);
            device.writeSamples(block, 0, BLOCK);   // blocks until the sound card has room
        }
    }

    /** Stops the audio thread and releases the device. */
    void dispose() {
        running = false;
        try {
            thread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        device.dispose();
    }

    SampleRing ring() {
        return ring;
    }
}
