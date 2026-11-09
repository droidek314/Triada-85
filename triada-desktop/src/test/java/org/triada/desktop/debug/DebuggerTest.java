package org.triada.desktop.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.assembler.AssemblyResult;
import org.triada.assembler.Assembler;
import org.triada.assembler.SourceLoader;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.rom.Rom;

class DebuggerTest {

    private AssemblyResult asm;

    /** Boots a machine with {@code source} autorun, and a debugger on it. */
    private Debugger start(String source) throws Exception {
        asm = new Assembler(SourceLoader.NONE).assembleSource("t.tasm", source);
        Machine m = Rom.newMachine();
        Rom.autorun(m, asm.image());
        return new Debugger(m);
    }

    private int label(String name) {
        return asm.symbols().get(name);
    }

    /** Runs frames until the debugger pauses (or the limit). */
    private static void runUntilPaused(Debugger d, int maxFrames) {
        for (int i = 0; i < maxFrames && !d.isPaused(); i++) {
            d.runFrames(1);
        }
    }

    private static final String COUNTER = """
                    LDI R0, 0
            loop:   INC R0
            here:   INC R1
                    WAIT
                    JMP loop
            """;

    // ================================================================ run / pause

    @Test
    void runsUntilPausedAndStandsStillWhilePaused() throws Exception {
        Debugger d = start(COUNTER);
        assertEquals(100, d.runFrames(100));
        assertEquals("RUNNING", d.status());
        d.pause();
        int frame = d.machine().system().frame();
        assertEquals(0, d.runFrames(10), "nothing runs while paused");
        assertEquals(frame, d.machine().system().frame());
        assertEquals("PAUSED", d.status());
        d.resume();
        assertEquals(5, d.runFrames(5));
    }

    @Test
    void aBreakpointPausesBeforeTheInstruction() throws Exception {
        Debugger d = start(COUNTER);
        d.toggleBreakpoint(label("here"));
        runUntilPaused(d, 200);
        assertTrue(d.isPaused());
        assertEquals(label("here"), d.machine().cpu().pc());
        assertEquals("PAUSED: BREAKPOINT AT " + label("here"), d.status());
        int r1 = d.machine().cpu().register(1);
        d.resume();
        runUntilPaused(d, 5);
        assertEquals(r1 + 1, d.machine().cpu().register(1), "continued for one more loop, then stopped again");
        assertEquals(List.of(label("here")), d.breakpoints());
        d.toggleBreakpoint(label("here"));
        assertTrue(d.breakpoints().isEmpty());
    }

    @Test
    void continuingFromAUserPauseOnABreakpointDoesNotGetStuck() throws Exception {
        Debugger d = start(COUNTER);
        d.runFrames(80);                                     // boot, and the program is looping
        d.pause();                                           // between frames: PC is on "JMP loop"
        int pc = d.machine().cpu().pc();
        long cycles = d.machine().cpu().cycles();
        d.toggleBreakpoint(pc);                              // a breakpoint right under PC
        d.resume();
        d.runFrames(1);
        // It did NOT stop at once: JMP, INC R0, INC R1, WAIT ran, and WAIT ended the frame
        // with PC back on the JMP.
        assertEquals(cycles + 4, d.machine().cpu().cycles());
        assertFalse(d.isPaused());
        d.runFrames(1);                                      // the next frame starts ON the breakpoint
        assertTrue(d.isPaused());
        assertEquals("PAUSED: BREAKPOINT AT " + pc, d.status());
    }

    // ================================================================ step / step over

    @Test
    void stepRunsOneInstruction() throws Exception {
        Debugger d = start(COUNTER);
        d.toggleBreakpoint(label("loop"));
        runUntilPaused(d, 200);
        int r0 = d.machine().cpu().register(0);
        d.step();                                            // INC R0
        assertEquals(r0 + 1, d.machine().cpu().register(0));
        assertEquals(label("here"), d.machine().cpu().pc());
        assertEquals("PAUSED: STEP", d.status());
    }

