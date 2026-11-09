package org.triada.core;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.triada.core.MachineProfile.Device;

/**
 * The TRIADA models there are (M16). Today one: the TRIADA-85.
 */
public final class MachineProfiles {

    /**
     * The TRIADA-85: every number here is the one in {@code docs/SPEC.md}
     * (§2.2, §2.3, §2.5, §4, §5, §6, §7), and {@code MachineProfilesTest} checks it.
     */
    public static final MachineProfile TRIADA_85 = new MachineProfile(
            "triada-85", "TRIADA-85", "1.1",
            0, 59_049,                      // RAM: 0 ... 59,048 (3^10 words)
            -19_683, 19_683,                // main ROM: -19,683 ... -1 (3^9)
            -59_048, 38_636,                // extension ROM: -59,048 ... -20,413 (TRIBASIC)
            -20_412, 729,                   // I/O registers: -20,412 ... -19,684 (3^6)
            243,                            // programs load at 3^5
            40_000, 44_000, 44_600,         // tiles, tile map, sprite table
            45_000,                         // free / stack area
            59_049, 60,                     // 3^10 cycles per frame, 60 frames per second
            216, 144, 12,                   // screen 216 x 144, border 12
            8, 243, 27,                     // 8 x 8 tiles, 243 of them, 27 sprites
            3,                              // three voices
            EnumSet.allOf(Device.class),
            List.of("main", "tribasic"));

    private static final List<MachineProfile> ALL = List.of(TRIADA_85);

    private MachineProfiles() {
    }

    /** Every known model. */
    public static List<MachineProfile> all() {
        return ALL;
    }

    /**
     * The model called {@code id} (as on the command line: "triada-85"; case
     * doesn't matter).
     *
     * @throws IllegalArgumentException naming the known models, if there is none
     */
    public static MachineProfile byId(String id) {
        for (MachineProfile p : ALL) {
            if (p.id().equals(id.toLowerCase(Locale.ROOT))) {
                return p;
            }
        }
        StringBuilder known = new StringBuilder();
        for (MachineProfile p : ALL) {
            known.append(known.length() == 0 ? "" : ", ").append(p.id());
        }
        throw new IllegalArgumentException("no such model '" + id + "' (known: " + known + ")");
    }
}
