package org.triada.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.triada.core.MachineInfo;

/**
 * Starts the desktop frontend: opens a window and hands control to libGDX.
 *
 * <p>libGDX runs a loop for us: about 60 times per second it calls
 * {@link TriadaApp#render()}. Everything the machine shows goes through there.
 */
public final class DesktopLauncher {

    /**
     * Temporary window size: the 216 x 144 screen plus a 12-pixel border on
     * each side, scaled 4x. The real border size is decided in M6 (video).
     */
    static final int WINDOW_WIDTH = (216 + 2 * 12) * 4;   // 960
    static final int WINDOW_HEIGHT = (144 + 2 * 12) * 4;  // 672

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(MachineInfo.NAME);
        config.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        config.setForegroundFPS(60);  // the machine runs at 60 frames per second (SPEC §6)
        config.useVsync(true);
        new Lwjgl3Application(new TriadaApp(), config);
    }
}
