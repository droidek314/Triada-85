package org.triada.desktop;

import java.nio.file.Path;

/**
 * The desktop's command line, parsed into plain values (no libGDX, so it is
 * easy to test).
 *
 * <pre>
 *   [--load] FILE           a .tasm or .tri program to start after boot (M9)
 *   --crt                   start with the CRT look switched on (F2 toggles it, M11)
 *   --capture PNG           don't show a window: run --frames frames, save the
 *                           picture (scaled 3x, with --crt if given) and quit (M11)
 *   --frames N              frames before the capture (default 120: boot plus a second)
 *   --disk DIR              the triskette: a folder of .tri files (default triskettes, M13)
 *   --modem HOST:PORT       the modem's line: a TRIADA-NET server over TCP (M17)
 *   --local-node            the modem's line: TRIADA-NET inside the emulator, offline (M19)
 * </pre>
 *
 * @param program       the program file, or null for none
 * @param crt           whether the CRT effect starts switched on
 * @param capture       where to save a capture, or null for a normal window
 * @param captureFrames how many frames to run before capturing
 * @param disk          the triskette folder
 * @param modem         the TRIADA-NET server as host:port, or null: no network
 * @param localNode     TRIADA-NET inside the emulator (M19)
 */
public record LaunchOptions(String program, boolean crt, Path capture, int captureFrames, Path disk,
        String modem, boolean localNode) {

    /** Frames before a capture unless --frames says otherwise: the boot (about 48) and a second more. */
    public static final int DEFAULT_CAPTURE_FRAMES = 120;

    /** The picture's scale in a capture. */
    public static final int CAPTURE_SCALE = 3;

    /** The triskette folder unless --disk says otherwise, relative to the working directory (M13). */
    public static final Path DEFAULT_DISK = Path.of("triskettes");

    /**
     * Parses the arguments.
     *
     * @throws IllegalArgumentException with a message for the user, for an
     *     unknown option, a missing value or a second program file
     */
    public static LaunchOptions parse(String... args) {
        String program = null;
        boolean crt = false;
        Path capture = null;
        int frames = DEFAULT_CAPTURE_FRAMES;
        Path disk = DEFAULT_DISK;
        String modem = null;
        boolean localNode = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--crt" -> crt = true;
                case "--load" -> program = setOnce(program, value(args, ++i, a + " needs a file, e.g. --load programs/piano.tasm"));
                case "--capture" -> capture = Path.of(value(args, ++i, a + " needs a PNG file name"));
                case "--frames" -> frames = positive(value(args, ++i, a + " needs a number of frames"));
                case "--disk" -> disk = Path.of(value(args, ++i, a + " needs a folder, e.g. --disk triskettes"));
                case "--modem" -> modem = value(args, ++i, a + " needs a server, e.g. --modem localhost:2385");
                case "--local-node" -> localNode = true;
                default -> {
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("unknown option " + a
                                + " (options: --load FILE, --crt, --capture PNG, --frames N, --disk DIR,"
                                + " --modem HOST:PORT, --local-node)");
                    }
                    program = setOnce(program, a);
                }
            }
        }
        if (modem != null && localNode) {
            throw new IllegalArgumentException("--modem and --local-node: one line at a time");
        }
        return new LaunchOptions(program, crt, capture, frames, disk, modem, localNode);
    }

    private static String value(String[] args, int i, String message) {
        if (i >= args.length) {
            throw new IllegalArgumentException(message);
        }
        return args[i];
    }

    private static String setOnce(String old, String file) {
        if (old != null) {
            throw new IllegalArgumentException("only one program at a time: " + old + " and " + file);
        }
        return file;
    }

    private static int positive(String text) {
        try {
            int n = Integer.parseInt(text);
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException("--frames needs a whole number of at least 1, not " + text);
    }
}
