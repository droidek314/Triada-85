package org.triada.core.cpu;

import java.util.Arrays;
import org.triada.core.memory.Bus;
import org.triada.core.ternary.Word;
import org.triada.core.ternary.WordMath;

/**
 * The TRIADA-85 processor (SPEC §3). It owns the nine registers R0–R8, the
 * program counter PC and the two flags S and O, and reaches memory and
 * devices only through the {@link Bus}.
 *
 * <p>{@link #step()} runs one instruction: fetch the word at PC, decode it,
 * read any extra words (moving PC past the whole instruction), then execute.
 * {@link #runFrame(int)} calls {@code step()} until the frame's cycle budget is
 * used up, a {@code WAIT} ends the frame early, or the CPU halts (SPEC §6).
 *
 * <p><b>Performance rule:</b> nothing in {@code step()} allocates an object
 * (CLAUDE.md §2). Registers are an {@code int[]}, flags are {@code int}s, and
 * all arithmetic is {@link WordMath}'s static methods.
 *
 * <p><b>Operand rule</b> (DECISIONS.md D-009): every source register is read
 * before anything is written. This only matters for {@code PUSH R8} (pushes R8's
 * value from before the push) and {@code POP R8} (R8 ends up as the popped value).
 */
public final class Cpu {

    private final Bus bus;

    private final int[] r = new int[Instruction.REGISTER_COUNT];
    private int pc;
    private int s;
    private int o;

    private boolean halted;
    private Fault fault;
    private int faultPc;
    private boolean waitRequested;
    private long cycles;

    /** A CPU attached to {@code bus}, in power-on state. */
    public Cpu(Bus bus) {
        this.bus = bus;
        reset();
    }

    /**
     * Power-on state (SPEC §2.4): all registers 0, S = O = 0, PC = −19,683 (the
     * first ROM word), not halted, no fault. Memory is not touched.
     */
    public void reset() {
        Arrays.fill(r, 0);
        pc = bus.profile().romStart();
        s = 0;
        o = 0;
        halted = false;
        fault = null;
        faultPc = 0;
        waitRequested = false;
        cycles = 0;
    }

    // ---------------------------------------------------------------- running

    /** Why {@link #run} returned. */
    public enum Stop {
        /** The cycle budget is used up. */
        BUDGET,
        /** A WAIT (or a device's {@link #endFrame()}) ended the frame. */
        WAIT,
        /** The CPU is halted (HLT or a fault). */
        HALT,
        /** The next instruction's address is a breakpoint; it has NOT been executed. */
        BREAKPOINT
    }

    private Stop lastStop = Stop.BUDGET;

    /**
     * Runs one frame's worth of instructions (SPEC §6): until {@code budget}
     * cycles are used, a {@code WAIT} executes, or the CPU halts.
     *
     * @return the number of cycles actually used (0 if already halted)
     */
    public int runFrame(int budget) {
        return run(budget, null, false);
    }

    /**
     * Runs instructions until one of the {@link Stop} reasons happens; the
     * debugger's way of running (M9).
     *
     * @param budget      the most cycles to use
     * @param breakAt     breakpoints, indexed by {@code address + Word.MAX}, or null for none
     * @param ignoreFirst true to execute the first instruction even if its address is a
     *                    breakpoint: that is how execution resumes FROM a breakpoint
     * @return the number of cycles used; {@link #lastStop()} says why it stopped
     */
    public int run(int budget, boolean[] breakAt, boolean ignoreFirst) {
        waitRequested = false;          // a WAIT single-stepped outside a frame doesn't carry over
        int used = 0;
        boolean first = true;
        while (true) {
            if (halted) {
                lastStop = Stop.HALT;
                return used;
            }
            if (used >= budget) {
                lastStop = Stop.BUDGET;
                return used;
            }
            if (breakAt != null && breakAt[pc + Word.MAX] && !(first && ignoreFirst)) {
                lastStop = Stop.BREAKPOINT;
                return used;
            }
            first = false;
            step();
            used++;
            if (waitRequested) {
                waitRequested = false;
                lastStop = Stop.WAIT;   // the rest of this frame's cycles are skipped
                return used;
            }
        }
    }

