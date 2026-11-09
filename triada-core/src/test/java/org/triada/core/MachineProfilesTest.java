package org.triada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.MachineProfile.Device;

/** The TRIADA-85 profile holds exactly the numbers of docs/SPEC.md (M16). */
class MachineProfilesTest {

    private static final MachineProfile P = MachineProfiles.TRIADA_85;

    @Test
    void nameAndVersion() {
        assertEquals("triada-85", P.id());
        assertEquals("TRIADA-85", P.name());
        assertEquals("1.1", P.specVersion());
    }

    @Test
    void memoryMapIsSpec22And25() {
        assertEquals(0, P.ramStart());
        assertEquals(59_049, P.ramSize());                  // 3^10
        assertEquals(59_048, P.ramEnd());
        assertEquals(-19_683, P.romStart());
        assertEquals(19_683, P.romSize());                  // 3^9
        assertEquals(-1, P.romEnd());
        assertEquals(-20_412, P.ioBase());
        assertEquals(729, P.ioSize());                      // 3^6
        assertEquals(-19_684, P.ioEnd());
        assertEquals(-59_048, P.extRomStart());
        assertEquals(-20_413, P.extRomEnd());
        assertEquals(38_636, P.extRomSize());
    }

    @Test
    void ramLayoutIsSpec23() {
        assertEquals(243, P.programStart());                // 3^5
        assertEquals(40_000, P.tiles());
        assertEquals(44_000, P.map());
        assertEquals(44_600, P.sprites());
        assertEquals(45_000, P.freeStart());
        assertEquals(59_049, P.stackTop());
    }

    @Test
    void timingIsSpec6() {
        assertEquals(59_049, P.cyclesPerFrame());           // 3^10
        assertEquals(60, P.framesPerSecond());
    }

    @Test
    void videoIsSpec4() {
        assertEquals(216, P.screenWidth());
        assertEquals(144, P.screenHeight());
        assertEquals(12, P.border());                       // D-020
        assertEquals(240, P.frameWidth());
        assertEquals(168, P.frameHeight());
        assertEquals(8, P.tileSize());
        assertEquals(243, P.tileCount());
        assertEquals(27, P.spriteCount());
        assertEquals(27, P.mapColumns());
        assertEquals(18, P.mapRows());
    }

    @Test
    void soundDevicesAndRomsAreSpec5To8() {
        assertEquals(3, P.voices());
        assertEquals(1_200, P.modemBitsPerSecond());            // SPEC §5.2
        assertEquals(2, P.modemCharsPerFrame());
        assertEquals(EnumSet.allOf(Device.class), P.devices());
        assertEquals(List.of("main", "tribasic"), P.romImages());
    }

    @Test
    void foundByIdInAnyCase() {
        assertSame(P, MachineProfiles.byId("triada-85"));
        assertSame(P, MachineProfiles.byId("TRIADA-85"));
        assertEquals(List.of(P), MachineProfiles.all());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MachineProfiles.byId("triada-88"));
        assertTrue(e.getMessage().contains("known: triada-85"), e.getMessage());
    }

    @Test
    void overlappingRegionsAreRefused() {
        // RAM 0 ... 59,048 and a ROM that starts inside it.
        assertThrows(IllegalArgumentException.class, () -> new MachineProfile("bad", "BAD", "1.1",
                0, 59_049, 59_000, 100, 0, 0, -20_412, 729, 243, 40_000, 44_000, 44_600, 45_000,
                59_049, 60, 216, 144, 12, 8, 243, 27, 3, 1_200, EnumSet.allOf(Device.class), List.of()));
    }
}
