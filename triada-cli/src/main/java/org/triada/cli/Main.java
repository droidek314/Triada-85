package org.triada.cli;

import org.triada.core.MachineInfo;

/**
 * Entry point of the {@code triada} command-line tool.
 *
 * <p>In M0 it only prints a usage message. The {@code tasm} command arrives in
 * M4/M5 and the headless {@code run} command in M5.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println(usage());
        // Exit code 2 is the usual "wrong usage" code for command-line tools.
        System.exit(args.length == 0 ? 0 : 2);
    }

    /** The help text, kept separate from {@code main} so tests can check it. */
    static String usage() {
        return MachineInfo.NAME + " command-line tools\n"
                + "\n"
                + "usage:\n"
                + "  triada tasm source.tasm -o out.tri [--listing out.lst]   (coming in M5)\n"
                + "  triada run program.tri [--frames N] [--dump-screen out.png]   (coming in M5)";
    }
}
