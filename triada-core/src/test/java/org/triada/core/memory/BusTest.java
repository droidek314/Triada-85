package org.triada.core.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.triada.core.io.IoDevice;
import org.triada.core.ternary.Word;

class BusTest {

    /** A fake device that answers 1000 + k to reads, 2000 + k to peeks, and records writes. */
    static final class RecordingDevice implements IoDevice {
        int lastWriteK = -1;
        int lastWriteValue;
        int reads;

        @Override
        public int read(int k) {
            reads++;
            return 1000 + k;
        }

        @Override
        public int peek(int k) {
            return 2000 + k;
        }

        @Override
        public void write(int k, int value) {
            lastWriteK = k;
            lastWriteValue = value;
        }
    }

    private final Bus bus = new Bus();

    // ------------------------------------------------------------ power-on

    @Test
    void powerOnRamIsAllZero() {
        for (int a = MemoryMap.RAM_START; a <= MemoryMap.RAM_END; a++) {
            if (bus.read(a) != 0) {
                fail("RAM " + a + " not 0 at power-on");
            }
        }
    }

    // ------------------------------------------------------------ RAM

    @ParameterizedTest(name = "RAM address {0} reads back what was written")
    @ValueSource(ints = {0, 1, 243, 59_047, 59_048})
    void ramBoundariesAreReadWrite(int address) {
        bus.write(address, -12_345);
        assertEquals(-12_345, bus.read(address));
        bus.write(address, Word.MAX);
        assertEquals(Word.MAX, bus.read(address));
    }

    @Test
    void everyRamCellIsIndependent() {
        // Give every cell a different value, then check they all kept theirs.
        for (int a = MemoryMap.RAM_START; a <= MemoryMap.RAM_END; a++) {
            bus.write(a, Word.wrap(a * 7L - 100_000));
        }
        for (int a = MemoryMap.RAM_START; a <= MemoryMap.RAM_END; a++) {
            if (bus.read(a) != Word.wrap(a * 7L - 100_000)) {
                fail("RAM " + a + " lost its value");
            }
        }
    }

    // ------------------------------------------------------------ ROM

    @Test
    void romBoundariesAreReadOnly() {
        int[] image = new int[MemoryMap.ROM_SIZE];
        image[0] = 111;                       // address −19,683
        image[1] = 112;                       // address −19,682
        image[MemoryMap.ROM_SIZE - 2] = 998;  // address −2
        image[MemoryMap.ROM_SIZE - 1] = 999;  // address −1
        bus.loadRom(image);

        int[][] cases = {{-19_683, 111}, {-19_682, 112}, {-2, 998}, {-1, 999}};
        for (int[] c : cases) {
            assertEquals(c[1], bus.read(c[0]), "ROM " + c[0]);
            bus.write(c[0], 5);               // the CPU cannot change ROM
            assertEquals(c[1], bus.read(c[0]), "ROM " + c[0] + " after write");
        }
    }

    @Test
    void writesJustOutsideRomLandInTheirOwnRegions() {
        RecordingDevice dev = new RecordingDevice();
        bus.attach(0, 729, dev);
        bus.write(-19_684, 42);               // one below ROM: last I/O register
        assertEquals(728, dev.lastWriteK);
        bus.write(0, 43);                     // one above ROM: first RAM word
        assertEquals(43, bus.read(0));
    }

    @Test
    void loadRomClearsTheRestAndRejectsBadImages() {
        bus.loadRom(new int[] {1, 2, 3});
        bus.loadRom(new int[] {9});
        assertEquals(9, bus.read(-19_683));
        assertEquals(0, bus.read(-19_682), "a shorter image clears the old words");
        assertThrows(IllegalArgumentException.class, () -> bus.loadRom(new int[MemoryMap.ROM_SIZE + 1]));
        assertThrows(IllegalArgumentException.class, () -> bus.loadRom(new int[] {Word.MAX + 1}));
    }

    // ------------------------------------------------------------ I/O

    @Test
    void ioRegistersMapToKFromFirstToLast() {
        RecordingDevice dev = new RecordingDevice();
        bus.attach(0, 729, dev);
        assertEquals(1000, bus.read(-20_412), "IO_BASE is register 0");
        assertEquals(1001, bus.read(-20_411));
        assertEquals(1727, bus.read(-19_685));
        assertEquals(1728, bus.read(-19_684), "last I/O address is register 728");

        bus.write(-20_412, 7);
        assertEquals(0, dev.lastWriteK);
        assertEquals(7, dev.lastWriteValue);
        bus.write(-19_684, -7);
        assertEquals(728, dev.lastWriteK);
        assertEquals(-7, dev.lastWriteValue);
    }

    @Test
    void addressesJustOutsideIoDoNotReachTheDevice() {
        RecordingDevice dev = new RecordingDevice();
        bus.attach(0, 729, dev);
        assertEquals(0, bus.read(-20_413));   // extension ROM (empty)
        assertEquals(0, bus.read(-19_683));   // ROM (all 0)
        bus.write(-20_413, 5);
        bus.write(-19_683, 5);
        assertEquals(-1, dev.lastWriteK);
        assertEquals(0, dev.reads);
    }

