package org.triada.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.nio.ByteBuffer;
import org.triada.core.video.VideoChip;

/**
 * Shows the machine's picture: the {@code int[]} from triada-core is copied
 * into a texture with nearest-neighbour filtering and drawn at a whole-number
 * scale, optionally through the {@link CrtEffect}. Shared by the desktop and
 * the web frontend (M12), so both show exactly the same picture.
 *
 * <p>Create it inside libGDX's {@code create()}: it needs an OpenGL context.
 */
public final class MachineScreen {

    private final SpriteBatch batch = new SpriteBatch();
    private final Pixmap pixmap = new Pixmap(VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT, Pixmap.Format.RGBA8888);
    private final Texture texture = new Texture(pixmap);
    private final CrtEffect crt = new CrtEffect();
    private boolean crtOn;

    public MachineScreen(boolean crtOn) {
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        this.crtOn = crtOn && crt.available();
    }

    /** True while the CRT look is on. */
    public boolean crtOn() {
        return crtOn;
    }

    /** Switches the CRT look on or off (F2); stays off if the shader isn't available. */
    public void toggleCrt() {
        crtOn = !crtOn && crt.available();
    }

    /** The drawing area is now {@code width} x {@code height} pixels, origin bottom left. */
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /** Copies 0xRRGGBB pixels into the RGBA pixmap bytes and uploads them. */
    public void upload(int[] frame) {
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

    /** Clears to black and draws the picture as large as fits, centred in a {@code width} x {@code height} area. */
    public void drawCentred(int width, int height) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        int scale = scaleFor(width, height);
        draw((width - VideoChip.FRAME_WIDTH * scale) / 2f, (height - VideoChip.FRAME_HEIGHT * scale) / 2f, scale);
    }

    /** Draws the picture at (x, y), scaled, with the CRT look if it is on. */
    public void draw(float x, float y, int scale) {
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

    /** The largest whole-number scale at which the picture fits, at least 1. */
    public static int scaleFor(int width, int height) {
        int s = Math.min(width / VideoChip.FRAME_WIDTH, height / VideoChip.FRAME_HEIGHT);
        return Math.max(1, s);
    }

    public void dispose() {
        crt.dispose();
        texture.dispose();
        pixmap.dispose();
        batch.dispose();
    }
}
