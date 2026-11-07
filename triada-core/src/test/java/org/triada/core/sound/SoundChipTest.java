package org.triada.core.sound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.cpu.CpuBenchmark;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/**
 * The sound chip (SPEC §7). At 441 Hz one period is exactly 100 samples
 * (44,100 / 441), so each quarter is exactly 25 samples: that makes every
 * expected value easy to work out by hand.
 */
class SoundChipTest {

    private final SoundChip chip = new SoundChip();

    /** Sets voice v (0, 1, 2) through its three registers. */
    private void voice(int v, int freq, int vol, int wave) {
        chip.write(IoRegisters.V1_FREQ + 3 * v, freq);
        chip.write(IoRegisters.V1_VOL + 3 * v, vol);
        chip.write(IoRegisters.V1_WAVE + 3 * v, wave);
    }

    private short[] samples(int n) {
        short[] s = new short[n];
        chip.generate(s, 0, n);
        return s;
    }

    @Test
    void timingConstants() {
        assertEquals(44_100, SoundChip.SAMPLE_RATE);
        assertEquals(735, SoundChip.SAMPLES_PER_FRAME, "44,100 / 60");
        assertEquals(735 * 60, SoundChip.SAMPLE_RATE);
    }

    @Test
    void registersReadBackAndSitAtTheSpecAddresses() {
        Machine m = new Machine();
        int base = MemoryMap.IO_BASE;
        m.bus().write(base + IoRegisters.V1_FREQ, 440);       // -20,402
        m.bus().write(base + IoRegisters.V2_VOL, 99);         // -20,398
        m.bus().write(base + IoRegisters.V3_WAVE, -1);        // -20,394
        assertEquals(-20_402, base + IoRegisters.V1_FREQ);
        assertEquals(-20_394, base + IoRegisters.V3_WAVE);
        assertEquals(440, m.bus().read(base + IoRegisters.V1_FREQ));
        assertEquals(99, m.bus().read(base + IoRegisters.V2_VOL), "the register keeps the word; only playback clamps");
        assertEquals(-1, m.bus().read(base + IoRegisters.V3_WAVE));
    }

    @Test
    void powerOnIsSilent() {
        for (short s : samples(2000)) {
            if (s != 0) {
                fail("power-on sound must be silent");
            }
        }
    }

    @Test
    void zeroFrequencyOrVolumeIsSilent() {
        voice(0, 0, 13, 1);
        voice(1, 440, 0, 1);
        voice(2, -440, 13, 1);                 // negative frequency: silent too
        for (short s : samples(1000)) {
            assertEquals(0, s);
        }
    }

    @Test
    void ternarySquareStepsPlusZeroMinusZeroInQuarters() {
        voice(0, 441, 13, SoundChip.WAVE_SQUARE);
        short[] s = samples(200);
        for (int i = 0; i < 200; i++) {
            int quarter = i % 100 / 25;
            int expected = quarter == 0 ? 10_000 : quarter == 2 ? -10_000 : 0;
            assertEquals(expected, s[i], "sample " + i);
        }
    }

    @Test
    void triangleRisesFallsAndRises() {
        voice(0, 441, 13, SoundChip.WAVE_TRIANGLE);
        short[] s = samples(100);
        assertEquals(0, s[0]);
        assertEquals(10_000, s[25], "peak at a quarter period");
        assertEquals(0, s[50]);
        assertEquals(-10_000, s[75]);
        assertEquals(4_000, s[10], "straight line: 10/25 of the way up");
        assertEquals(-4_000, s[90]);
        for (int i = 0; i < 50; i++) {
            assertEquals(s[i], -s[i + 50], "second half mirrors the first, sample " + i);
        }
    }

    @Test
    void volumeScalesLinearlyAndIsClamped() {
        voice(0, 441, 6, SoundChip.WAVE_SQUARE);
        assertEquals(10_000 * 6 / 13, samples(1)[0]);
        chip.reset();
        voice(0, 441, 99, SoundChip.WAVE_SQUARE);
        assertEquals(10_000, samples(1)[0], "VOL above 13 counts as 13");
        chip.reset();
        voice(0, 441, -5, SoundChip.WAVE_SQUARE);
        assertEquals(0, samples(1)[0], "VOL below 0 counts as 0");
    }

