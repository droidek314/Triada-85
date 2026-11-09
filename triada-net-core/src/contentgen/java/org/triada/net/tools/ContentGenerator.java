package org.triada.net.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.Assembler;
import org.triada.core.Machine;
import org.triada.core.MachineProfiles;
import org.triada.core.TriImage;
import org.triada.core.io.KeyFeeder;
import org.triada.rom.Rom;

/**
 * Builds the programs TRIADA-NET's starter boards offer (M19), during the
 * build, into resources {@code net/files/NAME}, as {@code .tri} bytes:
 * <ul>
 * <li>a {@code .tasm} source is assembled, as {@code triada tasm} does;</li>
 * <li>a {@code .bas} program is typed into a real TRIBASIC on a headless
 *     TRIADA-85 and SAVEd to its triskette: that is the only true way to
 *     get TRIBASIC's own tokens, so the file is exactly what a user's SAVE
 *     makes.</li>
 * </ul>
 *
 * <p>usage: ContentGenerator projectDir files.txt outputDir
 */
public final class ContentGenerator {

    private ContentGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args[0]);
        Path list = Path.of(args[1]);
        Path out = Path.of(args[2]).resolve("net/files");
        Files.createDirectories(out);
        int n = 0;
        for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] f = t.split(" +");
            String name = f[0];
            Path source = root.resolve(f[1]);
            TriImage image = f[1].endsWith(".tasm") ? assemble(source) : typeIn(source, name);
            Files.write(out.resolve(name), image.toBytes());
            n++;
        }
        System.out.println("TRIADA-NET content: " + n + " files");
    }

    private static TriImage assemble(Path source) {
        try {
            return new Assembler(path -> {
                try {
                    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return null;
                }
            }, MachineProfiles.TRIADA_85).assemble(source.toString()).image();
        } catch (AssemblyException e) {
            throw new IllegalStateException(source + ": " + e.getMessage(), e);
        }
    }

    /** Types the program into TRIBASIC and saves it; the saved .TB file is the result. */
    private static TriImage typeIn(Path source, String name) throws IOException {
        String base = name.substring(0, name.indexOf('.'));
        String text = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
        Machine m = Rom.newMachine();
        for (int i = 0; i < 120; i++) {                 // the boot
            m.runFrame();
        }
        KeyFeeder typing = new KeyFeeder("NEW\n" + text + "\nSAVE \"" + base + "\"\n");
        for (int frame = 0; frame < 20_000; frame++) {
            typing.feed(m.input());
            m.runFrame();
            TriImage saved = typing.done() ? m.triskette().port().read(name) : null;
            if (saved != null) {
                return saved;
            }
        }
        throw new IllegalStateException(source + ": TRIBASIC did not save " + name);
    }
}
