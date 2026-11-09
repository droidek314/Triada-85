package org.triada.rom.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.triada.assembler.AssemblyError;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.AssemblyResult;
import org.triada.assembler.Assembler;
import org.triada.core.TriImage;
import org.triada.core.MachineProfile;
import org.triada.core.MachineProfiles;
import org.triada.core.ternary.Word;

/**
 * Build-time tool (Gradle task {@code generateRom}): assembles the ROM source
 * and writes {@code org/triada/rom/RomData.java} plus a listing.
 *
 * <p>Why Java source instead of a {@code .tri} resource file? The emulator then
 * contains its ROM like a real machine does, needs no file or resource access
 * at run time, and a web build (TeaVM) compiles it like any other class.
 *
 * <p>The words are stored as text: each word becomes 4 characters from a
 * 27-letter alphabet, because 27^4 = 531,441 = 3^12. One character carries
 * exactly three trits. Long strings are split into chunks (a Java string
 * constant may hold at most 65,535 bytes).
 *
 * <p>usage: RomGenerator rom.tasm outputJavaDir listing.lst
 */
public final class RomGenerator {

    /** The model this ROM is for (M16): its built-in symbols and memory map. */
    private static final MachineProfile P = MachineProfiles.TRIADA_85;

    /** 27 symbols; character i stands for the base-27 digit i. Must match Rom.ALPHABET. */
    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQ";
    private static final int CHUNK_WORDS = 4096;

    private RomGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args[0]);
        Path javaDir = Path.of(args[1]);
        Path listing = Path.of(args[2]);

        AssemblyResult result;
        try {
            result = new Assembler(RomGenerator::read, P).assemble(source.toString());
        } catch (AssemblyException e) {
            for (AssemblyError error : e.errors()) {
                System.err.println(error);
            }
            throw new IllegalStateException("the ROM has " + e.errors().size() + " assembly error(s)");
        }
        TriImage all = result.image();
        TriImage image = mainRom(all);
        TriImage ext = extensionRom(all);

        Files.createDirectories(listing.getParent());
        Files.writeString(listing, result.listing(), StandardCharsets.UTF_8);

        Path out = javaDir.resolve("org/triada/rom/RomData.java");
        Files.createDirectories(out.getParent());
        Files.writeString(out, javaSource(image, ext, result.symbols()), StandardCharsets.UTF_8);
        System.out.println("ROM: " + image.size() + " of " + P.romSize() + " words, extension ROM: "
                + ext.size() + " of " + P.extRomSize() + " words, " + result.symbols().size() + " symbols");
    }

    /**
     * The main ROM's part of the assembled image (M14): ROM_START ... -1. The
     * source assembles the main ROM and the extension ROM together, so the
     * image may begin lower, at EXT_ROM_START; the I/O registers between the
     * two must stay empty.
     */
    static TriImage mainRom(TriImage all) {
        long last = (long) all.loadAddress() + all.size() - 1;
        if (all.loadAddress() > P.romStart() || last > P.romEnd()) {
            throw new IllegalStateException("the ROM must start at " + P.romStart()
                    + " and end by " + P.romEnd() + ", not " + all.loadAddress() + " .. " + last);
        }
        if (all.loadAddress() < P.extRomStart()) {
            throw new IllegalStateException("code at " + all.loadAddress() + ", below the extension ROM");
        }
        for (int a = P.ioBase(); a <= P.ioEnd(); a++) {
            if (wordAt(all, a) != 0) {
                throw new IllegalStateException("the ROM source puts a word into the I/O registers at " + a);
            }
        }
        int[] words = new int[(int) last - P.romStart() + 1];
        for (int i = 0; i < words.length; i++) {
            words[i] = wordAt(all, P.romStart() + i);
        }
        return new TriImage(P.romStart(), words);
    }

    /** The extension ROM's part (EXT_ROM_START ...), without trailing zeros; empty if there is none. */
    static TriImage extensionRom(TriImage all) {
        int end = P.extRomEnd();
        while (end >= P.extRomStart() && wordAt(all, end) == 0) {
            end--;
        }
        int[] words = new int[end - P.extRomStart() + 1];
        for (int i = 0; i < words.length; i++) {
            words[i] = wordAt(all, P.extRomStart() + i);
        }
        return new TriImage(P.extRomStart(), words);
    }

    private static int wordAt(TriImage image, int address) {
        int i = address - image.loadAddress();
        return i >= 0 && i < image.size() ? image.word(i) : 0;
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static String encode(int word) {
        int u = word + Word.MAX;                       // 0 ... 531,440
        char[] c = new char[4];
        for (int i = 3; i >= 0; i--) {
            c[i] = ALPHABET.charAt(u % 27);
            u /= 27;
        }
        return new String(c);
    }

    private static String javaSource(TriImage image, TriImage ext, Map<String, Integer> symbols) {
        StringBuilder sb = new StringBuilder();
        sb.append("package org.triada.rom;\n\n");
        sb.append("/** GENERATED by RomGenerator from rom/rom.tasm during the build. Do not edit. */\n");
        sb.append("final class RomData {\n\n");
        sb.append("    static final int SIZE = ").append(image.size()).append(";\n\n");
        sb.append("    /** The main ROM's words, 4 base-27 characters each (see Rom.ALPHABET), in chunks. */\n");
        words(sb, "WORDS", image);
        sb.append("    static final int EXT_SIZE = ").append(ext.size()).append(";\n\n");
        sb.append("    /** The extension ROM's words (TRIBASIC, M14), encoded the same way. */\n");
        words(sb, "EXT_WORDS", ext);

        Map<String, Integer> sorted = new TreeMap<>(symbols);
        sb.append("    /** Every label and constant of the ROM source, with its value. */\n");
        sb.append("    static final String[] SYMBOL_NAMES = {\n");
        for (String name : sorted.keySet()) {
            sb.append("        \"").append(name).append("\",\n");
        }
        sb.append("    };\n\n");
        sb.append("    static final int[] SYMBOL_VALUES = {\n");
        for (int v : sorted.values()) {
            sb.append("        ").append(v).append(",\n");
        }
        sb.append("    };\n\n");
        sb.append("    private RomData() {\n    }\n}\n");
        return sb.toString();
    }

    private static void words(StringBuilder sb, String name, TriImage image) {
        sb.append("    static final String[] ").append(name).append(" = {\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < image.size(); i++) {
            chunk.append(encode(image.word(i)));
            if ((i + 1) % CHUNK_WORDS == 0) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
            }
        }
        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }
        for (String c : chunks) {
            sb.append("        \"").append(c).append("\",\n");
        }
        sb.append("    };\n\n");
    }
}
