package org.triada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.core.cpu.Cpu;
import org.triada.core.cpu.Instruction;

/**
 * The machine's debugging support (M9): breakpoints that stop a frame in the
 * middle, resuming it, and single steps, all without changing what the
 * program computes or when its frames end.
 */
class MachineDebugTest {

    private Machine m;

    private static int i(int op, int rd, int rs, int imm) {
        return Instruction.encode(op, Instruction.reg(rd), Instruction.reg(rs), imm);
    }

    /** 243: ADDI R0,1 / 244: ADDI R1,1 / 245: JMP 243  -- a busy loop, never WAITs. */
    @BeforeEach
    void loadLoop() {
        m = new Machine();
        m.loadProgram(new TriImage(243, new int[] {
            i(Instruction.ADDI, 0, 4, 1), i(Instruction.ADDI, 1, 4, 1), i(Instruction.JMP, 4, 4, 0), 243,
        }));
    }

    private int frame() {
        return m.system().frame();
    }

    @Test
    void aBreakpointStopsBeforeTheInstructionAndLeavesTheFrameOpen() {
        m.setBreakpoint(244, true);
        int used = m.runFrame();
        assertEquals(1, used, "only ADDI R0 ran");
        assertTrue(m.stoppedAtBreakpoint());
        assertEquals(244, m.cpu().pc());
        assertEquals(1, m.cpu().register(0));
        assertEquals(0, m.cpu().register(1), "the instruction at the breakpoint has not run");
        assertEquals(0, frame(), "the frame is not finished");
        assertEquals(Machine.CYCLES_PER_FRAME - 1, m.cyclesLeftInFrame());
    }

    @Test
    void resumingRunsTheBreakpointInstructionAndStopsAgainNextTimeRound() {
        m.setBreakpoint(244, true);
        m.runFrame();
        m.runFrame();                                   // resume: ADDI R1, JMP, ADDI R0, stop again at 244
        assertEquals(4, m.cpu().cycles());
        assertEquals(1, m.cpu().register(1));
        assertEquals(2, m.cpu().register(0));
        assertTrue(m.stoppedAtBreakpoint());
    }

    @Test
    void aFrameInterruptedByBreakpointsStillGetsExactlyItsBudget() {
        m.setBreakpoint(244, true);
        for (int k = 0; k < 100; k++) {
            m.runFrame();                               // 100 stops in the first frame
        }
        m.setBreakpoint(244, false);
        m.runFrame();                                   // finish the frame
        assertFalse(m.stoppedAtBreakpoint());
        assertEquals(1, frame());
        assertEquals(Machine.CYCLES_PER_FRAME, m.cpu().cycles(), "59,049 cycles, breakpoints or not");
    }

    @Test
    void stepRunsExactlyOneInstruction() {
        m.step();
        m.step();
        assertEquals(245, m.cpu().pc());
        assertEquals(2, m.cpu().cycles());
        assertEquals(0, frame());
    }

    @Test
    void stepIgnoresABreakpointUnderThePc() {
        m.setBreakpoint(243, true);
        m.step();
        assertEquals(244, m.cpu().pc());
        assertFalse(m.stoppedAtBreakpoint());
    }

    @Test
    void steppingAWaitFinishesTheFrame() {
        Machine w = new Machine();
        w.loadProgram(new TriImage(243, new int[] {i(Instruction.WAIT, 4, 4, 0), i(Instruction.NOP, 4, 4, 0)}));
        w.step();
        assertEquals(1, w.system().frame(), "WAIT ends the frame");
        assertEquals(Machine.CYCLES_PER_FRAME, w.cyclesLeftInFrame(), "a fresh budget");
    }

    @Test
    void steppingTheLastCycleOfTheBudgetFinishesTheFrame() {
        // Step through the frame to its very last cycle, then step that one.
        // (Stepping is the safe way: breakpoint stops could jump over the "1 left" moment.)
        while (m.cyclesLeftInFrame() > 1) {
            m.step();
        }
        assertEquals(0, frame());
        m.step();
        assertEquals(1, frame());
        assertEquals(Machine.CYCLES_PER_FRAME, m.cpu().cycles());
    }

    @Test
    void steppingAHaltedCpuStillMovesTime() {
        Machine h = new Machine();          // empty ROM: HLT at the reset address
        h.runFrame();
        assertTrue(h.cpu().isHalted());
        int f = h.system().frame();
        h.step();
        assertEquals(f + 1, h.system().frame());
    }

    @Test
    void breakpointsCanBeClearedOneByOneOrAllAtOnce() {
        m.setBreakpoint(244, true);
        m.setBreakpoint(245, true);
        m.setBreakpoint(245, true);         // setting twice counts once
        assertTrue(m.isBreakpoint(245));
        m.setBreakpoint(244, false);
        m.setBreakpoint(245, false);
        assertFalse(m.isBreakpoint(245));
        m.runFrame();
        assertEquals(1, frame(), "no breakpoints left: a whole frame");
        m.setBreakpoint(-265_720, true);
        m.setBreakpoint(265_720, true);
        assertTrue(m.isBreakpoint(-265_720) && m.isBreakpoint(265_720));
        m.clearBreakpoints();
        assertFalse(m.isBreakpoint(265_720));
        assertThrows(IllegalArgumentException.class, () -> m.setBreakpoint(300_000, true));
    }

    @Test
    void cpuRunReportsWhyItStopped() {
        Cpu cpu = m.cpu();
        cpu.run(5, null, false);
        assertEquals(Cpu.Stop.BUDGET, cpu.lastStop());
        boolean[] bp = new boolean[531_441];
        bp[245 + 265_720] = true;
        cpu.setPc(243);
        cpu.run(100, bp, false);
        assertEquals(Cpu.Stop.BREAKPOINT, cpu.lastStop());
        assertEquals(245, cpu.pc());
    }

    @Test
    void powerOnForgetsAHalfFinishedFrame() {
        m.setBreakpoint(244, true);
        m.runFrame();
        m.powerOn();
        assertFalse(m.stoppedAtBreakpoint());
        assertEquals(Machine.CYCLES_PER_FRAME, m.cyclesLeftInFrame());
        assertTrue(m.isBreakpoint(244), "breakpoints belong to the debugger, not to the machine's memory");
    }
}
