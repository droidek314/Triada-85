package org.triada.core.memory;

import org.triada.core.ternary.Word;

/**
 * Where everything lives in TRIADA's address space (SPEC §2.2 and §2.3).
 *
 * <p>Addresses are words, so they run from −265,720 to +265,720, with
 * address 0 in the middle. Going up from the bottom:
 *
 * <pre>
 *   −265,720 … −20,413   unmapped     (reads 0, writes ignored)
 *    −20,412 … −19,684   I/O          (729 registers)
 *    −19,683 …      −1   ROM          (19,683 = 3^9 words, read-only)
 *          0 …  59,048   RAM          (59,049 = 3^10 words)
 *     59,049 … 265,720   unmapped     (reserved for cartridges)
 * </pre>
 */
public final class MemoryMap {

    /** The four kinds of address. */
    public enum Region {
        /** Nothing there: reads 0, writes ignored. */
        UNMAPPED,
        /** I/O registers: reads and writes go to devices (SPEC §5). */
        IO,
        /** Read-only memory holding the kernel; CPU writes are ignored. */
        ROM,
        /** Normal read/write memory. */
        RAM
    }

    // ------------------------------------------------------------ regions (§2.2)

    /** First RAM address. */
    public static final int RAM_START = 0;
    /** Number of RAM words: 3^10. */
    public static final int RAM_SIZE = 59_049;
    /** Last RAM address. */
    public static final int RAM_END = RAM_START + RAM_SIZE - 1;            // 59,048

    /** Number of ROM words: 3^9. */
    public static final int ROM_SIZE = 19_683;
    /** First ROM address; also where the CPU starts after power-on (§2.4). */
    public static final int ROM_START = -ROM_SIZE;                         // −19,683
    /** Last ROM address. */
    public static final int ROM_END = -1;

    /** Number of I/O registers: 3^6. */
    public static final int IO_SIZE = 729;
    /** Address of I/O register 0 (called IO_BASE in SPEC §5). */
    public static final int IO_BASE = ROM_START - IO_SIZE;                 // −20,412
    /** Address of the last I/O register (k = 728). */
    public static final int IO_END = ROM_START - 1;                        // −19,684

    // ------------------------------------------------------------ RAM layout (§2.3)

    /** System variables owned by the ROM: 0 … 242. */
    public static final int SYSVARS_START = 0;
    /** Where programs are loaded: 243 = 3^5. */
    public static final int PROGRAM_START = 243;
    /** Tile patterns: 243 tiles × 16 words (§4.4). */
    public static final int TILES = 40_000;
    /** Tile map: 27 × 18 cells (§4.5). */
    public static final int MAP = 44_000;
    /** Sprite table: 27 sprites × 4 words (§4.7). */
    public static final int SPRITES = 44_600;
    /** Start of the free / stack area. */
    public static final int FREE_START = 45_000;
    /** Initial stack pointer: one past the end of RAM; the stack grows down (§2.3). */
    public static final int STACK_TOP = RAM_END + 1;                       // 59,049

    private MemoryMap() {
    }

    /**
     * Which region an address belongs to.
     *
     * @throws IllegalArgumentException if the address is not a word
     */
    public static Region regionOf(int address) {
        if (address >= RAM_START && address <= RAM_END) {
            return Region.RAM;
        }
        if (address >= ROM_START && address <= ROM_END) {
            return Region.ROM;
        }
        if (address >= IO_BASE && address <= IO_END) {
            return Region.IO;
        }
        checkAddress(address);
        return Region.UNMAPPED;
    }

    static void checkAddress(int address) {
        if (!Word.isValid(address)) {
            throw new IllegalArgumentException(
                    "address " + address + " is not a word (range " + Word.MIN + " .. " + Word.MAX + ")");
        }
    }
}
