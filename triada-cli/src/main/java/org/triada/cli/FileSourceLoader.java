package org.triada.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.triada.assembler.SourceLoader;

/**
 * Reads assembly sources from disk for the assembler. The assembler library
 * itself never touches files (it must stay portable), so the CLI provides
 * this small adapter. Sources are UTF-8 text.
 */
final class FileSourceLoader implements SourceLoader {

    @Override
    public String load(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;     // the assembler reports "can't read" / "can't find" with file and line
        }
    }
}
