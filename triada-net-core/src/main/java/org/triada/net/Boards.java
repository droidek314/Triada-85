package org.triada.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.triada.core.TriImage;

/**
 * Where TRIADA-NET's boards and their files come from (M18, M19): the boards
 * built in (resources {@code /net/boards/}, listed in {@code index.txt}), or a
 * folder of {@code .board} files. The programs the boards offer are built
 * during the build ({@code ContentGenerator}) into resources {@code /net/files/}.
 */
public final class Boards {

    private Boards() {
    }

    /** The boards built into TRIADA-NET: the starter boards (M19). */
    public static List<BoardDef> builtIn() {
        List<BoardDef> boards = new ArrayList<>();
        for (String name : resource("boards/index.txt").split("\n")) {
            if (!name.isBlank() && !name.startsWith("#")) {
                boards.add(parse(name.strip(), resource("boards/" + name.strip())));
            }
        }
        return boards;
    }

    /** Every {@code .board} file in {@code dir}, or the built-in boards if {@code dir} is null. */
    public static List<BoardDef> load(Path dir) throws IOException {
        if (dir == null) {
            return builtIn();
        }
        List<BoardDef> boards = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".board")).sorted().toList()) {
                boards.add(parse(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        return boards;
    }

    /** A file the boards offer (e.g. "TROIKA.TRI"), as built during the build; null if there is none. */
    public static TriImage file(String name) {
        try (InputStream in = Boards.class.getResourceAsStream("/net/files/" + name)) {
            return in == null ? null : TriImage.fromBytes(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BoardDef parse(String name, String text) {
        try {
            return BoardDef.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + ": " + e.getMessage(), e);
        }
    }

    private static String resource(String name) {
        try (InputStream in = Boards.class.getResourceAsStream("/net/" + name)) {
            if (in == null) {
                throw new IllegalStateException("no built-in " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