    private static final String CALLS = """
            loop:   LDI R0, 'A'
            call:   JSR PUTCHAR             ; the ROM routine: many instructions
            after:  INC R5
                    WAIT
                    JMP loop
            """;

    @Test
    void stepOverRunsAWholeSubroutine() throws Exception {
        Debugger d = start(CALLS);
        d.toggleBreakpoint(label("call"));
        runUntilPaused(d, 200);
        int sp = d.machine().cpu().register(8);
        long cyclesBefore = d.machine().cpu().cycles();
        d.stepOver();
        runUntilPaused(d, 5);
        assertEquals(label("after"), d.machine().cpu().pc());
        assertEquals(sp, d.machine().cpu().register(8));
        assertTrue(d.machine().cpu().cycles() - cyclesBefore > 10, "PUTCHAR really ran");
        assertEquals("PAUSED: STEPPED OVER", d.status());
        assertFalse(d.machine().isBreakpoint(label("after")), "the temporary breakpoint is gone");
    }

    @Test
    void stepOverAnythingElseIsAStep() throws Exception {
        Debugger d = start(CALLS);
        d.toggleBreakpoint(label("after"));
        runUntilPaused(d, 200);
        d.stepOver();
        assertEquals(label("after") + 1, d.machine().cpu().pc());
    }

    @Test
    void stepOverARecursiveCallStopsOnlyWhenBackAtTheSameLevel() throws Exception {
        // fact calls itself at 'inner'; the return address after 'inner' is passed
        // at every level on the way back up. Step over the JSR at depth 1 must
        // not stop when deeper levels return there, only when depth 1's call returns.
        Debugger d = start("""
                        LDI R0, 5
                        JSR fact
                        LDI R1, IO_BASE
                        ST R0, [R1+DEBUG_NUM]
                stop:   WAIT
                        JMP stop
                fact:   PUSH R1
                        MOV R1, R0
                        ADDI R1, -1
                        JLE base
                        PUSH R0
                        MOV R0, R1
                inner:  JSR fact
                back:   POP R1
                        MUL R0, R1
                base:   POP R1
                        RET
                """);
        d.toggleBreakpoint(label("inner"));
        runUntilPaused(d, 200);                              // depth 1: computing 4!
        d.toggleBreakpoint(label("inner"));                  // remove it, or deeper levels would stop there
        int sp = d.machine().cpu().register(8);
        d.stepOver();
        runUntilPaused(d, 5);
        assertEquals(label("back"), d.machine().cpu().pc());
        assertEquals(sp, d.machine().cpu().register(8), "back at depth 1, not deeper");
        assertEquals(24, d.machine().cpu().register(0), "4! has been computed by the stepped-over call");
    }

    @Test
    void aUserBreakpointInsideTheSubroutineWins() throws Exception {
        Debugger d = start(CALLS);
        d.toggleBreakpoint(label("call"));
        runUntilPaused(d, 200);
        d.toggleBreakpoint(Rom.address("celladdr"));         // PUTCHAR calls celladdr
        d.stepOver();
        runUntilPaused(d, 5);
        assertEquals(Rom.address("celladdr"), d.machine().cpu().pc());
        assertTrue(d.status().contains("BREAKPOINT"));
        d.toggleBreakpoint(Rom.address("celladdr"));
        assertFalse(d.machine().isBreakpoint(label("after")), "the step over was cancelled");
    }

    // ================================================================ faults

    @Test
    void aFaultPausesWithItsCodeAndAddress() throws Exception {
        Debugger d = start("NOP\n.word " + (-11 * 19_683 + 4 * 2_187) + ", 0\n");   // JIF with condition 4
        runUntilPaused(d, 200);
        int faultAt = 244;
        assertEquals("PAUSED: FAULT 1 (INVALID JIF CONDITION) AT " + faultAt, d.status());
    }

    // ================================================================ panels

