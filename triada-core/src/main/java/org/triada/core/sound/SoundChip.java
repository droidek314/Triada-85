package org.triada.core.sound;

import org.triada.core.io.IoDevice;
import org.triada.core.io.IoRegisters;

/**
 * The sound chip (SPEC §7): three voices, each with a frequency, a volume and
 * one of three waveforms, mixed into one stream of 16-bit samples.
 *
 * <p>It owns I/O registers V1_FREQ … V3_WAVE (k = 10 … 18), three per voice,
 * which hold the last word written. Once per frame the machine calls
 * {@link #renderFrame()}, which turns the current register values into
 * {@link #SAMPLES_PER_FRAME} samples (DECISIONS.md D-023). The host plays them
 * (desktop) or saves them (CLI). triada-core itself never touches a sound card.
 *
 * <p><b>Exact phase.</b> Each voice keeps its position in the wave as an
 * integer {@code phase} from 0 to 44,099, meaning "phase / 44,100 of a period".
 * Every sample adds FREQ and wraps at 44,100. That is exact: a 441 Hz tone
 * repeats every 100 samples forever, with no rounding drift.
 *
 * <p><b>Levels.</b> A voice at full volume swings between −{@link #VOICE_PEAK}
 * and +{@link #VOICE_PEAK}; three voices together stay within ±30,000, below
 * the 16-bit limit of 32,767, so mixing never clips.
 */
public final class SoundChip implements IoDevice {

    /** Samples per second. */
    public static final int SAMPLE_RATE = 44_100;
    /** Samples per machine frame: 44,100 / 60. */
    public static final int SAMPLES_PER_FRAME = SAMPLE_RATE / 60;   // 735
    /** Number of voices. */
    public static final int VOICES = 3;
    /** Highest volume (SPEC §7). */
    public static final int MAX_VOLUME = 13;
    /** Output level of one voice at full volume. */
    public static final int VOICE_PEAK = 10_000;

    /** Waveform numbers (SPEC §7). */
    public static final int WAVE_NOISE = -1;
    public static final int WAVE_TRIANGLE = 0;
    public static final int WAVE_SQUARE = 1;

    /** First I/O register (V1_FREQ) and count (3 voices × FREQ, VOL, WAVE). */
    public static final int FIRST_REGISTER = IoRegisters.V1_FREQ;
    public static final int REGISTER_COUNT = 3 * VOICES;

    /** Default noise seed; any non-zero value works. */
    public static final int NOISE_SEED = 0x7A1ADA;

    private final int[] freq = new int[VOICES];
    private final int[] vol = new int[VOICES];
    private final int[] wave = new int[VOICES];

    private final int[] phase = new int[VOICES];        // 0 .. SAMPLE_RATE - 1
    private final int[] quarter = new int[VOICES];      // quarter of the period (0..3), -1 = none yet
    private final int[] noiseTrit = new int[VOICES];    // current noise level: -1, 0, +1
    private int noiseState = NOISE_SEED;

    private final short[] frameSamples = new short[SAMPLES_PER_FRAME];

    /** A silent sound chip in power-on state. */
    public SoundChip() {
        reset();
    }

    // ================================================================ registers

    @Override
    public int read(int k) {
        int v = (k - FIRST_REGISTER) / 3;
        switch ((k - FIRST_REGISTER) % 3) {
            case 0:
                return freq[v];
            case 1:
                return vol[v];
            default:
                return wave[v];
        }
    }

    @Override
    public void write(int k, int value) {
        int v = (k - FIRST_REGISTER) / 3;
        switch ((k - FIRST_REGISTER) % 3) {
            case 0:
                freq[v] = value;
                break;
            case 1:
                vol[v] = value;
                break;
            default:
                wave[v] = value;
                break;
        }
    }

    /** Power-on: all registers 0 (silence), oscillators and noise generator restarted. */
    public void reset() {
        for (int v = 0; v < VOICES; v++) {
            freq[v] = 0;
            vol[v] = 0;
            wave[v] = 0;
            phase[v] = 0;
            quarter[v] = -1;                 // so noise draws its first trit at once
            noiseTrit[v] = 0;
        }
        noiseState = NOISE_SEED;
        java.util.Arrays.fill(frameSamples, (short) 0);
    }

    // ================================================================ samples

    /** Generates this frame's {@link #SAMPLES_PER_FRAME} samples; read them with {@link #frameSamples()}. */
    public void renderFrame() {
        generate(frameSamples, 0, SAMPLES_PER_FRAME);
    }

    /** The samples of the last frame (a buffer that is reused; copy it if you keep it). */
    public short[] frameSamples() {
        return frameSamples;
    }

    /**
     * Generates {@code count} samples into {@code out} starting at {@code offset},
     * continuing every voice exactly where the previous call stopped.
     */
    public void generate(short[] out, int offset, int count) {
        for (int i = 0; i < count; i++) {
            long mix = 0;
            for (int v = 0; v < VOICES; v++) {
                mix += (long) level(v) * effectiveVolume(v);
            }
            // mix is in units of 1/SAMPLE_RATE of full scale, times volume 0..13.
            out[offset + i] = (short) (mix * VOICE_PEAK / ((long) SAMPLE_RATE * MAX_VOLUME));
        }
    }

    /**
     * The current level of voice v as a fraction of full scale, in units of
     * 1/44,100 (so ±44,100 is the peak), then advances its phase by one sample.
     */
    private int level(int v) {
        int f = freq[v];
        if (f <= 0) {
            return 0;                       // FREQ 0 (or negative) = silent
        }
        int p = phase[v];
        int q = (int) (4L * p / SAMPLE_RATE);                         // quarter 0..3
        int value;
        switch (foldWave(wave[v])) {
            case WAVE_SQUARE:
                // The ternary square: +1, 0, -1, 0 in four equal quarters.
                value = q == 0 ? SAMPLE_RATE : q == 2 ? -SAMPLE_RATE : 0;
                break;
            case WAVE_TRIANGLE: {
                // 0 -> +1 -> 0 -> -1 -> 0, straight lines.
                long x = 4L * p;                                        // 0 .. 4*SAMPLE_RATE
                long t = x < SAMPLE_RATE ? x
                        : x < 3L * SAMPLE_RATE ? 2L * SAMPLE_RATE - x
                        : x - 4L * SAMPLE_RATE;
                value = (int) t;
                break;
            }
            default: {
                // Noise: a fresh random trit at every quarter of the period, so
                // FREQ sets the "pitch" (graininess) of the noise.
                if (q != quarter[v]) {
                    noiseTrit[v] = nextRandomTrit();
                }
                value = noiseTrit[v] * SAMPLE_RATE;
                break;
            }
        }
        quarter[v] = q;
        p += f % SAMPLE_RATE;               // one sample later; FREQ above 44,100 just wraps
        if (p >= SAMPLE_RATE) {
            p -= SAMPLE_RATE;
        }
        phase[v] = p;
        return value;
    }

    /** VOL clamped to 0 … 13 (SPEC §7). */
    private int effectiveVolume(int v) {
        return Math.max(0, Math.min(MAX_VOLUME, vol[v]));
    }

    /** WAVE folded to its lowest trit, like BORDER (DECISIONS.md D-023): 2 behaves as -1. */
    static int foldWave(int w) {
        return Math.floorMod(w + 1, 3) - 1;
    }

    /** xorshift32, then mod 3: a random trit. Deterministic from the seed, for repeatable tests. */
    private int nextRandomTrit() {
        int x = noiseState;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        noiseState = x;
        return (int) ((x & 0xFFFFFFFFL) % 3) - 1;
    }
}
