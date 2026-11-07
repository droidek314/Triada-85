package org.triada.core.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.triada.core.memory.MemoryMap.Region;

class MemoryMapTest {

    @Test
    void constantsMatchSpecTables() {
        // SPEC §2.2, numbers copied literally from the table.
        assertEquals(-20_412, MemoryMap.IO_BASE);
        assertEquals(-19_684, MemoryMap.IO_END);
        assertEquals(-19_683, MemoryMap.ROM_START);
        assertEquals(-1, MemoryMap.ROM_END);
        assertEquals(0, MemoryMap.RAM_START);
        assertEquals(59_048, MemoryMap.RAM_END);
        assertEquals(729, MemoryMap.IO_SIZE);
        assertEquals(19_683, MemoryMap.ROM_SIZE);
        assertEquals(59_049, MemoryMap.RAM_SIZE);
        // SPEC §2.3
        assertEquals(243, MemoryMap.PROGRAM_START);
        assertEquals(40_000, MemoryMap.TILES);
        assertEquals(44_000, MemoryMap.MAP);
        assertEquals(44_600, MemoryMap.SPRITES);
        assertEquals(45_000, MemoryMap.FREE_START);
        assertEquals(59_049, MemoryMap.STACK_TOP);
    }

    @ParameterizedTest(name = "address {0} is {1}")
    @CsvSource({
        // first and last address of every region, and one step beyond each
        "-265720, UNMAPPED",
        "-265719, UNMAPPED",
        "-20414, UNMAPPED",
        "-20413, UNMAPPED",
        "-20412, IO",
        "-20411, IO",
        "-19685, IO",
        "-19684, IO",
        "-19683, ROM",
        "-19682, ROM",
        "-2, ROM",
        "-1, ROM",
        "0, RAM",
        "1, RAM",
        "59047, RAM",
        "59048, RAM",
        "59049, UNMAPPED",
        "59050, UNMAPPED",
        "265719, UNMAPPED",
        "265720, UNMAPPED",
    })
    void regionBoundaries(int address, Region expected) {
        assertEquals(expected, MemoryMap.regionOf(address));
    }

    @Test
    void addressesOutsideTheWordRangeAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MemoryMap.regionOf(-265_721));
        assertThrows(IllegalArgumentException.class, () -> MemoryMap.regionOf(265_721));
    }

    @Test
    void everyAddressIsInExactlyTheRegionTheSpecSays() {
        for (int a = -265_720; a <= 265_720; a++) {
            Region expected;
            if (a <= -20_413) {
                expected = Region.UNMAPPED;
            } else if (a <= -19_684) {
                expected = Region.IO;
            } else if (a <= -1) {
                expected = Region.ROM;
            } else if (a <= 59_048) {
                expected = Region.RAM;
            } else {
                expected = Region.UNMAPPED;
            }
            if (MemoryMap.regionOf(a) != expected) {
                fail("address " + a + ": " + MemoryMap.regionOf(a) + ", expected " + expected);
            }
        }
    }

    @Test
    void ramLayoutFitsInRamAndDoesNotOverlap() {
        int tilesEnd = MemoryMap.TILES + 243 * 16 - 1;     // 43,887 (SPEC §2.3)
        int mapEnd = MemoryMap.MAP + 27 * 18 - 1;          // 44,485
        int spritesEnd = MemoryMap.SPRITES + 27 * 4 - 1;   // 44,707
        assertEquals(43_887, tilesEnd);
        assertEquals(44_485, mapEnd);
        assertEquals(44_707, spritesEnd);
        assertEquals(true, tilesEnd < MemoryMap.MAP && mapEnd < MemoryMap.SPRITES
                && spritesEnd < MemoryMap.FREE_START && MemoryMap.FREE_START <= MemoryMap.RAM_END);
    }
}
