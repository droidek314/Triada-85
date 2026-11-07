package org.triada.desktop;

/**
 * Turns real elapsed time into a whole number of machine frames, so the
 * machine runs at exactly 60 frames per second whatever the monitor's refresh
 * rate is: a <em>fixed time step</em>.
 *
 * <p>Each call adds the time since the last screen refresh to an
 * "accumulator" and takes out as many whole frames as it holds. A 144 Hz
 * monitor gets a machine frame on most refreshes but not all; a 30 Hz one gets
 * two per refresh. To avoid a burst of catch-up frames after a long pause
 * (for example while the window is dragged), very long steps are capped.
 */
final class FrameClock {

    private final double frameTime;
    private final double maxStep;
    private double accumulator;

    /**
     * @param framesPerSecond the machine's frame rate
     * @param maxCatchUp      the most frames one call may return
     */
    FrameClock(int framesPerSecond, int maxCatchUp) {
        this.frameTime = 1.0 / framesPerSecond;
        this.maxStep = maxCatchUp * frameTime;
    }

    /** Adds {@code seconds} of real time; returns how many machine frames are now due. */
    int advance(double seconds) {
        accumulator += Math.min(Math.max(seconds, 0), maxStep);
        int frames = (int) (accumulator / frameTime);
        accumulator -= frames * frameTime;
        return frames;
    }
}