    @Test
    void registersWithoutADeviceReadZeroAndIgnoreWrites() {
        bus.write(-20_412 + 100, 55);
        assertEquals(0, bus.read(-20_412 + 100));
        assertEquals(0, bus.peek(-20_412 + 100));
    }

    @Test
    void peekUsesTheDevicesSideEffectFreePath() {
        RecordingDevice dev = new RecordingDevice();
        bus.attach(6, 1, dev);
        assertEquals(2006, bus.peek(-20_406));
        assertEquals(0, dev.reads);
        assertEquals(1006, bus.read(-20_406));
        assertEquals(1, dev.reads);
    }

    @Test
    void peekOutsideIoIsAPlainRead() {
        bus.write(500, 77);
        assertEquals(77, bus.peek(500));
        assertEquals(0, bus.peek(100_000));
    }

    @Test
    void attachRejectsOverlapsBadRangesAndNull() {
        RecordingDevice dev = new RecordingDevice();
        bus.attach(10, 9, dev);               // sound voices, k = 10 … 18
        assertSame(dev, bus.deviceAt(10));
        assertSame(dev, bus.deviceAt(18));
        assertNull(bus.deviceAt(19));
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> bus.attach(18, 2, new RecordingDevice()));
        assertTrue(e.getMessage().contains("18 (V3_WAVE) already has a device"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> bus.attach(-1, 1, dev));
        assertThrows(IllegalArgumentException.class, () -> bus.attach(728, 2, dev));
        assertThrows(IllegalArgumentException.class, () -> bus.attach(30, 0, dev));
        assertThrows(IllegalArgumentException.class, () -> bus.attach(30, 1, null));
        assertNull(bus.deviceAt(30), "a failed attach changes nothing");
    }

    // ------------------------------------------------------------ unmapped

    @ParameterizedTest(name = "unmapped address {0} reads 0 and ignores writes")
    @ValueSource(ints = {-265_720, -265_719, -59_050, -59_049, 59_049, 59_050, 265_719, 265_720})
    void unmappedReadsZeroAndIgnoresWrites(int address) {
        bus.write(address, 99);
        assertEquals(0, bus.read(address));
    }

    @Test
    void unmappedWritesDoNotWrapIntoRam() {
        bus.write(59_049, 99);                // one past the end of RAM
        assertEquals(0, bus.read(0));
        assertEquals(0, bus.read(59_048));
    }

    // ------------------------------------------------------------ invalid input

    @Test
    void addressesOutsideTheWordRangeAreBugs() {
        assertThrows(IllegalArgumentException.class, () -> bus.read(265_721));
        assertThrows(IllegalArgumentException.class, () -> bus.read(-265_721));
        assertThrows(IllegalArgumentException.class, () -> bus.write(265_721, 0));
        assertThrows(IllegalArgumentException.class, () -> bus.peek(-265_721));
    }

    @Test
    void valuesOutsideTheWordRangeAreBugs() {
        assertThrows(IllegalArgumentException.class, () -> bus.write(0, Word.MAX + 1));
        assertThrows(IllegalArgumentException.class, () -> bus.write(-1, Word.MIN - 1));
    }

    // ------------------------------------------------------------ host loading

    @Test
    void loadRamPlacesAProgramAndChecksBounds() {
        bus.loadRam(243, new int[] {10, 20, 30});
        assertEquals(10, bus.read(243));
        assertEquals(30, bus.read(245));
        bus.loadRam(59_048, new int[] {5});
        assertEquals(5, bus.read(59_048));
        assertThrows(IllegalArgumentException.class, () -> bus.loadRam(59_048, new int[] {1, 2}));
        assertThrows(IllegalArgumentException.class, () -> bus.loadRam(-1, new int[] {1}));
        assertThrows(IllegalArgumentException.class, () -> bus.loadRam(0, new int[] {Word.MAX + 1}));
    }

    @Test
    void clearRamResetsRamButKeepsRom() {
        bus.loadRom(new int[] {77});
        bus.write(1000, 5);
        bus.clearRam();
        assertEquals(0, bus.read(1000));
        assertEquals(77, bus.read(-19_683));
    }

    @Test
    void theExtensionRomIsReadOnlyAndLoadedByTheHost() {
        bus.loadExtensionRom(new int[] {11, 22, 33});
        assertEquals(11, bus.read(-59_048), "first word");
        assertEquals(33, bus.read(-59_046));
        assertEquals(0, bus.read(-20_413), "last word, not loaded: 0");
        bus.write(-59_048, 5);
        assertEquals(11, bus.read(-59_048), "the CPU can't write it");
        assertEquals(0, bus.read(-59_049), "one below: unmapped");
        assertThrows(IllegalArgumentException.class, () -> bus.loadExtensionRom(new int[38_637]));
    }
}
