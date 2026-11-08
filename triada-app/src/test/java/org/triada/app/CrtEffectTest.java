package org.triada.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The shader itself needs a graphics card; what can be checked without one is that its names match the code. */
class CrtEffectTest {

    @Test
    void theCrtShaderDeclaresWhatTheCodeSets() {
        for (String uniform : new String[] {"u_sourceSize", "u_curvature", "u_scanlines", "u_texture"}) {
            assertTrue(CrtEffect.FRAGMENT.contains("uniform vec2 " + uniform)
                    || CrtEffect.FRAGMENT.contains("uniform float " + uniform)
                    || CrtEffect.FRAGMENT.contains("uniform sampler2D " + uniform), uniform);
        }
        // SpriteBatch feeds these names (libGDX's ShaderProgram constants).
        for (String attribute : new String[] {"a_position", "a_color", "a_texCoord0", "u_projTrans"}) {
            assertTrue(CrtEffect.VERTEX.contains(attribute), attribute);
        }
        // WebGL (GLSL ES) refuses a fragment shader without a default float precision.
        assertTrue(CrtEffect.FRAGMENT.contains("precision mediump float;"));
    }
}
