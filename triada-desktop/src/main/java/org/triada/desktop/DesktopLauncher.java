package org.triada.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.Assembler;
import org.triada.core.Machine;
import org.triada.core.MachineInfo;
import org.triada.core.TriImage;
import org.triada.core.video.VideoChip;
import org.triada.rom.Rom;

/**
 * Starts the desktop frontend: builds a machine with its ROM, optionally hands
 * it a program, opens a window and hands control to libGDX. The machine boots
 * like the real one: font, chime, boot screen, then the monitor, or the given
 * program, started through AUTORUN (D-028).
 *
 * <pre>
 *   ./gradlew :triada-desktop:run                                   (boots to the monitor)
 *   ./gradlew :triada-desktop:run --args="programs/colors.tasm"     (boots, then runs it)
 * </pre>
 *
 * A {@code .tasm} file is assembled first; a {@code .tri} file is loaded as is.
 * The full {@code --load} option and drag-and-drop arrive with the debugger (M9).
 */
public final class DesktopLauncher {

    /** Initial window size: the picture at 4x scale. */
    static final int WINDOW_WIDTH = VideoChip.FRAME_WIDTH * 4;     // 960
    static final int WINDOW_HEIGHT = VideoChip.FRAME_HEIGHT * 4;   // 672

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        Machine machine = Rom.newMachine();
        String title = MachineInfo.NAME;
        if (args.length > 0) {
            try {
                Rom.autorun(machine, load(args[0]));
                title = MachineInfo.NAME + " - " + Path.of(args[0]).getFileName();
            } catch (IOException | AssemblyException | IllegalArgumentException e) {
                System.err.println("triada: can't load " + args[0] + ":\n" + e.getMessage());
                System.exit(2);
            }
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(title);
        config.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        config.setForegroundFPS(0);   // no cap: render() keeps the machine at 60 Hz itself
        config.useVsync(true);
        // OpenAL: 16 sources (libGDX default), 512-sample buffers, 6 of them: about 70 ms of sound queued.
        config.setAudioConfig(16, 512, 6);
        TriadaApp app = new TriadaApp(machine, title);
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                app.focusLost();
            }
        });
        new Lwjgl3Application(app, config);
    }

    private static TriImage load(String file) throws IOException, AssemblyException {
        if (file.toLowerCase(java.util.Locale.ROOT).endsWith(".tasm")) {
            return new Assembler(path -> {
                try {
                    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            }).assemble(file).image();
        }
        return TriImage.fromBytes(Files.readAllBytes(Path.of(file)));
    }
}
