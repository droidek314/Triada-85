package org.triada.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
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

/**
 * Starts the desktop frontend: builds a machine, optionally loads a program,
 * opens a window and hands control to libGDX.
 *
 * <pre>
 *   ./gradlew :triada-desktop:run                                   (empty machine)
 *   ./gradlew :triada-desktop:run --args="programs/colors.tasm"     (with a program)
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
        Machine machine = new Machine();
        String title = MachineInfo.NAME;
        if (args.length > 0) {
            try {
                machine.loadProgram(load(args[0]));
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
        new Lwjgl3Application(new TriadaApp(machine, title), config);
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
