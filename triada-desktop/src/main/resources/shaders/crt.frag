// CRT effect, fragment part (M11): makes the sharp picture look a little like
// a 1985 television. It runs once for every pixel of the window.
//   1. curvature:  the glass bulges, so the picture is bent slightly outwards;
//   2. scanlines:  each of the machine's 168 picture rows is bright in its
//                  middle and darker at its top and bottom edge;
//   3. phosphors:  window pixel columns are tinted red, green, blue in turn,
//                  like the stripes of a colour picture tube;
//   4. vignette:   the corners are a little darker.
// The picture itself is still read with nearest-neighbour filtering, so the
// TRIADA's pixels keep their exact colours in the middle of each row.
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
