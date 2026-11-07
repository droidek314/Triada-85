package org.triada.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.nio.ByteBuffer;
import org.triada.core.Machine;
import org.triada.core.sound.SoundChip;
import org.triada.core.video.VideoChip;

/**
 * The libGDX application: it runs the machine at 60 frames per second and
 * shows its picture.
 *
 * <p>libGDX calls {@link #render()} once per screen refresh, which may be 60,
 * 75, 144 … times per second. The machine must run at exactly 60 frames per
 * second (SPEC §6), so a {@link FrameClock} decides how many machine frames
 * are due on each call: a <em>fixed time step</em>.
 *
 * <p>The video chip produces the picture as an {@code int[]} (in
 * triada-core). This class only copies it into a {@link Pixmap}, uploads that
 * to a {@link Texture} with nearest-neighbour filtering (crisp pixels, no
 * blur), and draws it at the largest whole-number scale that fits the window.
 *
 * <p>Since M7 it also forwards keyboard events to an {@link InputMapper} and
 * each frame's samples to a {@link SoundOutput}.
 */
public class TriadaApp extends ApplicationAdapter {

    /** Never try to catch up more than this many frames (e.g. after the window was dragged). */
    private static final int MAX_CATCH_UP = 4;

    private final Machine machine;
    private final String title;

    private SpriteBatch batch;
    private Pixmap pixmap;
    private Texture texture;
    private final FrameClock clock = new FrameClock(Machine.FRAMES_PER_SECOND, MAX_CATCH_UP);
    private InputMapper inputMapper;
    private SoundOutput sound;      // null if no audio device could be opened
    private int viewWidth;
    private int viewHeight;

    /** @param machine a machine, already powered on and possibly with a program loaded */
    public TriadaApp(Machine machine, String title) {
        this.machine = machine;
        this.title = title;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        pixmap = new Pixmap(VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, Pixmap.Format.RGBA8888);
        texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        Gdx.graphics.setTitle(title);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        inputMapper = new InputMapper(machine.input());
        Gdx.input.setInputProcessor(new InputAdapter() {
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

        try {
            sound = new SoundOutput(Gdx.audio.newAudioDevice(SoundChip.SAMPLE_RATE, true));
        } catch (RuntimeException e) {
            // No sound card, or audio disabled: the machine still runs, silently.
            Gdx.app.error("triada", "no audio output: " + e.getMessage());
            sound = null;
        }
    }

    /** The window lost focus: key-up events would be missed, so let go of every key. */
    void focusLost() {
        if (inputMapper != null) {
            inputMapper.releaseAll();
        }
    }

    @Override
    public void resize(int width, int height) {
        // Work in real screen pixels (the back buffer), so integer scaling stays
        // exact even when the OS scales windows (e.g. Windows at 125 %).
        viewWidth = Gdx.graphics.getBackBufferWidth();
        viewHeight = Gdx.graphics.getBackBufferHeight();
        Gdx.gl.glViewport(0, 0, viewWidth, viewHeight);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, viewWidth, viewHeight);
    }

    @Override
    public void render() {
        int due = clock.advance(Gdx.graphics.getDeltaTime());
        for (int i = 0; i < due; i++) {
            machine.runFrame();
            if (sound != null) {
                sound.submit(machine.sound().frameSamples());
            }
        }
        if (due > 0) {
            upload(machine.video().frame());
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        int scale = scaleFor(viewWidth, viewHeight);
        int w = VideoChip.FRAME_WIDTH * scale;
        int h = VideoChip.FRAME_HEIGHT * scale;
        batch.begin();
        // Centred. No flip: libGDX shows a Pixmap upright (its row 0 at the top),
        // even though OpenGL counts y from the bottom (M7 fixed a wrong flip here).
        batch.draw(texture, (viewWidth - w) / 2f, (viewHeight - h) / 2f, w, h, 0, 0,
                VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, false, false);
        batch.end();
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

    @Override
    public void dispose() {
        if (sound != null) {
            sound.dispose();
        }
        texture.dispose();
        pixmap.dispose();
        batch.dispose();
    }
}
