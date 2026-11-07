package org.triada.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test helper: runs the CLI in-process and captures its output and exit code. */
final class Cli {

    /** What one run of the command line produced. */
    record Result(int code, String out, String err) {
        @Override
        public String toString() {
            return "exit " + code + "\n--- stdout:\n" + out + "--- stderr:\n" + err;
        }
    }

    private Cli() {
    }

    static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code;
        try (PrintStream o = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream e = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            code = Main.run(args, o, e);
        }
        // Normalise line endings so the tests read the same on every OS.
        return new Result(code, out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                err.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    /** Writes a source file into {@code dir} and returns its path as a string. */
    static String write(Path dir, String name, String text) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p.toString();
    }

    /** The repository root (set by Gradle, see triada-cli/build.gradle.kts). */
    static Path root() {
        String root = System.getProperty("triada.root");
        return root != null ? Path.of(root) : Path.of("..");
    }
}
