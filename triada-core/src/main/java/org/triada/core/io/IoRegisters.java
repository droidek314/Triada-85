package org.triada.core.io;

/**
 * The I/O register numbers {@code k} from SPEC §5. Register {@code k} lives at
 * address {@code MemoryMap.IO_BASE + k}. The assembler will offer the same
 * names as built-in symbols (SPEC §9.3).
 */
public final class IoRegisters {

    public static final int FRAME = 0;
    public static final int JOY_X = 1;
    public static final int JOY_Y = 2;
    public static final int BTN_A = 3;
    public static final int BTN_B = 4;
    public static final int BTN_C = 5;
    public static final int KEY = 6;
    public static final int SCROLL_X = 7;
    public static final int SCROLL_Y = 8;
    public static final int BORDER = 9;
    public static final int V1_FREQ = 10;
    public static final int V1_VOL = 11;
    public static final int V1_WAVE = 12;
    public static final int V2_FREQ = 13;
    public static final int V2_VOL = 14;
    public static final int V2_WAVE = 15;
    public static final int V3_FREQ = 16;
    public static final int V3_VOL = 17;
    public static final int V3_WAVE = 18;
    public static final int RAND = 19;
    public static final int DEBUG_CHAR = 20;
    public static final int DEBUG_NUM = 21;
    public static final int HOST_EXIT = 22;
    /** Triskette drive (SPEC §5.1, M13). */
    public static final int TS_CMD = 23;
    public static final int TS_NAME = 24;
    public static final int TS_ADDR = 25;
    public static final int TS_LEN = 26;
    public static final int TS_STATUS = 27;

    /** Number of registers the spec defines (0 … 27); the rest are reserved. */
    public static final int DEFINED_COUNT = 28;

    /** Total number of register slots (0 … 728). */
    public static final int COUNT = 729;

    private static final String[] NAMES = {
        "FRAME", "JOY_X", "JOY_Y", "BTN_A", "BTN_B", "BTN_C", "KEY",
        "SCROLL_X", "SCROLL_Y", "BORDER",
        "V1_FREQ", "V1_VOL", "V1_WAVE", "V2_FREQ", "V2_VOL", "V2_WAVE",
        "V3_FREQ", "V3_VOL", "V3_WAVE",
        "RAND", "DEBUG_CHAR", "DEBUG_NUM", "HOST_EXIT",
        "TS_CMD", "TS_NAME", "TS_ADDR", "TS_LEN", "TS_STATUS",
    };

    private IoRegisters() {
    }

    /**
     * The spec name of register {@code k}, or {@code null} for a reserved one.
     * Used by the debugger and the disassembler.
     */
    public static String nameOf(int k) {
        return k >= 0 && k < NAMES.length ? NAMES[k] : null;
    }
}
