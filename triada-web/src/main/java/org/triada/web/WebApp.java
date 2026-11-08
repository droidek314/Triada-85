package org.triada.web;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.teavm.jso.typedarrays.Int8Array;
import org.triada.app.FrameClock;
import org.triada.app.InputMapper;
import org.triada.app.MachineScreen;
import org.triada.assembler.Assembler;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.rom.Rom;

/**
 * The TRIADA-85 in a browser tab (M12): the same machine, ROM, picture, CRT
 * look and keyboard as the desktop, without the debugger (D-039).
 *
 * <p>Each refresh, the {@link FrameClock} says how many machine frames are
 * due; each frame's sound goes straight to the page's player. Programs come
 * from {@code ?program=NAME} (one of the bundled {@code programs/*.tasm},
 * assembled right here) or from a file dropped on the page: "insert a
 * triskette", as on the desktop.
 */
final class WebApp extends ApplicationAdapter {

    private static final int MAX_CATCH_UP = 4;

    private final Machine machine = Rom.newMachine();
    private final FrameClock clock = new FrameClock(Machine.FRAMES_PER_SECOND, MAX_CATCH_UP);
    private final InputMapper inputMapper = new InputMapper(machine.input());
    private MachineScreen screen;
    private int width;
    private int height;

    @Override
    public void create() {
        screen = new MachineScreen(false);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.F2) {
                    screen.toggleCrt();
                    return true;
                }
                if (keycode == Input.Keys.SPACE) {
                    // Catching Space (below) stops the page scrolling, but it also stops the
                    // browser's typed-character event, so the space is typed from here.
                    return inputMapper.keyTyped(' ');
                }
                return inputMapper.keyDown(keycode);
            }

            @Override
            public boolean keyUp(int keycode) {
                return inputMapper.keyUp(keycode);
            }

            @Override
            public boolean keyTyped(char character) {
                return inputMapper.keyTyped(character);
            }
        });
        // Keep the browser from scrolling the page or leaving it while the TRIADA uses these keys.
        for (int key : new int[] {Input.Keys.LEFT, Input.Keys.RIGHT, Input.Keys.UP, Input.Keys.DOWN,
                Input.Keys.SPACE, Input.Keys.F2}) {
            Gdx.input.setCatchKey(key, true);
        }

        String program = WebPage.programParameter();
        if (program.matches("[a-z0-9]+")) {
            insert(program + ".tasm", null);
        } else {
            WebPage.status("Type HELP and press Enter.");
        }
    }

    /**
     * Assembles or reads a program and runs it on a freshly switched-on
     * machine. {@code bytes} is the dropped file, or null for a bundled program.
     */
    private void insert(String name, byte[] bytes) {
        try {
            TriImage image = name.toLowerCase(Locale.ROOT).endsWith(".tasm")
                    ? new Assembler(path -> source(path, name, bytes)).assemble(name).image()
                    : TriImage.fromBytes(bytes);
            machine.powerOn();
            Rom.autorun(machine, image);
            WebPage.status("Running " + name + ".");
        } catch (Exception e) {
            WebPage.status("Can't load " + name + ": " + e.getMessage());
        }
    }

    /** Source text for the assembler: the dropped file itself, or a bundled one (for .include "lib/game.tasm"). */
    private static String source(String path, String droppedName, byte[] droppedBytes) {
        if (droppedBytes != null && path.equals(droppedName)) {
            return new String(droppedBytes, StandardCharsets.UTF_8);
        }
        FileHandle file = Gdx.files.internal(path);
        return file.exists() ? file.readString("UTF-8") : null;
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        Gdx.gl.glViewport(0, 0, width, height);
        screen.resize(width, height);
    }

    @Override
    public void render() {
        String dropped = WebPage.droppedName();
        if (dropped != null) {
            insert(dropped, toBytes(WebPage.takeDroppedBytes()));
        }
        int due = clock.advance(Gdx.graphics.getDeltaTime());
        for (int i = 0; i < due; i++) {
            machine.runFrame();
            WebPage.playSamples(machine.sound().frameSamples());
        }
        if (due > 0) {
            screen.upload(machine.video().frame());
        }
        screen.drawCentred(width, height);
    }

    private static byte[] toBytes(Int8Array array) {
        byte[] bytes = new byte[array.getLength()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = array.get(i);
        }
        return bytes;
    }

    @Override
    public void dispose() {
        screen.dispose();
    }
}