    @Test
    void threeVoicesAddUpWithoutClipping() {
        voice(0, 441, 13, SoundChip.WAVE_SQUARE);
        voice(1, 441, 13, SoundChip.WAVE_SQUARE);
        voice(2, 441, 13, SoundChip.WAVE_SQUARE);
        short[] s = samples(100);
        assertEquals(30_000, s[0]);
        assertEquals(-30_000, s[50]);
        chip.reset();
        voice(0, 441, 13, SoundChip.WAVE_SQUARE);
        voice(1, 441, 13, SoundChip.WAVE_TRIANGLE);
        assertEquals(10_000 + 4_000, samples(11)[10]);
    }

    @Test
    void frequencyIsExactOverASecond() {
        // Count rising steps from 0 to + of the square: one per period.
        voice(0, 440, 13, SoundChip.WAVE_SQUARE);
        short[] s = samples(SoundChip.SAMPLE_RATE);
        int periods = 0;
        for (int i = 1; i < s.length; i++) {
            if (s[i] > 0 && s[i - 1] == 0) {
                periods++;
            }
        }
        assertEquals(440 - 1, periods, "440 periods per second; the first starts at sample 0, not after a step");
    }

    @Test
    void phaseContinuesAcrossFrames() {
        voice(0, 523, 13, SoundChip.WAVE_TRIANGLE);
        voice(1, 330, 9, SoundChip.WAVE_SQUARE);
        short[] whole = samples(735 * 3);
        chip.reset();
        voice(0, 523, 13, SoundChip.WAVE_TRIANGLE);
        voice(1, 330, 9, SoundChip.WAVE_SQUARE);
        short[] pieces = new short[735 * 3];
        for (int f = 0; f < 3; f++) {
            chip.renderFrame();
            System.arraycopy(chip.frameSamples(), 0, pieces, 735 * f, 735);
        }
        assertArrayEquals(whole, pieces, "frame by frame = all at once: no clicks at frame edges");
    }

    @Test
    void noiseUsesThreeLevelsChangingOnlyAtQuarters() {
        voice(0, 441, 13, SoundChip.WAVE_NOISE);
        short[] s = samples(44_100);
        int[] count = new int[3];
        for (int i = 0; i < s.length; i++) {
            int level = s[i] / 10_000;                     // -1, 0, +1
            assertEquals(level * 10_000, s[i], "only three levels");
            count[level + 1]++;
            if (i % 25 != 0) {
                assertEquals(s[i - 1], s[i], "level changes only at a quarter boundary, sample " + i);
            }
        }
        for (int c : count) {
            assertTrue(c > 44_100 / 3 - 2_000 && c < 44_100 / 3 + 2_000, "each level about a third: " + c);
        }
    }

    @Test
    void noiseIsRepeatable() {
        voice(0, 1000, 13, SoundChip.WAVE_NOISE);
        short[] a = samples(5000);
        chip.reset();
        voice(0, 1000, 13, SoundChip.WAVE_NOISE);
        assertArrayEquals(a, samples(5000));
    }

    @Test
    void waveValuesBeyondOneFoldToTheLowestTrit() {
        assertEquals(-1, SoundChip.foldWave(2));
        assertEquals(0, SoundChip.foldWave(3));
        assertEquals(1, SoundChip.foldWave(-2));
        voice(0, 441, 13, 4);                               // 4 folds to +1: square
        assertEquals(10_000, samples(1)[0]);
    }

    @Test
    void theMachineGeneratesOneFrameOfSamplesPerFrame() {
        Machine m = new Machine();
        m.bus().write(MemoryMap.IO_BASE + IoRegisters.V2_FREQ, 441);
        m.bus().write(MemoryMap.IO_BASE + IoRegisters.V2_VOL, 13);
        m.bus().write(MemoryMap.IO_BASE + IoRegisters.V2_WAVE, 1);
        m.runFrame();
        short[] s = m.sound().frameSamples();
        assertEquals(735, s.length);
        assertEquals(10_000, s[0]);
        assertEquals(0, s[25]);
        m.powerOn();
        m.runFrame();
        assertEquals(0, m.sound().frameSamples()[0], "power-on silences the chip");
    }

    @Test
    void generatingAllocatesNothing() {
        voice(0, 523, 13, SoundChip.WAVE_TRIANGLE);
        voice(1, 330, 9, SoundChip.WAVE_SQUARE);
        voice(2, 2000, 5, SoundChip.WAVE_NOISE);
        for (int i = 0; i < 200; i++) {
            chip.renderFrame();
        }
        long before = CpuBenchmark.allocatedBytes();
        for (int i = 0; i < 600; i++) {
            chip.renderFrame();
        }
        assertTrue(CpuBenchmark.allocatedBytes() - before < 10_000);
    }
}
