package org.triada.cli;

import java.io.PrintStream;
import java.util.Arrays;
import org.triada.core.MachineInfo;

/**
 * Entry point of the {@code triada} command-line tool. It dispatches to the
 * commands:
 * <ul>
 * <li>{@code triada tasm}: assemble a source file ({@link TasmCommand});</li>
 * <li>{@code triada run}: run a program without a window ({@link RunCommand}).</li>
 * </ul>
 *
 * <p>All the work happens in {@link #run(String[], PrintStream, PrintStream)},
 * which returns the exit code instead of calling {@code System.exit}, so the
 * tests can call it directly and inspect its output.
 *
 * <p>Exit codes (DECISIONS.md D-017):
 * <pre>
 *   0      success (or the program wrote HOST_EXIT 0, or halted with HLT)
 *   n      the program wrote HOST_EXIT n
 *   1      the source has assembly errors
 *   2      wrong usage, unreadable file, broken .tri file
 *   3      the program caused a CPU fault
 *   4      time-out: no HOST_EXIT or HLT within the default frame limit
 * </pre>
 */
public final class Main {

    public static final int EXIT_OK = 0;
    public static final int EXIT_ASSEMBLY_ERRORS = 1;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_FAULT = 3;
    public static final int EXIT_TIMEOUT = 4;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the command line {@code args}; returns the exit code. */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            err.println(usage());
            return EXIT_USAGE;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (args[0]) {
                case "tasm":
                    return new TasmCommand(out, err).run(rest);
                case "run":
                    return new RunCommand(out, err).run(rest);
                case "help":
                case "-h":
                case "--help":
                    out.println(usage());
                    return EXIT_OK;
                default:
                    throw new UsageException("unknown command '" + args[0] + "'");
            }
        } catch (UsageException e) {
            err.println("triada: " + e.getMessage());
            err.println();
            err.println(usage());
            return EXIT_USAGE;
        }
    }

    /** The help text, kept separate from {@code main} so tests can check it. */
    static String usage() {
        return MachineInfo.NAME + " command-line tools\n"
                + "\n"
                + "usage:\n"
                + "  triada tasm source.tasm [-o out.tri] [--listing out.lst]\n"
                + "      Assemble a source file. Without -o the output is source.tri.\n"
                + "  triada run program.tri [--frames N] [--dump-screen out.png] [--dump-audio out.wav]\n"
                + "      Run a program without a window, printing what it writes to\n"
                + "      DEBUG_CHAR and DEBUG_NUM. A .tasm file is assembled first.\n"
                + "      Stops on HOST_EXIT, HLT, a fault, or after N frames\n"
                + "      (default: " + RunCommand.DEFAULT_FRAME_LIMIT + " frames = 1 minute of machine time).\n"
                + "  triada help\n"
                + "\n"
                + "exit codes: 0 ok, n = HOST_EXIT n, 1 assembly errors, 2 usage or file error,\n"
                + "            3 CPU fault, 4 time-out";
    }

    /** Wrong command-line usage: reported with the usage text, exit code 2. */
    static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
