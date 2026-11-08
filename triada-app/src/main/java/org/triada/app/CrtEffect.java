package org.triada.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import org.triada.core.video.VideoChip;

/**
 * The optional CRT look (M11, D-036): a shader program that the SpriteBatch
 * uses instead of its default one while the TRIADA's picture is drawn.
 *
 * <p>The GLSL source is kept right here as text ({@link #VERTEX},
 * {@link #FRAGMENT}) rather than in resource files, so neither the desktop nor
 * the browser has to load a file to get it (M12, D-040).
 *
 * <p>Only the drawing of the picture changes: the machine, its colours and
 * every test stay exactly as they were. If the graphics card can't compile
 * the shader, the effect stays off and the error is logged.
 */
public final class CrtEffect {

    /** How much the glass bulges (0 = flat). */
    static final float CURVATURE = 0.035f;
    /** How dark the gaps between picture rows are (0 = none, 1 = black). */
    static final float SCANLINES = 0.35f;

    /** Vertex part: the same as libGDX's default SpriteBatch shader; it passes colour and texture position on. */
    static final String VERTEX = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;

            void main() {
                v_color = a_color;
                v_color.a = v_color.a * (255.0 / 254.0);
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;

    /**
     * Fragment part, run once for every pixel of the window. It makes the sharp
     * picture look a little like a 1985 television:
     * <ol>
     * <li>curvature: the glass bulges, so the picture is bent slightly outwards;</li>
     * <li>scanlines: each of the machine's 168 picture rows is bright in its middle
     *     and darker at its top and bottom edge;</li>
     * <li>phosphors: window pixel columns are tinted red, green, blue in turn, like
     *     the stripes of a colour picture tube;</li>
     * <li>vignette: the corners are a little darker.</li>
     * </ol>
     * The picture itself is still read with nearest-neighbour filtering, so the
     * TRIADA's pixels keep their exact colours in the middle of each row.
     */
    static final String FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif

            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            uniform vec2 u_sourceSize;      // the machine's picture: 240 x 168
            uniform float u_curvature;      // 0.0 = flat glass
            uniform float u_scanlines;      // 0.0 = none ... 1.0 = black gaps

            void main() {
                vec2 p = v_texCoords * 2.0 - 1.0;                   // -1 ... +1 across the picture
                p *= 1.0 + u_curvature * (p.yx * p.yx);             // 1. bend outwards, more at the edges
                vec2 uv = p * 0.5 + 0.5;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);        // bent past the edge: the black tube rim
                    return;
                }
                vec3 colour = texture2D(u_texture, uv).rgb;

                float row = fract(uv.y * u_sourceSize.y);           // 2. where in its picture row this pixel is
                float scan = 1.0 - u_scanlines * (1.0 - sin(row * 3.14159265));

                float column = mod(gl_FragCoord.x, 3.0);            // 3. red, green, blue stripes
                vec3 phosphor = column < 1.0 ? vec3(1.0, 0.85, 0.85)
                              : column < 2.0 ? vec3(0.85, 1.0, 0.85)
                              :                vec3(0.85, 0.85, 1.0);

                float vignette = 1.0 - 0.18 * dot(p, p);            // 4. darker corners

                // The effects darken the picture, so brighten it again a little.
                gl_FragColor = vec4(min(colour * scan * phosphor * vignette * 1.3, 1.0), 1.0) * v_color;
            }
            """;

    private final ShaderProgram program;

    /** Compiles the shader; needs a running libGDX application (an OpenGL context). */
    public CrtEffect() {
        ShaderProgram.pedantic = false;       // libGDX would refuse uniforms the compiler optimised away
        ShaderProgram p = new ShaderProgram(VERTEX, FRAGMENT);
        if (!p.isCompiled()) {
            Gdx.app.error("triada", "CRT shader not available:\n" + p.getLog());
            p.dispose();
            p = null;
        }
        program = p;
    }

    /** True if the shader compiled and can be used. */
    public boolean available() {
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

    public void dispose() {
        if (program != null) {
            program.dispose();
        }
    }
}
