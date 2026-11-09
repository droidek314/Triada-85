package org.triada.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.MachineProfile;
import org.triada.core.MachineProfiles;
import org.triada.core.TriImage;

/** The built-in symbols and the default origin come from the profile assembled for (M16). */
class ProfileSymbolsTest {

    private static final String SOURCE =
            ".word TILES, MAP, SPRITES, IO_BASE, PUTCHAR, ROM_START, EXT_ROM_START, "
            + "PROGRAM_START, STACK_TOP, MAP_COLS, MAP_ROWS, MAP_CELLS\n";

    private static TriImage assemble(MachineProfile p) throws AssemblyException {
        return new Assembler(SourceLoader.NONE, p).assembleSource("t.tasm", SOURCE).image();
    }

    @Test
    void theTriada85IsTheDefault() throws AssemblyException {
        TriImage defaulted = new Assembler(SourceLoader.NONE).assembleSource("t.tasm", SOURCE).image();
        TriImage explicit = assemble(MachineProfiles.TRIADA_85);
        assertEquals(243, defaulted.loadAddress());
        assertEquals(List.of(40_000, 44_000, 44_600, -20_412, -19_681, -19_683, -59_048, 243, 59_049, 27, 18, 486),
                words(defaulted));
        assertEquals(words(defaulted), words(explicit));
    }

    @Test
    void anotherModelGetsItsOwnValues() throws AssemblyException {
        MachineProfile p = new MachineProfile("test", "TEST", "1.1",
                0, 19_683, -19_683, 19_683, -59_048, 0, -20_412, 729,
                100, 10_000, 14_000, 14_600, 15_000, 1_000, 50, 120, 96, 4, 8, 243, 27, 1,
                EnumSet.allOf(MachineProfile.Device.class), List.of("main"));
        TriImage image = assemble(p);
        assertEquals(100, image.loadAddress());
        assertEquals(List.of(10_000, 14_000, 14_600, -20_412, -19_681, -19_683, -59_048, 100, 19_683, 15, 12, 180),
                words(image));
    }

    private static List<Integer> words(TriImage image) {
        Integer[] w = new Integer[image.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = image.word(i);
        }
        return List.of(w);
    }
}
