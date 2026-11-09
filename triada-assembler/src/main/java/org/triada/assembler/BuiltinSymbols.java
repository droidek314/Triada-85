package org.triada.assembler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.triada.core.MachineProfile;
import org.triada.core.MachineProfiles;
import org.triada.core.io.IoRegisters;

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
 * <li>{@code TILES}, {@code MAP} and {@code SPRITES};</li>
 * <li>since M16, the rest of the memory map and screen a program may need:
 *     {@code ROM_START}, {@code EXT_ROM_START}, {@code PROGRAM_START},
 *     {@code STACK_TOP}, {@code MAP_COLS}, {@code MAP_ROWS} and
 *     {@code MAP_CELLS}.</li>
 * </ul>
 * Built-in names are case-sensitive, like labels, and can't be redefined.
 *
 * <p>The values come from a {@link MachineProfile} (M16): {@link #all()} is
 * the TRIADA-85's, {@link #of(MachineProfile)} any model's. The jump table
 * and the I/O register numbers are the same on every model.
 */
public final class BuiltinSymbols {

    /** ROM routine names in jump-table order; entry i is at ROM_START + 2·i. */
    private static final String[] ROM_ROUTINES = {
        "RESET", "PUTCHAR", "PRINT", "PRINTNUM", "CLS", "GETKEY", "LOCATE",
        "DISKSAVE", "DISKLOAD", "DISKDIR", "DISKDEL",
    };

    private static final Map<String, Integer> TRIADA_85 = build(MachineProfiles.TRIADA_85);

    private BuiltinSymbols() {
    }

    /** All built-in symbols of the TRIADA-85 and their values, in a stable order. */
    public static Map<String, Integer> all() {
        return TRIADA_85;
    }

    /** All built-in symbols of the model {@code profile}, in a stable order. */
    public static Map<String, Integer> of(MachineProfile profile) {
        return profile == MachineProfiles.TRIADA_85 ? TRIADA_85 : build(profile);
    }

    private static Map<String, Integer> build(MachineProfile p) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < ROM_ROUTINES.length; i++) {
            m.put(ROM_ROUTINES[i], p.romStart() + 2 * i);
        }
        m.put("IO_BASE", p.ioBase());
        for (int k = 0; k < IoRegisters.DEFINED_COUNT; k++) {
            m.put(IoRegisters.nameOf(k), k);
        }
        m.put("TILES", p.tiles());
        m.put("MAP", p.map());
        m.put("SPRITES", p.sprites());
        m.put("ROM_START", p.romStart());
        m.put("EXT_ROM_START", p.extRomStart());
        m.put("PROGRAM_START", p.programStart());
        m.put("STACK_TOP", p.stackTop());
        m.put("MAP_COLS", p.mapColumns());
        m.put("MAP_ROWS", p.mapRows());
        m.put("MAP_CELLS", p.mapColumns() * p.mapRows());
        return Collections.unmodifiableMap(m);
    }
}
