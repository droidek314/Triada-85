package org.triada.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import java.io.IOException;
import java.nio.file.Path;
import org.triada.app.FrameClock;
import org.triada.app.InputMapper;
import org.triada.app.MachineScreen;
import org.triada.core.Machine;
import org.triada.core.MachineInfo;
import org.triada.core.TriImage;
import org.triada.core.io.KeyFeeder;
import org.triada.core.triskette.TrisketteException;
import org.triada.core.triskette.TrisketteNames;
import org.triada.core.sound.SoundChip;
import org.triada.core.video.VideoChip;
import org.triada.desktop.debug.Debugger;
import org.triada.desktop.debug.DebuggerView;

/**
 * The libGDX application: it runs the machine at 60 frames per second, shows
 * its picture, plays its sound, passes on the keyboard, and hosts the
 * debugger (M9).
 *
 * <p>libGDX calls {@link #render()} once per screen refresh (60, 75, 144 …
 * times per second). A {@link FrameClock} decides how many machine frames are
 * due; the {@link Debugger} runs them (or not, while paused). The picture is
 * drawn by {@link MachineScreen}, which the web version shares (M12).
 *
 * <p>M11 adds an optional CRT look ({@code CrtEffect}, F2) and a capture mode
 * (--capture): run a number of frames, draw the picture into an off-screen
 * buffer, save it as a PNG and quit.
 *
 * <p>Keys: F1 shows or hides the debugger, F2 the CRT look, Ctrl+V types the clipboard in (M15). While it is shown, F5 = run/pause,
 * F11 = step, F10 = step over. Everything else goes to the TRIADA (unless a
 * debugger text field has the keyboard).
 */
public class TriadaApp extends ApplicationAdapter {

    /** Never try to catch up more than this many frames (e.g. after the window was dragged). */
    private static final int MAX_CATCH_UP = 4;
    private static final int GAP = 8;

    private final Debugger debugger;
    private final Machine machine;
    private final String baseTitle;

    private MachineScreen screen;
    private KeyFeeder pasting;      // text from Ctrl+V still being typed, or null
    private final FrameClock clock = new FrameClock(Machine.FRAMES_PER_SECOND, MAX_CATCH_UP);
    private InputMapper inputMapper;
    private SoundOutput sound;      // null if no audio device could be opened
    private DebuggerView view;      // created the first time F1 is pressed
    private boolean debuggerVisible;
    private int viewWidth;
    private int viewHeight;
    private final LaunchOptions options;

    /** @param machine a machine with its ROM, possibly with a program set to autorun */
    public TriadaApp(Machine machine, String title, LaunchOptions options) {
        this.machine = machine;
        this.debugger = new Debugger(machine);
        this.baseTitle = title;
        this.options = options;
    }

