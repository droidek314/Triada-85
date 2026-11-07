package org.triada.core.cpu;

import static org.triada.core.cpu.Instruction.ADD;
import static org.triada.core.cpu.Instruction.ADDI;
import static org.triada.core.cpu.Instruction.CMP;
import static org.triada.core.cpu.Instruction.COND_NZ;
import static org.triada.core.cpu.Instruction.DIV;
import static org.triada.core.cpu.Instruction.JIF;
import static org.triada.core.cpu.Instruction.JMP;
import static org.triada.core.cpu.Instruction.JSR;
import static org.triada.core.cpu.Instruction.LD;
import static org.triada.core.cpu.Instruction.LDI;
import static org.triada.core.cpu.Instruction.MUL;
import static org.triada.core.cpu.Instruction.POP;
import static org.triada.core.cpu.Instruction.PUSH;
import static org.triada.core.cpu.Instruction.RET;
import static org.triada.core.cpu.Instruction.ROT;
import static org.triada.core.cpu.Instruction.ST;
import static org.triada.core.cpu.Instruction.SUB;
import static org.triada.core.cpu.Instruction.TMUL;
import static org.triada.core.cpu.Instruction.encode;
import static org.triada.core.cpu.Instruction.reg;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import org.triada.core.Machine;

/**
 * A simple timed benchmark of the CPU (CLAUDE.md §2, M3). Run it with
 * {@code ./gradlew :triada-core:benchmark}.
 *
 * <p>It runs two programs that never wait or halt, so every frame uses the
 * full budget of 59,049 instructions:
 * <ul>
 * <li><b>mixed</b>: a loop of typical instructions: loads, stores, arithmetic,
 *     a subroutine call, a trit-wise op, compare and a conditional jump;</li>
 * <li><b>trit-heavy</b>: mostly MIN/MAX/TMUL/ROT, the slowest operations,
 *     as a worst case.</li>
 * </ul>
 * For each it reports instructions per second, how many times faster than
 * the real machine (60 frames × 59,049 = 3,542,940 per second) that is, and how
 * many bytes the CPU loop allocated (should be 0).
 */
public final class CpuBenchmark {

    private static final int START = 243;
    private static final int SUBROUTINE = 400;
    private static final int DATA = 1000;

    private CpuBenchmark() {
    }

    private static int i(int op, int rd, int rs, int imm) {
        return encode(op, reg(rd), reg(rs), imm);
    }

    /** Loads the "mixed" endless loop into {@code m} and points the CPU at it. */
    static void loadMixed(Machine m) {
        m.bus().loadRam(START, new int[] {
            i(LDI, 6, 4, 0), DATA,          // R6 = data pointer
            i(LDI, 7, 4, 0), 0,             // R7 = 0, for the compare
            // loop (247):
            i(ADDI, 0, 4, 1),               // R0 += 1
            i(LD, 1, 6, 0),                 // R1 = mem[R6]
            i(ADD, 1, 0, 0),                // R1 += R0
            i(ST, 1, 6, 1),                 // mem[R6+1] = R1
            i(MUL, 2, 0, 0),                // R2 *= R0
            i(DIV, 3, 0, 0),                // R3 /= R0
            i(ROT, 4, 4, 1),                // rotate R4
            i(SUB, 5, 1, 0),                // R5 -= R1
            i(JSR, 4, 4, 0), SUBROUTINE,    // call
            i(CMP, 0, 7, 0),                // S = sign(R0 - 0)
            encode(JIF, COND_NZ, 0, 0), 247, // loop while R0 ≠ 0 (always, until it wraps to 0)
            i(JMP, 4, 4, 0), 247,
        });
        m.bus().loadRam(SUBROUTINE, new int[] {
            i(PUSH, 3, 4, 0),
            i(TMUL, 3, 1, 0),
            i(POP, 3, 4, 0),
            i(RET, 4, 4, 0),
        });
        m.cpu().setPc(START);
        m.cpu().setRegister(8, 59_049);
    }

    /** Loads the "trit-heavy" endless loop. */
    static void loadTritHeavy(Machine m) {
        m.bus().loadRam(START, new int[] {
            i(LDI, 1, 4, 0), 123_456,
            i(LDI, 2, 4, 0), -98_765,
            // loop (247):
            i(Instruction.MIN, 0, 1, 0),
            i(Instruction.MAX, 3, 2, 0),
            i(TMUL, 4, 1, 0),
            i(ROT, 1, 4, 1),
            i(ROT, 2, 4, -1),
            i(Instruction.MAX, 0, 3, 0),
            i(TMUL, 3, 4, 0),
            i(JMP, 4, 4, 0), 247,
        });
        m.cpu().setPc(START);
    }

    public static void main(String[] args) {
        System.out.println("TRIADA-85 CPU benchmark (" + System.getProperty("java.vm.name")
                + " " + System.getProperty("java.version") + ", "
                + Runtime.getRuntime().availableProcessors() + " cores)");
        run("mixed", CpuBenchmark::loadMixed);
        run("trit-heavy", CpuBenchmark::loadTritHeavy);
    }

    private interface Loader {
        void load(Machine m);
    }

    private static void run(String name, Loader loader) {
        Machine m = new Machine();
        loader.load(m);
        // Warm-up: give the JIT compiler time to compile step() before measuring.
        for (int f = 0; f < 600; f++) {
            m.runFrame();
        }
        long before = allocatedBytes();
        long instructions = 0;
        int frames = 0;
        long t0 = System.nanoTime();
        long deadline = t0 + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            for (int f = 0; f < 60; f++) {
                instructions += m.runFrame();
            }
            frames += 60;
        }
        long elapsed = System.nanoTime() - t0;
        long allocated = allocatedBytes() - before;

        double ips = instructions * 1e9 / elapsed;
        double realTime = Machine.FRAMES_PER_SECOND * (double) Machine.CYCLES_PER_FRAME;
        System.out.printf(Locale.ROOT, "%-11s %,d frames in %.2f s: %,.0f instructions/s = %.1f x real time, "
                        + "%.3f ms per frame, %,d bytes allocated%n",
                name, frames, elapsed / 1e9, ips, ips / realTime, elapsed / 1e6 / frames, allocated);
        if (m.cpu().isHalted()) {
            System.out.println("  WARNING: the CPU halted, so the numbers are meaningless");
        }
    }

    /** Bytes this thread has allocated so far (HotSpot-specific, hence the cast). */
    static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
}