    /** Why the last {@link #run} (or {@link #runFrame}) returned. */
    public Stop lastStop() {
        return lastStop;
    }

    /**
     * Ends the current {@link #runFrame(int)} after the instruction that is
     * executing now, as if it had been a WAIT. Devices use this when the
     * program asks the host to stop (HOST_EXIT), so nothing runs after that.
     */
    public void endFrame() {
        waitRequested = true;
    }

    /**
     * Executes exactly one instruction (one cycle, whatever its length).
     * Does nothing if the CPU is halted.
     */
    public void step() {
        if (halted) {
            return;
        }
        cycles++;
        final int start = pc;
        final int word = bus.read(pc);
        pc = next(pc);

        // Decode with the offset trick (see Instruction): every field plus its maximum.
        final int u = word + Word.MAX;
        final int imm = u % 243 - 121;
        final int rs = u / 243 % 9;          // register NUMBER 0 … 8 (field + 4)
        final int rd = u / 2_187 % 9;        // register number, or condition + 4 for JIF
        final int op = u / 19_683 - 13;

        switch (op) {
            case Instruction.HLT:
                halted = true;
                break;
            case Instruction.NOP:
                break;
            case Instruction.MOV:
                setWithSign(rd, r[rs]);
                break;
            case Instruction.LDI: {
                int value = bus.read(pc);
                pc = next(pc);
                setWithSign(rd, value);
                break;
            }
            case Instruction.LD:
                setWithSign(rd, bus.read(WordMath.add(r[rs], imm)));
                break;
            case Instruction.ST:
                bus.write(WordMath.add(r[rs], imm), r[rd]);
                break;
            case Instruction.PUSH:
                push(r[rd]);
                break;
            case Instruction.POP:
                setWithSign(rd, pop());
                break;
            case Instruction.ADD:
                o = WordMath.addCarry(r[rd], r[rs]);
                setWithSign(rd, WordMath.add(r[rd], r[rs]));
                break;
            case Instruction.SUB:
                o = WordMath.subCarry(r[rd], r[rs]);
                setWithSign(rd, WordMath.sub(r[rd], r[rs]));
                break;
            case Instruction.ADDI:
                o = WordMath.addCarry(r[rd], imm);
                setWithSign(rd, WordMath.add(r[rd], imm));
                break;
            case Instruction.MUL:
                setWithSign(rd, WordMath.mul(r[rd], r[rs]));
                break;
            case Instruction.DIV:
                o = WordMath.isDivideByZero(r[rs]) ? 1 : 0;
                setWithSign(rd, WordMath.div(r[rd], r[rs]));
                break;
            case Instruction.MOD:
                o = WordMath.isDivideByZero(r[rs]) ? 1 : 0;
                setWithSign(rd, WordMath.mod(r[rd], r[rs]));
                break;
            case Instruction.NEG:
                setWithSign(rd, WordMath.neg(r[rd]));
                break;
            case Instruction.MIN:
                setWithSign(rd, WordMath.min(r[rd], r[rs]));
                break;
            case Instruction.MAX:
                setWithSign(rd, WordMath.max(r[rd], r[rs]));
                break;
            case Instruction.TMUL:
                setWithSign(rd, WordMath.tmul(r[rd], r[rs]));
                break;
            case Instruction.ROT:
                setWithSign(rd, WordMath.rot(r[rd], imm));
                break;
            case Instruction.SHT:
                setWithSign(rd, WordMath.sht(r[rd], imm));
                break;
            case Instruction.CMP:
                s = WordMath.compare(r[rd], r[rs]);
                break;
            case Instruction.JMP:
                pc = bus.read(pc);
                break;
            case Instruction.JSR: {
                int target = bus.read(pc);
                pc = next(pc);
                push(pc);                    // return address: the word after the JSR
                pc = target;
                break;
            }
            case Instruction.RET:
                pc = pop();
                break;
            case Instruction.JIF: {
                int target = bus.read(pc);
                pc = next(pc);
                int cond = rd - 4;
                if (!Instruction.isValidCondition(cond)) {
                    raise(Fault.INVALID_CONDITION, start);
                } else if (conditionHolds(cond)) {
                    pc = target;
                }
                break;
            }
            case Instruction.BR3: {
                int ifNegative = bus.read(pc);
                int ifZero = bus.read(next(pc));
                int ifPositive = bus.read(next(next(pc)));
                pc = s < 0 ? ifNegative : s == 0 ? ifZero : ifPositive;
                break;
            }
            case Instruction.WAIT:
                waitRequested = true;
                break;
            default:
                // Unreachable: all 27 values −13 … +13 are opcodes.
                throw new IllegalStateException("opcode " + op);
        }
    }

