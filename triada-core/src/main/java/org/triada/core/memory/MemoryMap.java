package org.triada.core.memory;

import org.triada.core.MachineProfile;
import org.triada.core.MachineProfiles;
import org.triada.core.ternary.Word;

/**
 * Where everything lives in TRIADA's address space (SPEC §2.2 and §2.3).
 *
 * <p>Addresses are words, so they run from −265,720 to +265,720, with
 * address 0 in the middle. For the TRIADA-85, going up from the bottom:
 *
 * <pre>
 *   −265,720 … −59,049   unmapped     (reads 0, writes ignored)
 *    −59,048 … −20,413   extension ROM (TRIBASIC, 38,636 words)
 *    −20,412 … −19,684   I/O          (729 registers)
 *    −19,683 …      −1   ROM          (19,683 = 3^9 words, read-only)
 *          0 …  59,048   RAM          (59,049 = 3^10 words)
 *     59,049 … 265,720   unmapped     (reserved for cartridges)
 * </pre>
 *
 * <p>Since M16 the numbers belong to a {@link MachineProfile}; the machine
 * reads them from its own profile ({@link #regionOf(MachineProfile, int)}).
 * The constants below are the TRIADA-85's ({@link MachineProfiles#TRIADA_85}),
 * kept for code that is about that model only: its tests, tools and frontends.
 */
public final class MemoryMap {

    /** The four kinds of address. */
    public enum Region {
        /** Nothing there: reads 0, writes ignored. */
        UNMAPPED,
        /** I/O registers: reads and writes go to devices (SPEC §5). */
        IO,
        /** Read-only memory: the main ROM (kernel, monitor) and, since M14, the
         *  extension ROM (TRIBASIC); CPU writes are ignored. */
        ROM,
        /** Normal read/write memory. */
        RAM
    }

    private static final MachineProfile P = MachineProfiles.TRIADA_85;

    // ------------------------------------------------------------ regions (§2.2)

    /** First RAM address. */
    public static final int RAM_START = P.ramStart();
    /** Number of RAM words: 3^10. */
    public static final int RAM_SIZE = P.ramSize();
    /** Last RAM address: 59,048. */
    public static final int RAM_END = P.ramEnd();

    /** Number of ROM words: 3^9. */
    public static final int ROM_SIZE = P.romSize();
    /** First ROM address (−19,683); also where the CPU starts after power-on (§2.4). */
    public static final int ROM_START = P.romStart();
    /** Last ROM address: −1. */
    public static final int ROM_END = P.romEnd();

    /** First word of the extension ROM, where TRIBASIC lives (SPEC §2.5, M14): −59,048. */
    public static final int EXT_ROM_START = P.extRomStart();
    /** Last word of the extension ROM, just below the I/O registers: −20,413. */
    public static final int EXT_ROM_END = P.extRomEnd();
    /** Number of extension ROM words: 38,636. */
    public static final int EXT_ROM_SIZE = P.extRomSize();

    /** Number of I/O registers: 3^6. */
    public static final int IO_SIZE = P.ioSize();
    /** Address of I/O register 0 (called IO_BASE in SPEC §5): −20,412. */
    public static final int IO_BASE = P.ioBase();
    /** Address of the last I/O register (k = 728): −19,684. */
    public static final int IO_END = P.ioEnd();

    // ------------------------------------------------------------ RAM layout (§2.3)

    /** System variables owned by the ROM: 0 … 242. */
    public static final int SYSVARS_START = P.ramStart();
    /** Where programs are loaded: 243 = 3^5. */
    public static final int PROGRAM_START = P.programStart();
    /** Tile patterns: 243 tiles × 16 words (§4.4). */
    public static final int TILES = P.tiles();
    /** Tile map: 27 × 18 cells (§4.5). */
    public static final int MAP = P.map();
    /** Sprite table: 27 sprites × 4 words (§4.7). */
    public static final int SPRITES = P.sprites();
    /** Start of the free / stack area. */
    public static final int FREE_START = P.freeStart();
    /** Initial stack pointer: one past the end of RAM (59,049); the stack grows down (§2.3). */
    public static final int STACK_TOP = P.stackTop();

    private MemoryMap() {
    }

    /**
     * Which region an address belongs to, on the TRIADA-85.
     *
     * @throws IllegalArgumentException if the address is not a word
     */
    public static Region regionOf(int address) {
        return regionOf(P, address);
    }

    /**
     * Which region an address belongs to, on the model {@code profile}.
     *
     * @throws IllegalArgumentException if the address is not a word
     */
    public static Region regionOf(MachineProfile profile, int address) {
        if (address >= profile.ramStart() && address <= profile.ramEnd()) {
            return Region.RAM;
        }
        if (address >= profile.romStart() && address <= profile.romEnd()) {
            return Region.ROM;
        }
        if (address >= profile.ioBase() && address <= profile.ioEnd()) {
            return Region.IO;
        }
        if (profile.extRomSize() > 0 && address >= profile.extRomStart() && address <= profile.extRomEnd()) {
            return Region.ROM;
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
