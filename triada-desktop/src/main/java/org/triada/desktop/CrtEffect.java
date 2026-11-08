package org.triada.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.triada.core.video.VideoChip;

/**
 * The optional CRT look (M11, D-036): a shader program that the SpriteBatch
 * uses instead of its default one while the TRIADA's picture is drawn. The
 * GLSL source is in {@code src/main/resources/shaders/crt.vert} and
 * {@code crt.frag}; the fragment shader explains each effect.
 *
 * <p>Only the drawing of the picture changes: the machine, its colours and
 * every test stay exactly as they were. If the graphics card can't compile
 * the shader, the effect stays off and the error is logged.
 */
final class CrtEffect {

    /** How much the glass bulges (0 = flat). */
    static final float CURVATURE = 0.035f;
    /** How dark the gaps between picture rows are (0 = none, 1 = black). */
    static final float SCANLINES = 0.35f;

    private final ShaderProgram program;

    /** Compiles the shader; needs a running libGDX application (an OpenGL context). */
    CrtEffect() {
        ShaderProgram.pedantic = false;       // libGDX would refuse uniforms the compiler optimised away
        ShaderProgram p = new ShaderProgram(source("crt.vert"), source("crt.frag"));
        if (!p.isCompiled()) {
            Gdx.app.error("triada", "CRT shader not available:\n" + p.getLog());
            p.dispose();
            p = null;
        }
        program = p;
    }

    /** True if the shader compiled and can be used. */
    boolean available() {
        return program != null;
    }

    /** The shader, for {@code SpriteBatch.setShader}; null if unavailable. */
    ShaderProgram program() {
        return program;
    }

    /** Sets the shader's settings; call between {@code batch.begin()} and drawing. */
    void setUniforms() {
        program.setUniformf("u_sourceSize", VideoChip.FRAME_WIDTH, VideoChip.FRAME_HEIGHT);
        program.setUniformf("u_curvature", CURVATURE);
        program.setUniformf("u_scanlines", SCANLINES);
    }

    void dispose() {
        if (program != null) {
            program.dispose();
        }
    }

    /** A shader's GLSL source from the classpath ({@code /shaders/NAME}). */
    static String source(String name) {
        try (InputStream in = CrtEffect.class.getResourceAsStream("/shaders/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing resource /shaders/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
