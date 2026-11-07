package org.triada.assembler;

/**
 * Where the assembler gets source text from. The assembler library itself
 * never touches files (CLAUDE.md §2: keep it portable), so whoever calls it
 * supplies a loader: the CLI reads from disk, tests use a map of strings.
 */
@FunctionalInterface
public interface SourceLoader {

    /**
     * Returns the text of the source called {@code path}, or {@code null} if
     * there is no such source. Paths use {@code /} as separator; the assembler
     * resolves an {@code .include} relative to the including file's folder.
     */
    String load(String path);

    /** A loader that knows no files at all: {@code .include} always fails. */
    SourceLoader NONE = path -> null;
}
