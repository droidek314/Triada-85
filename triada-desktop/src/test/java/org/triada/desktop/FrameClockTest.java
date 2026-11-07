package org.triada.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The fixed time step and the integer scaling, the two pieces of display logic that need no window. */
class FrameClockTest {

    private static int run(FrameClock clock, double refreshRate, double seconds) {
        int frames = 0;
        int refreshes = (int) Math.round(refreshRate * seconds);
        for (int i = 0; i < refreshes; i++) {
            frames += clock.advance(1.0 / refreshRate);
        }
        return frames;
    }

    /**
     * Adding up thousands of tiny floating-point time steps lands a hair above
     * or below the exact total, so the last frame may be one refresh early or
     * late. What matters is that the error never grows: ±1 frame after ten
     * seconds AND after a whole hour.
     */
    @Test
    void sixtyFramesPerSecondOnAnyMonitorWithoutDrift() {
        for (double hz : new double[] {60, 144, 75, 30}) {
            int tenSeconds = run(new FrameClock(60, 4), hz, 10);
            assertTrue(Math.abs(tenSeconds - 600) <= 1, hz + " Hz: " + tenSeconds + " frames in 10 s");
            int oneHour = run(new FrameClock(60, 4), hz, 3600);
            assertTrue(Math.abs(oneHour - 216_000) <= 1, hz + " Hz: " + oneHour + " frames in 1 hour");
        }
    }

    @Test
    void longPausesDoNotCauseABurstOfFrames() {
        FrameClock clock = new FrameClock(60, 4);
        assertEquals(4, clock.advance(2.0), "a 2-second hiccup gives at most 4 frames");
        assertEquals(0, clock.advance(0.001));
        assertEquals(0, clock.advance(-1), "a negative time step (clock glitch) is ignored");
    }

    @Test
    void integerScaleFitsTheWindow() {
        assertEquals(4, TriadaApp.scaleFor(960, 672));
        assertEquals(3, TriadaApp.scaleFor(959, 672));
        assertEquals(3, TriadaApp.scaleFor(1920, 600), "height limits");
        assertEquals(8, TriadaApp.scaleFor(1920, 1344));
        assertEquals(1, TriadaApp.scaleFor(100, 100), "never smaller than 1");
    }
}
