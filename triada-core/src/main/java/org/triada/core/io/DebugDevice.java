package org.triada.core.io;

/**
 * The debug port: three write-only registers that let a program talk to the
 * <em>host</em> (the PC running the emulator) instead of the TRIADA screen
 * (SPEC §5). A development aid, and the way tests see what a program did.
 *
 * <ul>
 * <li>DEBUG_CHAR (k = 20): the value is printed as a character.</li>
 * <li>DEBUG_NUM (k = 21): the value is printed as a decimal number plus a newline.</li>
 * <li>HOST_EXIT (k = 22): the program asks the host to stop, with this exit code.</li>
 * </ul>
 *
 * <p>triada-core does no file or console I/O (CLAUDE.md §2), so the printed text
 * collects in a buffer that the host drains with {@link #takeOutput()}, for
 * example after every frame.
 *
 * <p>When HOST_EXIT is written, the device calls an "end the frame now" hook
 * (the machine connects it to {@code Cpu.endFrame()}), so no further
 * instruction runs after the exit request.
 */
public final class DebugDevice implements IoDevice {

    /** First register this device occupies. */
    public static final int FIRST_REGISTER = IoRegisters.DEBUG_CHAR;
    /** Number of registers: DEBUG_CHAR, DEBUG_NUM, HOST_EXIT. */
    public static final int REGISTER_COUNT = 3;

    private final StringBuilder output = new StringBuilder();
    private final Runnable onExit;
    private boolean exitRequested;
    private int exitCode;

    /**
     * @param onExit called when the program writes HOST_EXIT (may do nothing)
     */
    public DebugDevice(Runnable onExit) {
        this.onExit = onExit;
    }

    // ---------------------------------------------------------------- host side

    /** Returns everything printed since the last call, and empties the buffer. */
    public String takeOutput() {
        String s = output.toString();
        output.setLength(0);
        return s;
    }

    /** True once the program has written HOST_EXIT. */
    public boolean exitRequested() {
        return exitRequested;
    }

    /** The value written to HOST_EXIT (meaningful only if {@link #exitRequested()}). */
    public int exitCode() {
        return exitCode;
    }

    /** Forgets output and any exit request, e.g. on power-on. */
    public void reset() {
        output.setLength(0);
        exitRequested = false;
        exitCode = 0;
    }

    // ---------------------------------------------------------------- CPU side

    @Override
    public int read(int k) {
        return 0;   // write-only registers read 0
    }

    @Override
    public void write(int k, int value) {
        switch (k) {
            case IoRegisters.DEBUG_CHAR:
                appendChar(value);
                break;
            case IoRegisters.DEBUG_NUM:
                output.append(value).append('\n');
                break;
            case IoRegisters.HOST_EXIT:
                exitRequested = true;
                exitCode = value;
                onExit.run();
                break;
            default:
                break;
        }
    }

    /**
     * Printable ASCII (32 … 126), newline, carriage return and tab print as
     * themselves. Anything else would be invisible or garble the terminal, so
     * it prints as its number in angle brackets, e.g. {@code <0>} or {@code <-5>}
     * (DECISIONS.md D-018).
     */
    private void appendChar(int value) {
        if ((value >= 32 && value <= 126) || value == '\n' || value == '\r' || value == '\t') {
            output.append((char) value);
        } else {
            output.append('<').append(value).append('>');
        }
    }
}
