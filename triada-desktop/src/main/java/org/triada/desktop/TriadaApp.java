package org.triada.desktop;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/**
 * The libGDX application. In M0 it only paints the window one plain color,
 * which proves that the window, OpenGL and the render loop work.
 *
 * <p>From M6 on, {@code render()} will run one machine frame in triada-core
 * and upload the resulting pixels to a texture.
 */
public class TriadaApp extends ApplicationAdapter {

    @Override
    public void render() {
        // Dark gray-blue, like a switched-on monitor before anything is drawn.
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }
}
