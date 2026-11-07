package org.triada.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
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
 *   ./gradlew :triada-desktop:run                                     (boots to the monitor)
 *   ./gradlew :triada-desktop:run --args="--load programs/piano.tasm" (boots, then runs it)
 * </pre>
 *
 * A {@code .tasm} file is assembled first; a {@code .tri} file is loaded as is.
 * Dropping such a file on the window "inserts a triskette": the machine powers
 * on again and runs it (M9). F1 opens the debugger.
 */
public final class DesktopLauncher {

    /** Initial window size: the picture at 4x scale. */
    static final int WINDOW_WIDTH = VideoChip.FRAME_WIDTH * 4;     // 960
    static final int WINDOW_HEIGHT = VideoChip.FRAME_HEIGHT * 4;   // 672

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        String program = programArgument(args);
        Machine machine = Rom.newMachine();
        String title = MachineInfo.NAME;
        if (program != null) {
            try {
                Rom.autorun(machine, load(program));
                title = MachineInfo.NAME + " - " + Path.of(program).getFileName();
            } catch (IOException | AssemblyException | IllegalArgumentException e) {
                System.err.println("triada: can't load " + program + ":\n" + e.getMessage());
                System.exit(2);
            }
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(title);
        config.setWindowedMode(WINDOW_WIDTH, WINDOW_HEIGHT);
        // Window sizes and mouse positions in real pixels: keeps integer scaling and
        // the debugger's layout exact when the OS scales windows (e.g. 125 %).
        config.setHdpiMode(HdpiMode.Pixels);
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

            @Override
            public void filesDropped(String[] files) {
                if (files.length > 0) {
                    app.insertTriskette(Path.of(files[0]));   // one triskette at a time
                }
            }
        });
        new Lwjgl3Application(app, config);
    }

    /**
     * The program to start: {@code --load FILE}, or (as before M9) a plain file
     * name. Null if none.
     */
    static String programArgument(String[] args) {
        if (args.length == 0) {
            return null;
        }
        if (args[0].equals("--load")) {
            if (args.length < 2) {
                System.err.println("triada: --load needs a file, e.g. --load programs/piano.tasm");
                System.exit(2);
            }
            return args[1];
        }
        return args[0];
    }

    /** Reads a .tri file, or assembles a .tasm file. */
    static TriImage load(String file) throws IOException, AssemblyException {
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
