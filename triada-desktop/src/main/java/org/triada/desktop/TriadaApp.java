package org.triada.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.nio.ByteBuffer;
import org.triada.core.Machine;
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
        // Centred; the texture's row 0 is the top of the picture, so flip it vertically.
        batch.draw(texture, (viewWidth - w) / 2f, (viewHeight - h) / 2f, w, h, 0, 0,
                VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, false, true);
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
        texture.dispose();
        pixmap.dispose();
        batch.dispose();
    }
}
