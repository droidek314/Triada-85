package org.triada.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.triada.core.Machine;
import org.triada.core.MachineInfo;
import org.triada.core.TriImage;
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
 * due; the {@link Debugger} runs them (or not, while paused). The picture is an
 * {@code int[]} from triada-core, copied into a texture with nearest-neighbour
 * filtering and drawn at a whole-number scale.
 *
 * <p>M11 adds an optional CRT look ({@link CrtEffect}, F2) and a capture mode
 * (--capture): run a number of frames, draw the picture into an off-screen
 * buffer, save it as a PNG and quit.
 *
 * <p>Keys: F1 shows or hides the debugger, F2 the CRT look. While it is shown, F5 = run/pause,
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

    private SpriteBatch batch;
    private Pixmap pixmap;
    private Texture texture;
    private final FrameClock clock = new FrameClock(Machine.FRAMES_PER_SECOND, MAX_CATCH_UP);
    private InputMapper inputMapper;
    private SoundOutput sound;      // null if no audio device could be opened
    private DebuggerView view;      // created the first time F1 is pressed
    private boolean debuggerVisible;
    private int viewWidth;
    private int viewHeight;
    private final LaunchOptions options;
    private CrtEffect crt;          // created in create(); unavailable if the shader didn't compile
    private boolean crtOn;

    /** @param machine a machine with its ROM, possibly with a program set to autorun */
    public TriadaApp(Machine machine, String title, LaunchOptions options) {
        this.machine = machine;
        this.debugger = new Debugger(machine);
        this.baseTitle = title;
        this.options = options;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        pixmap = new Pixmap(VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, Pixmap.Format.RGBA8888);
        texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        Gdx.graphics.setTitle(baseTitle);

        inputMapper = new InputMapper(machine.input());
        updateInput();
        crt = new CrtEffect();
        crtOn = options.crt() && crt.available();
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
        if (keycode == Input.Keys.F1) {
            showDebugger(!debuggerVisible);
            return true;
        }
        if (keycode == Input.Keys.F2) {
            crtOn = !crtOn && crt.available();
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
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        if (debuggerVisible) {
            view.resize(width, height, VideoChip.FRAME_HEIGHT * debuggerScale() + GAP);
        }
    }

    /** The screen's scale beside the debugger: the room left of the panel, above the memory viewer. */
    private int debuggerScale() {
        return scaleFor(viewWidth - DebuggerView.PANEL_WIDTH - 2 * GAP, viewHeight - DebuggerView.MEMORY_HEIGHT);
    }

    @Override
    public void render() {
        if (options.capture() != null) {
            capture();
            return;
        }
        int due = clock.advance(Gdx.graphics.getDeltaTime());
        boolean wasPaused = debugger.isPaused();
        int frames = debugger.runFrames(due);
        if (!wasPaused && debugger.isPaused() && !debuggerVisible) {
            showDebugger(true);         // a breakpoint or a fault: show where we are
        }
        if (frames > 0 || debuggerVisible) {
            upload(machine.video().frame());
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        int scale;
        float x;
        float y;
        if (debuggerVisible) {
            scale = debuggerScale();
            x = GAP;
            y = viewHeight - GAP - VideoChip.FRAME_HEIGHT * scale;
        } else {
            scale = scaleFor(viewWidth, viewHeight);
            x = (viewWidth - VideoChip.FRAME_WIDTH * scale) / 2f;
            y = (viewHeight - VideoChip.FRAME_HEIGHT * scale) / 2f;
        }
        drawPicture(x, y, scale);
        if (debuggerVisible) {
            view.refresh();
            view.draw(Gdx.graphics.getDeltaTime());
        }
    }

    /** Draws the machine's picture at (x, y), scaled, with the CRT look if it is on. */
    private void drawPicture(float x, float y, int scale) {
        batch.setShader(crtOn ? crt.program() : null);
        batch.begin();
        if (crtOn) {
            crt.setUniforms();
        }
        // No flip: libGDX shows a Pixmap upright (its row 0 at the top).
        batch.draw(texture, x, y, VideoChip.FRAME_WIDTH * scale, VideoChip.FRAME_HEIGHT * scale, 0, 0,
                VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, false, false);
        batch.end();
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
        upload(machine.video().frame());
        int scale = LaunchOptions.CAPTURE_SCALE;
        int w = VideoChip.FRAME_WIDTH * scale;
        int h = VideoChip.FRAME_HEIGHT * scale;
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        drawPicture(0, 0, scale);
        Pixmap shot = Pixmap.createFromFrameBuffer(0, 0, w, h);
        fbo.end();
        fbo.dispose();
        try {
            Capture.writePng(shot, options.capture());
            Gdx.app.log("triada", "saved " + options.capture() + " (" + w + " x " + h
                    + (crtOn ? ", CRT" : "") + ")");
        } catch (IOException e) {
            Gdx.app.error("triada", "can't write " + options.capture() + ": " + e.getMessage());
        }
        shot.dispose();
        Gdx.app.exit();
    }

    /** The largest whole-number scale at which the picture fits, at least 1. */
    static int scaleFor(int width, int height) {
        int s = Math.min(width / VideoChip.FRAME_WIDTH, height / VideoChip.FRAME_HEIGHT);
        return Math.max(1, s);
    }

    /** Copies 0xRRGGBB pixels into the RGBA pixmap bytes and uploads them. */
    private void upload(int[] frame) {
        ByteBuffer bytes = pixmap.getPixels();
        bytes.clear();
        for (int rgb : frame) {
            bytes.put((byte) (rgb >> 16));
            bytes.put((byte) (rgb >> 8));
            bytes.put((byte) rgb);
            bytes.put((byte) 0xFF);
        }
        bytes.flip();
        texture.draw(pixmap, 0, 0);
    }

    /** The window lost focus: key-up events would be missed, so let go of every key. */
    void focusLost() {
        if (inputMapper != null) {
            inputMapper.releaseAll();
        }
    }

    /**
     * A file was dropped on the window: "insert the triskette". The machine
     * powers on and boots, then runs the program (AUTORUN).
     */
    void insertTriskette(Path file) {
        try {
            TriImage image = DesktopLauncher.load(file.toString());
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
        if (crt != null) {
            crt.dispose();
        }
        texture.dispose();
        pixmap.dispose();
        batch.dispose();
    }
}
