package org.triada.net.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.triada.net.BoardDef;

/**
 * Where the server's boards come from (M18): a folder of {@code .board}
 * files ({@code TRIADA_NET_BOARDS}), or else the ones built into the server
 * (listed in {@code boards/index.txt} among its resources).
 */
public final class Boards {

    private Boards() {
    }

    /** Every {@code .board} file in {@code dir}, or the built-in boards if {@code dir} is null. */
    public static List<BoardDef> load(Path dir) throws IOException {
        List<BoardDef> boards = new ArrayList<>();
        if (dir == null) {
            for (String name : resource("index.txt").split("\n")) {
                if (!name.isBlank() && !name.startsWith("#")) {
                    boards.add(parse(name.strip(), resource(name.strip())));
                }
            }
            return boards;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".board")).sorted().toList()) {
                boards.add(parse(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        return boards;
    }

    private static BoardDef parse(String name, String text) {
        try {
            return BoardDef.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + ": " + e.getMessage(), e);
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = Boards.class.getResourceAsStream("/boards/" + name)) {
            if (in == null) {
                throw new IOException("no built-in board file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
