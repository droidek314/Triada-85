import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-off converter (M8): turns font8x8_basic.h (public domain, Daniel Hepper)
 * into rom/font.tasm, keeping the printable ASCII characters 32 ... 126.
 *
 * Each character becomes 8 words, one per pixel row from top to bottom; each
 * word is the row's byte value 0 ... 255, where bit 0 is the LEFTMOST pixel,
 * exactly as in the original. The ROM expands these bits into tiles at boot.
 *
 * Run with the JDK alone (single-file source launch), from the project root:
 *   java rom/tools/FontToTasm.java rom/third_party/font8x8/font8x8_basic.h rom/font.tasm
 */
public class FontToTasm {

    public static void main(String[] args) throws Exception {
        String header = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        // Lines like:   { 0x0C, 0x1E, 0x33, 0x33, 0x3F, 0x33, 0x33, 0x00},   // U+0041 (A)
        // (Anchored on "0x", so the opening brace of the whole C array isn't taken for a row.)
        Pattern row = Pattern.compile("\\{\\s*(0x[^}]*)\\},\\s*// U\\+([0-9A-F]{4})");
        Matcher m = row.matcher(header);
        int[][] glyphs = new int[128][];
        while (m.find()) {
            int code = Integer.parseInt(m.group(2), 16);
            String[] bytes = m.group(1).split(",");
            int[] g = new int[8];
            for (int i = 0; i < 8; i++) {
                g[i] = Integer.decode(bytes[i].trim());
            }
            glyphs[code] = g;
        }

        List<String> out = new ArrayList<>();
        out.add("; ============================================================================");
        out.add(";  FONT.TASM -- the 8 x 8 character set, ASCII 32 ... 126 (95 characters)");
        out.add(";");
        out.add(";  GENERATED FILE: do not edit. Made by rom/tools/FontToTasm.java from");
        out.add(";  rom/third_party/font8x8/font8x8_basic.h:");
        out.add(";    font8x8 by Daniel Hepper <daniel@hepper.net>, License: Public Domain,");
        out.add(";    based on the public domain VGA fonts of International Business Machines");
        out.add(";    via Marcel Sondaar.  See DECISIONS.md D-026.");
        out.add(";");
        out.add(";  8 words per character, one per pixel row (top first). A row is a number");
        out.add(";  0 ... 255 whose bit 0 is the LEFTMOST pixel. The kernel turns the bits into");
        out.add(";  tile pixels at boot (see 'loadfont' in kernel.tasm).");
        out.add("; ============================================================================");
        out.add("");
        out.add("font:");
        for (int code = 32; code <= 126; code++) {
            int[] g = glyphs[code];
            if (g == null) {
                throw new IllegalStateException("character " + code + " missing from " + args[0]);
            }
            StringBuilder sb = new StringBuilder("        .word ");
            for (int i = 0; i < 8; i++) {
                sb.append(i == 0 ? "" : ", ").append(String.format("%3d", g[i]));
            }
            String name = code == ' ' ? "space" : Character.toString((char) code);
            sb.append("   ; ").append(code).append(" '").append(name).append("'");
            out.add(sb.toString());
        }
        Files.write(Path.of(args[1]), out, StandardCharsets.UTF_8);
        System.out.println("wrote " + args[1] + ": 95 characters, " + (95 * 8) + " words");
    }
}
