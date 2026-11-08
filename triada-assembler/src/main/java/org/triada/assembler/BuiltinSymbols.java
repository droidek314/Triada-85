package org.triada.assembler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/**
 * Names every program knows without defining them (SPEC §9.3):
 * <ul>
 * <li>the ROM routines of the jump table (SPEC §8.1): {@code RESET},
 *     {@code PUTCHAR}, {@code PRINT}, {@code PRINTNUM}, {@code CLS},
 *     {@code GETKEY}, {@code LOCATE}, and since M13 {@code DISKSAVE},
 *     {@code DISKLOAD}, {@code DISKDIR}, {@code DISKDEL};</li>
 * <li>{@code IO_BASE} and every I/O register name as an offset from it
 *     ({@code FRAME = 0}, {@code JOY_X = 1}, … {@code HOST_EXIT = 22}, and since M13 {@code TS_CMD = 23} …
 *     {@code TS_STATUS = 27});</li>
 * <li>{@code TILES}, {@code MAP} and {@code SPRITES}.</li>
 * </ul>
 * Built-in names are case-sensitive, like labels, and can't be redefined.
 */
public final class BuiltinSymbols {

    /** ROM routine names in jump-table order; entry i is at ROM_START + 2·i. */
    private static final String[] ROM_ROUTINES = {
        "RESET", "PUTCHAR", "PRINT", "PRINTNUM", "CLS", "GETKEY", "LOCATE",
        "DISKSAVE", "DISKLOAD", "DISKDIR", "DISKDEL",
    };

    private static final Map<String, Integer> ALL;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < ROM_ROUTINES.length; i++) {
            m.put(ROM_ROUTINES[i], MemoryMap.ROM_START + 2 * i);
        }
        m.put("IO_BASE", MemoryMap.IO_BASE);
        for (int k = 0; k < IoRegisters.DEFINED_COUNT; k++) {
            m.put(IoRegisters.nameOf(k), k);
        }
        m.put("TILES", MemoryMap.TILES);
        m.put("MAP", MemoryMap.MAP);
        m.put("SPRITES", MemoryMap.SPRITES);
        ALL = Collections.unmodifiableMap(m);
    }

    private BuiltinSymbols() {
    }

    /** All built-in symbols and their values, in a stable order. */
    public static Map<String, Integer> all() {
        return ALL;
    }

    /** The value of a built-in symbol, or null. */
    static Integer get(String name) {
        return ALL.get(name);
    }
}
