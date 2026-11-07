package org.triada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.triada.core.cpu.Instruction;
import org.triada.core.io.IoRegisters;
import org.triada.core.io.SystemDevice;
import org.triada.core.memory.MemoryMap;
import org.triada.core.ternary.Word;

class MachineTest {

    private static final int FRAME = MemoryMap.IO_BASE + IoRegisters.FRAME;   // −20,412
    private static final int RAND = MemoryMap.IO_BASE + IoRegisters.RAND;     // −20,393

    private final Machine machine = new Machine();

    @Test
    void timingConstantsMatchSpec() {
        assertEquals(60, Machine.FRAMES_PER_SECOND);
        assertEquals(59_049, Machine.CYCLES_PER_FRAME);
    }

    @Test
    void frameCountsUpEvenWhenTheCpuIsHalted() {
        // The empty ROM is all HLT, so the CPU halts on its first instruction.
        assertEquals(0, machine.bus().read(FRAME));
        machine.runFrame();
        assertTrue(machine.cpu().isHalted());
        for (int i = 0; i < 9; i++) {
            assertEquals(0, machine.runFrame(), "a halted CPU uses no cycles");
        }
        assertEquals(10, machine.bus().read(FRAME));
    }

    @Test
    void frameIsReadOnlyAndCanBeReset() {
        machine.bus().write(FRAME, 500);
        assertEquals(0, machine.bus().read(FRAME));
        for (int i = 0; i < 3; i++) {
            machine.system().advanceFrame();
        }
        assertEquals(3, machine.system().frame());
        machine.system().resetFrame();
        assertEquals(0, machine.system().frame());
    }

    @Test
    void frameCounterWrapsFromMaxToMin() {
        SystemDevice dev = new SystemDevice();
        // advanceFrame 265,720 times reaches MAX; once more wraps to MIN.
        for (int i = 0; i < Word.MAX; i++) {
            dev.advanceFrame();
        }
        assertEquals(Word.MAX, dev.frame());
        dev.advanceFrame();
        assertEquals(Word.MIN, dev.frame());
    }

    @Test
    void randGivesValidRepeatableWordsAndPeekDoesNotAdvance() {
        Machine other = new Machine();
        int first = machine.bus().peek(RAND);
        assertEquals(first, machine.bus().peek(RAND), "peeking twice shows the same value");
        assertEquals(first, machine.bus().read(RAND), "read returns what peek showed");
        int second = machine.bus().read(RAND);
        assertNotEquals(first, second);
        assertEquals(first, other.bus().read(RAND), "same seed, same sequence");
        assertEquals(second, other.bus().read(RAND));
    }

    @Test
    void randCoversTheWholeRangeRoughlyEvenly() {
        int negative = 0;
        int positive = 0;
        long sum = 0;
        int n = 300_000;
        for (int i = 0; i < n; i++) {
            int v = machine.bus().read(RAND);
            assertTrue(Word.isValid(v));
            if (v < 0) {
                negative++;
            } else if (v > 0) {
                positive++;
            }
            sum += v;
        }
        // Each half should get about 150,000; the mean should be near 0.
        assertTrue(Math.abs(negative - positive) < 3_000, negative + " vs " + positive);
        assertTrue(Math.abs(sum / n) < 3_000, "mean " + sum / n);
    }

    @Test
    void seedingChangesTheSequence() {
        int a = machine.bus().peek(RAND);
        machine.system().seed(12_345);
        assertNotEquals(a, machine.bus().peek(RAND));
        machine.system().seed(0);   // 0 falls back to the default seed
        assertEquals(a, machine.bus().peek(RAND));
    }

    @Test
    void inputDeviceIsPluggedIn() {
        machine.input().pushKey('K');
        assertEquals('K', machine.bus().read(MemoryMap.IO_BASE + IoRegisters.KEY));
    }

    @Test
    void powerOnClearsRamCpuFrameAndKeysButKeepsRom() {
        machine.bus().loadRom(new int[] {Instruction.encode(Instruction.NOP, 0, 0, 0)});
        machine.bus().write(1000, 5);
        machine.input().pushKey('A');
        machine.runFrame();
        machine.powerOn();
        assertEquals(0, machine.bus().read(1000));
        assertEquals(MemoryMap.ROM_START, machine.cpu().pc());
        assertEquals(0, machine.system().frame());
        assertEquals(0, machine.input().queuedKeys());
        assertEquals(Instruction.encode(Instruction.NOP, 0, 0, 0), machine.bus().read(MemoryMap.ROM_START));
    }

    @Test
    void aProgramCanWaitForTheNextFrameByWatchingFrame() {
        // 243: LDI R1, IO_BASE ; LD R0, [R1+FRAME] ; WAIT ; LD R2, [R1+FRAME] ; HLT
        int r0 = Instruction.reg(0);
        int r1 = Instruction.reg(1);
        int r2 = Instruction.reg(2);
        machine.bus().loadRam(243, new int[] {
            Instruction.encode(Instruction.LDI, r1, 0, 0), MemoryMap.IO_BASE,
            Instruction.encode(Instruction.LD, r0, r1, IoRegisters.FRAME),
            Instruction.encode(Instruction.WAIT, 0, 0, 0),
            Instruction.encode(Instruction.LD, r2, r1, IoRegisters.FRAME),
            Instruction.encode(Instruction.HLT, 0, 0, 0),
        });
        machine.cpu().setPc(243);
        machine.runFrame();
        machine.runFrame();
        assertEquals(0, machine.cpu().register(0));
        assertEquals(1, machine.cpu().register(2));
    }
}
