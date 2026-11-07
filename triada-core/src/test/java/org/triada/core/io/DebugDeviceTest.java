package org.triada.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.cpu.Instruction;
import org.triada.core.memory.MemoryMap;

class DebugDeviceTest {

    private static final int DEBUG_CHAR = -20_392;   // SPEC §5 table
    private static final int DEBUG_NUM = -20_391;
    private static final int HOST_EXIT = -20_390;

    private final Machine machine = new Machine();

    @Test
    void addressesMatchTheSpec() {
        assertEquals(DEBUG_CHAR, MemoryMap.IO_BASE + IoRegisters.DEBUG_CHAR);
        assertEquals(HOST_EXIT, MemoryMap.IO_BASE + IoRegisters.HOST_EXIT);
    }

    @Test
    void debugCharPrintsCharacters() {
        for (char c : "HI!\n".toCharArray()) {
            machine.bus().write(DEBUG_CHAR, c);
        }
        assertEquals("HI!\n", machine.debug().takeOutput());
        assertEquals("", machine.debug().takeOutput(), "taking empties the buffer");
    }

    @Test
    void unprintableValuesShowAsNumbers() {
        machine.bus().write(DEBUG_CHAR, 0);
        machine.bus().write(DEBUG_CHAR, -5);
        machine.bus().write(DEBUG_CHAR, 127);
        machine.bus().write(DEBUG_CHAR, '\t');
        machine.bus().write(DEBUG_CHAR, 233);
        assertEquals("<0><-5><127>\t<233>", machine.debug().takeOutput());
    }

    @Test
    void debugNumPrintsDecimalAndNewline() {
        machine.bus().write(DEBUG_NUM, -265_720);
        machine.bus().write(DEBUG_NUM, 0);
        machine.bus().write(DEBUG_NUM, 42);
        assertEquals("-265720\n0\n42\n", machine.debug().takeOutput());
    }

    @Test
    void hostExitRecordsTheCode() {
        assertFalse(machine.debug().exitRequested());
        machine.bus().write(HOST_EXIT, -3);
        assertTrue(machine.debug().exitRequested());
        assertEquals(-3, machine.debug().exitCode());
    }

    @Test
    void registersAreWriteOnly() {
        machine.bus().write(DEBUG_NUM, 7);
        assertEquals(0, machine.bus().read(DEBUG_CHAR));
        assertEquals(0, machine.bus().read(DEBUG_NUM));
        assertEquals(0, machine.bus().read(HOST_EXIT));
    }

    @Test
    void hostExitStopsTheCpuRightAway() {
        // 243: LDI R1, IO_BASE ; ST R0, [R1+HOST_EXIT] ; ADDI R2, 1 ; JMP 247 (endless)
        int r1 = Instruction.reg(1);
        machine.bus().loadRam(243, new int[] {
            Instruction.encode(Instruction.LDI, r1, 0, 0), MemoryMap.IO_BASE,
            Instruction.encode(Instruction.ST, Instruction.reg(0), r1, IoRegisters.HOST_EXIT),
            Instruction.encode(Instruction.ADDI, Instruction.reg(2), 0, 1),
            Instruction.encode(Instruction.JMP, 0, 0, 0), 247,
        });
        machine.cpu().setPc(243);
        assertEquals(2, machine.runFrame(), "LDI and ST, then the frame ends");
        assertEquals(0, machine.cpu().register(2), "nothing ran after HOST_EXIT");
        assertTrue(machine.debug().exitRequested());
    }

    @Test
    void endFrameHookOnlyEndsTheCurrentFrame() {
        // A device ending one frame must not make later frames end early.
        machine.cpu().endFrame();
        machine.bus().loadRam(243, new int[] {
            Instruction.encode(Instruction.ADDI, Instruction.reg(2), 0, 1),
            Instruction.encode(Instruction.JMP, 0, 0, 0), 243,
        });
        machine.cpu().setPc(243);
        assertEquals(Machine.CYCLES_PER_FRAME, machine.runFrame());
    }

    @Test
    void powerOnForgetsOutputAndExit() {
        machine.bus().write(DEBUG_CHAR, 'X');
        machine.bus().write(HOST_EXIT, 1);
        machine.powerOn();
        assertEquals("", machine.debug().takeOutput());
        assertFalse(machine.debug().exitRequested());
    }
}
