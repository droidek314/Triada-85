package org.triada.core.cpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.triada.core.Machine;

/**
 * CLAUDE.md §2: "Don't allocate objects in the CPU's per-instruction loop."
 * This test runs 100 full frames (about 5.9 million instructions) and checks
 * with the JVM's per-thread allocation counter that (almost) nothing was
 * allocated. A single object per instruction would show up as hundreds of
 * megabytes. A small allowance covers the measuring calls themselves.
 */
class CpuAllocationTest {

    @Test
    void runningFramesAllocatesNothing() {
        Machine m = new Machine();
        CpuBenchmark.loadMixed(m);
        for (int f = 0; f < 50; f++) {
            m.runFrame();                       // warm-up
        }
        long before = CpuBenchmark.allocatedBytes();
        long instructions = 0;
        for (int f = 0; f < 100; f++) {
            instructions += m.runFrame();
        }
        long allocated = CpuBenchmark.allocatedBytes() - before;

        assertFalse(m.cpu().isHalted());
        assertEquals(100L * Machine.CYCLES_PER_FRAME, instructions);
        assertTrue(allocated < 10_000, allocated + " bytes allocated while running 5.9 million instructions");
    }
}