    private void setWithSign(int reg, int value) {
        r[reg] = value;
        s = WordMath.sign(value);
    }

    private void push(int value) {
        r[Instruction.SP] = previous(r[Instruction.SP]);
        bus.write(r[Instruction.SP], value);
    }

    private int pop() {
        int value = bus.read(r[Instruction.SP]);
        r[Instruction.SP] = next(r[Instruction.SP]);
        return value;
    }

    private boolean conditionHolds(int cond) {
        switch (cond) {
            case Instruction.COND_N:
                return s == -1;
            case Instruction.COND_Z:
                return s == 0;
            case Instruction.COND_P:
                return s == 1;
            case Instruction.COND_NZ:
                return s != 0;
            case Instruction.COND_LE:
                return s != 1;
            default:                    // COND_GE
                return s != -1;
        }
    }

    private void raise(Fault reason, int instructionAddress) {
        halted = true;
        fault = reason;
        faultPc = instructionAddress;
        pc = instructionAddress;        // leave PC on the faulting instruction (D-010)
    }

    /** Address + 1, wrapping from the top of the address range to the bottom. */
    private static int next(int address) {
        return address == Word.MAX ? Word.MIN : address + 1;
    }

    /** Address − 1, wrapping from the bottom to the top. */
    private static int previous(int address) {
        return address == Word.MIN ? Word.MAX : address - 1;
    }

    // ---------------------------------------------------------------- state access
    // For the machine, tests and the debugger (M9).

    /** Register Rn (n = 0 … 8). */
    public int register(int n) {
        return r[n];
    }

    /** Sets register Rn; the value must be a word. Doesn't touch the flags. */
    public void setRegister(int n, int value) {
        r[n] = checkWord(value);
    }

    public int pc() {
        return pc;
    }

    public void setPc(int address) {
        pc = checkWord(address);
    }

    /** Flag S: the sign of the last result (−1, 0, +1). */
    public int flagS() {
        return s;
    }

    /** Flag O: the last carry, or +1 after a division by zero (−1, 0, +1). */
    public int flagO() {
        return o;
    }

    public void setFlags(int sign, int overflow) {
        if (sign < -1 || sign > 1 || overflow < -1 || overflow > 1) {
            throw new IllegalArgumentException("flags must be trits, were S=" + sign + " O=" + overflow);
        }
        s = sign;
        o = overflow;
    }

    /** True after HLT or a fault. Only {@link #reset()} restarts the CPU. */
    public boolean isHalted() {
        return halted;
    }

    /** The fault that halted the CPU, or {@code null} if it hasn't faulted. */
    public Fault fault() {
        return fault;
    }

    /** Address of the faulting instruction (meaningful only if {@link #fault()} isn't null). */
    public int faultPc() {
        return faultPc;
    }

    /** Instructions executed since reset (= cycles, SPEC §3.9). */
    public long cycles() {
        return cycles;
    }

    private static int checkWord(int value) {
        if (!Word.isValid(value)) {
            throw new IllegalArgumentException(value + " is not a word");
        }
        return value;
    }
}