    @Test
    void disassemblyShowsLinesAroundPcWithMarkers() throws Exception {
        Debugger d = start(COUNTER);
        d.toggleBreakpoint(label("here"));
        runUntilPaused(d, 200);
        List<Debugger.DisasmLine> lines = d.disassembly(2, 3);
        Debugger.DisasmLine pcLine = lines.stream().filter(Debugger.DisasmLine::isPc).findFirst().orElseThrow();
        assertEquals(label("here"), pcLine.address());
        assertEquals("ADDI R1, 1", pcLine.text());
        assertTrue(pcLine.isBreakpoint());
        assertEquals("ADDI R0, 1", lines.get(lines.indexOf(pcLine) - 1).text(), "the instruction before PC");
        assertEquals("WAIT", lines.get(lines.indexOf(pcLine) + 1).text());
        assertEquals("JMP " + label("loop"), lines.get(lines.indexOf(pcLine) + 2).text());
    }

    @Test
    void disassemblyFindsInstructionsBeforeAMultiWordOne() throws Exception {
        // PC right after a 2-word LDI: walking back must land on the LDI, not on its operand.
        Debugger d = start("""
                        LDI R3, 1234
                        LDI R4, 0
                target: INC R4
                        WAIT
                        JMP target
                """);
        d.toggleBreakpoint(label("target"));
        runUntilPaused(d, 200);
        List<Debugger.DisasmLine> lines = d.disassembly(2, 0);
        assertEquals(List.of("LDI R3, 1234", "LDI R4, 0", "ADDI R4, 1"),
                lines.stream().map(Debugger.DisasmLine::text).toList());
    }

    @Test
    void memoryLinesShowDecimalAndTernary() throws Exception {
        Debugger d = start(COUNTER);
        d.machine().bus().write(1000, 6);
        d.machine().bus().write(1001, -2);
        List<Debugger.MemoryLine> lines = d.memory(1000, 2);
        assertEquals("   1000       6 000000000+-0", lines.get(0).format());
        assertEquals("   1001      -2 0000000000-+", lines.get(1).format());
    }

    @Test
    void memoryViewDoesNotEatKeys() throws Exception {
        Debugger d = start(COUNTER);
        d.machine().input().pushKey('K');
        assertEquals('K', d.memory(-20_406, 1).get(0).value(), "peeking KEY shows the key");
        assertEquals(1, d.machine().input().queuedKeys(), "... and leaves it in the queue");
    }

    @Test
    void registersShowDecimalAndTernary() throws Exception {
        Debugger d = start(COUNTER);
        d.machine().cpu().setRegister(3, 6);
        List<String> r = d.registers();
        assertEquals("R3       6 000000000+-0", r.get(3));
        assertTrue(r.get(8).startsWith("SP "));
        assertTrue(r.get(9).startsWith("PC "));
        assertTrue(r.get(10).startsWith("S "));
        assertEquals("MODEM 0   RX 0   DROPPED 0", r.get(11), "the modem, idle (M17)");
    }

    @Test
    void addressesCanBeTypedInDecimalOrTernary() {
        assertEquals(243, Debugger.parseAddress(" 243 "));
        assertEquals(-19_683, Debugger.parseAddress("-19683"));
        assertEquals(6, Debugger.parseAddress("0t+-0"));
        assertEquals(6, Debugger.parseAddress("0T+-0"));
        assertNull(Debugger.parseAddress("265721"));
        assertNull(Debugger.parseAddress("12x"));
        assertNull(Debugger.parseAddress("0t+2"));
        assertNull(Debugger.parseAddress(""));
    }

    // ================================================================ triskettes

    @Test
    void insertingATriskettePowersOnAndRunsIt() throws Exception {
        Debugger d = start(COUNTER);
        d.runFrames(120);
        d.pause();
        TriImage other = new Assembler(SourceLoader.NONE).assembleSource("o.tasm", """
                        LDI R1, IO_BASE
                        LDI R0, 77
                        ST R0, [R1+DEBUG_NUM]
                w:      WAIT
                        JMP w
                """).image();
        d.insertTriskette(other, "other.tri");
        assertEquals("RUNNING: TRISKETTE: OTHER.TRI", d.status());
        d.runFrames(80);
        assertEquals("77\n", d.machine().debug().takeOutput());
    }
}