    @Override
    public void create() {
        screen = new MachineScreen(options.crt());
        Gdx.graphics.setTitle(baseTitle);

        inputMapper = new InputMapper(machine.input());
        updateInput();
        if (options.capture() != null) {
            return;                     // no sound in a capture: see capture()
        }

        try {
            sound = new SoundOutput(Gdx.audio.newAudioDevice(SoundChip.SAMPLE_RATE, true));
        } catch (RuntimeException e) {
            // No sound card, or audio disabled: the machine still runs, silently.
            Gdx.app.error("triada", "no audio output: " + e.getMessage());
            sound = null;
        }
        debugger.setFrameListener(() -> {
            if (sound != null) {
                sound.submit(machine.sound().frameSamples());
            }
        });
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** Keys first to the debugger's F-keys, then (if shown) its panels, then the TRIADA. */
    private void updateInput() {
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                return functionKey(keycode);
            }
        });
        if (debuggerVisible) {
            mux.addProcessor(view.stage());
        }
        mux.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
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
        Gdx.input.setInputProcessor(mux);
    }

    private boolean functionKey(int keycode) {
        if (keycode == Input.Keys.V && (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))) {
            String text = Gdx.app.getClipboard().getContents();
            if (text != null) {
                pasting = new KeyFeeder(text);  // Ctrl+V: typed in, as fast as the queue takes it (M15)
            }
            return true;
        }
        if (keycode == Input.Keys.F1) {
            showDebugger(!debuggerVisible);
            return true;
        }
        if (keycode == Input.Keys.F2) {
            screen.toggleCrt();
            return true;
        }
        if (!debuggerVisible) {
            return false;
        }
        switch (keycode) {
            case Input.Keys.F5:
                debugger.togglePause();
                return true;
            case Input.Keys.F10:
                debugger.stepOver();
                return true;
            case Input.Keys.F11:
                debugger.step();
                return true;
            default:
                return false;
        }
    }

    private void showDebugger(boolean show) {
        if (show && view == null) {
            view = new DebuggerView(debugger);
        }
        debuggerVisible = show;
        updateInput();
        resize(viewWidth, viewHeight);
    }

    @Override
    public void resize(int width, int height) {
        // With HdpiMode.Pixels (see DesktopLauncher) these are real screen pixels,
        // so integer scaling stays exact even when the OS scales windows.
        viewWidth = width;
        viewHeight = height;
        Gdx.gl.glViewport(0, 0, width, height);
        screen.resize(width, height);
        if (debuggerVisible) {
            view.resize(width, height, VideoChip.FRAME_HEIGHT * debuggerScale() + GAP);
        }
    }

    /** The screen's scale beside the debugger: the room left of the panel, above the memory viewer. */
    private int debuggerScale() {
        return MachineScreen.scaleFor(viewWidth - DebuggerView.PANEL_WIDTH - 2 * GAP, viewHeight - DebuggerView.MEMORY_HEIGHT);
    }

    @Override
    public void render() {
        if (options.capture() != null) {
            capture();
            return;
        }
        if (pasting != null && !pasting.feed(machine.input())) {
            pasting = null;
        }
        int due = clock.advance(Gdx.graphics.getDeltaTime());
        boolean wasPaused = debugger.isPaused();
        int frames = debugger.runFrames(due);
        if (!wasPaused && debugger.isPaused() && !debuggerVisible) {
            showDebugger(true);         // a breakpoint or a fault: show where we are
        }
        if (frames > 0 || debuggerVisible) {
            screen.upload(machine.video().frame());
        }

        if (!debuggerVisible) {
            screen.drawCentred(viewWidth, viewHeight);
            return;
        }
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        int scale = debuggerScale();
        screen.draw(GAP, viewHeight - GAP - VideoChip.FRAME_HEIGHT * scale, scale);
        view.refresh();
        view.draw(Gdx.graphics.getDeltaTime());
    }

    /**
     * Capture mode: runs the requested frames at once, draws the picture into
     * an off-screen frame buffer (so the hidden window never has to be shown),
     * saves it and quits.
     */
    private void capture() {
        for (int i = 0; i < options.captureFrames(); i++) {
            machine.runFrame();
        }
        screen.upload(machine.video().frame());
        int scale = LaunchOptions.CAPTURE_SCALE;
        int w = VideoChip.FRAME_WIDTH * scale;
        int h = VideoChip.FRAME_HEIGHT * scale;
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        screen.resize(w, h);
        screen.draw(0, 0, scale);
        Pixmap shot = Pixmap.createFromFrameBuffer(0, 0, w, h);
        fbo.end();
        fbo.dispose();
        try {
            Capture.writePng(shot, options.capture());
            Gdx.app.log("triada", "saved " + options.capture() + " (" + w + " x " + h
                    + (screen.crtOn() ? ", CRT" : "") + ")");
        } catch (IOException e) {
            Gdx.app.error("triada", "can't write " + options.capture() + ": " + e.getMessage());
        }
        shot.dispose();
        Gdx.app.exit();
    }

    /** The window lost focus: key-up events would be missed, so let go of every key. */
    void focusLost() {
        if (inputMapper != null) {
            inputMapper.releaseAll();
        }
    }

    /**
     * A file was dropped on the window: "insert the triskette". It is copied
     * onto the triskette (M13), so it shows up in FILES. Machine code then
     * runs: the machine powers on and boots, then starts it (AUTORUN). A
     * TRIBASIC program ({@code .TB}) is only stored.
     */
    void insertTriskette(Path file) {
        try {
            TriImage image = DesktopLauncher.load(file.toString());
            String stored = TrisketteNames.forHostFile(file.getFileName().toString());
            try {
                machine.triskette().port().write(stored, image);
            } catch (TrisketteException e) {
                Gdx.app.error("triada", "can't store " + stored + " on the triskette: " + e.getMessage());
            }
            if (stored.endsWith(".TB")) {
                Gdx.graphics.setTitle(MachineInfo.NAME + " - " + stored + " is on the triskette");
                return;
            }
            debugger.insertTriskette(image, file.getFileName().toString());
            Gdx.graphics.setTitle(MachineInfo.NAME + " - " + file.getFileName());
        } catch (Exception e) {
            debugger.note("CAN'T LOAD " + file.getFileName() + ": " + e.getMessage());
            Gdx.app.error("triada", "can't load " + file + ": " + e.getMessage());
        }
    }

    @Override
    public void dispose() {
        if (sound != null) {
            sound.dispose();
        }
        if (view != null) {
            view.dispose();
        }
        screen.dispose();
    }
}
