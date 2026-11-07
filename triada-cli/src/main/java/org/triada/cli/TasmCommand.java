package org.triada.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.triada.assembler.AssemblyError;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.AssemblyResult;
import org.triada.assembler.Assembler;
import org.triada.core.TriImage;

/**
 * {@code triada tasm source.tasm [-o out.tri] [--listing out.lst]}: assembles a
 * source file into a {@code .tri} file and, if asked, a listing.
 */
final class TasmCommand {

    private final PrintStream out;
    private final PrintStream err;

    TasmCommand(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    int run(String[] args) throws Main.UsageException {
        String source = null;
        String output = null;
        String listing = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    output = value(args, ++i, "-o");
                    break;
                case "--listing":
                    listing = value(args, ++i, "--listing");
                    break;
                default:
                    if (args[i].startsWith("-")) {
                        throw new Main.UsageException("tasm: unknown option '" + args[i] + "'");
                    }
                    if (source != null) {
                        throw new Main.UsageException("tasm: only one source file, got '" + source
                                + "' and '" + args[i] + "'");
                    }
                    source = args[i];
            }
        }
        if (source == null) {
            throw new Main.UsageException("tasm: which file? e.g. triada tasm hello.tasm");
        }
        if (output == null) {
            output = withExtension(source, ".tri");
        }

        if (!Files.isReadable(Path.of(source))) {
            err.println("triada: can't read " + source);
            return Main.EXIT_USAGE;
        }
        AssemblyResult result;
        try {
            result = new Assembler(new FileSourceLoader()).assemble(source);
        } catch (AssemblyException e) {
            for (AssemblyError error : e.errors()) {
                err.println(error);
            }
            err.println(e.errors().size() + (e.errors().size() == 1 ? " error" : " errors"));
            return Main.EXIT_ASSEMBLY_ERRORS;
        }

        TriImage image = result.image();
        try {
            Files.write(Path.of(output), image.toBytes());
            if (listing != null) {
                Files.writeString(Path.of(listing), result.listing(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            err.println("triada: can't write output: " + e.getMessage());
            return Main.EXIT_USAGE;
        }
        out.println(source + ": " + image.size() + " words at " + image.loadAddress() + " -> " + output
                + (listing != null ? " (listing: " + listing + ")" : ""));
        return Main.EXIT_OK;
    }

    static String value(String[] args, int i, String option) throws Main.UsageException {
        if (i >= args.length) {
            throw new Main.UsageException(option + " needs a value");
        }
        return args[i];
    }

    /** {@code games/snake.tasm} → {@code games/snake.tri}. */
    static String withExtension(String path, String extension) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        return (dot > slash ? path.substring(0, dot) : path) + extension;
    }
}
