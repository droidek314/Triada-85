package org.triada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.MachineProfile.Device;
import org.triada.core.cpu.Instruction;
import org.triada.core.io.IoRegisters;

/**
 * A machine built from a made-up model follows that model, not the TRIADA-85
 * (M16): if any number were still hard-coded somewhere, one of these would fail.
 */
class ModifiedProfileTest {

    /** A small test model: a third of the RAM, video memory moved, a smaller screen, one voice, no drive. */
    static final MachineProfile SMALL = new MachineProfile(
            "test-small", "TEST-SMALL", "1.1",
            0, 19_683,                      // RAM 0 ... 19,682
            -19_683, 19_683,                // main ROM as on the TRIADA-85
            -59_048, 0,                     // no extension ROM
            -20_412, 729,
            100,                            // programs at 100
            10_000, 14_000, 14_600, 15_000, // tiles, map, sprites, free
            1_000, 50,                      // 1,000 cycles a frame, 50 frames a second
            120, 96, 4,                     // 120 x 96 screen, border 4
            8, 243, 27,
            1,                              // one voice
            EnumSet.of(Device.SYSTEM, Device.INPUT, Device.VIDEO, Device.SOUND, Device.DEBUG),
            List.of("main"));

    private final Machine machine = new Machine(SMALL);

    @Test
    void ramEndsWhereTheProfileSays() {
        machine.bus().write(19_682, 7);
        machine.bus().write(19_683, 7);                     // RAM on the TRIADA-85, unmapped here
        assertEquals(7, machine.bus().read(19_682));
        assertEquals(0, machine.bus().read(19_683));
        assertEquals(SMALL, machine.profile());
    }

    @Test
    void programsGetTheProfilesStack() {
        machine.loadProgram(new TriImage(100, new int[] {1, 2, 3}));
        assertEquals(100, machine.cpu().pc());
        assertEquals(19_683, machine.cpu().register(8));    // SP = one past the end of this RAM
    }

    @Test
    void aFrameHasTheProfilesCycles() {
        int[] nops = new int[SMALL.romSize()];
        Arrays.fill(nops, Instruction.encode(Instruction.NOP, 0, 0, 0));
        machine.bus().loadRom(nops);
        machine.powerOn();
        assertEquals(SMALL.romStart(), machine.cpu().pc());
        assertEquals(1_000, machine.runFrame());            // one cycle per NOP
        assertEquals(1, machine.bus().read(SMALL.ioBase() + IoRegisters.FRAME));
    }

    @Test
    void videoUsesTheProfilesScreenAndMap() {
        assertEquals(128, machine.video().frameWidth());
        assertEquals(104, machine.video().frameHeight());
        assertEquals(128 * 104, machine.video().frame().length);
        // Tile 1 = all pixel value +13 (the word 265,720 is four pixels of +13), shown in map cell 0.
        for (int i = 0; i < 16; i++) {
            machine.bus().write(SMALL.tiles() + 16 + i, 265_720);
        }
        machine.bus().write(SMALL.map(), 1);
        machine.runFrame();
        assertEquals(13, machine.video().screenPixel(0, 0));
        assertEquals(0, machine.video().screenPixel(8, 0));
    }

    @Test
    void soundHasOneVoiceAtFiftyFrames() {
        assertEquals(44_100 / 50, machine.sound().frameSamples().length);
        int v1 = SMALL.ioBase() + IoRegisters.V1_FREQ;
        machine.bus().write(v1, 440);
        machine.bus().write(v1 + 3, 440);                   // V2_FREQ: no second voice here
        assertEquals(440, machine.bus().read(v1));
        assertEquals(0, machine.bus().read(v1 + 3));
    }

    @Test
    void aMissingDeviceIsAnEmptySlot() {
        int status = SMALL.ioBase() + IoRegisters.TS_STATUS;
        machine.bus().write(status, 5);
        assertEquals(0, machine.bus().read(status));
    }
}
