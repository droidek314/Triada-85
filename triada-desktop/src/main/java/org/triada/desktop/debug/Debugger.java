package org.triada.desktop.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.triada.assembler.Disassembler;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.core.cpu.Cpu;
import org.triada.core.cpu.Fault;
import org.triada.core.cpu.Instruction;
import org.triada.core.ternary.Ternary;
import org.triada.core.ternary.Word;
import org.triada.rom.Rom;

/**
 * The debugger's logic (M9), separate from its user interface so it can be
 * tested without a window: it decides when the machine runs, pauses, steps,
 * and what the panels show.
 *
 * <ul>
 * <li><b>Pause / continue:</b> while paused, no frames run (the picture and
 *     sound stand still).</li>
 * <li><b>Step:</b> exactly one instruction ({@link Machine#step()}).</li>
 * <li><b>Step over:</b> on a JSR, run the whole subroutine and stop at the
 *     instruction after the JSR. That address gets a temporary breakpoint;
 *     it only counts when SP is back at its value from before the call, so a
 *     recursive subroutine that passes the same address deeper down doesn't
 *     stop too early. On any other instruction, step over = step.</li>
 * <li><b>Breakpoints:</b> the machine stops <em>before</em> such an
 *     instruction (in the middle of a frame) and the debugger pauses.</li>
 * <li><b>Faults:</b> the debugger pauses and reports code and address.</li>
 * </ul>
 */
public final class Debugger {

    /** One line of the disassembly panel. */
    public record DisasmLine(int address, String text, boolean isPc, boolean isBreakpoint) {
    }

    /** One line of the memory panel. */
    public record MemoryLine(int address, int value) {

        /** "ADDRESS  DECIMAL  TRITS", aligned for a monospace font. */
        public String format() {
            return String.format(Locale.ROOT, "%7d %7d %s", address, value, Word.toString(value));
        }
    }

    private final Machine machine;
    private final TreeSet<Integer> breakpoints = new TreeSet<>();
    private boolean paused;
    private String reason = "";
    private boolean faultReported;

    private boolean stepOverActive;
    private int stepOverTarget;
    private int stepOverSp;
    private boolean stepOverTempBreakpoint;
    private Runnable frameListener = () -> { };

    public Debugger(Machine machine) {
        this.machine = machine;
    }

    public Machine machine() {
        return machine;
    }

    /** Called after every finished frame (the frontend hands that frame's sound to the speakers). */
    public void setFrameListener(Runnable listener) {
        this.frameListener = listener;
    }

    /** Shows a message in the status line, e.g. "CAN'T LOAD X.TRI". */
    public void note(String message) {
        reason = message.toUpperCase(Locale.ROOT);
    }

    // ================================================================ running

    /**
     * Runs up to {@code due} frames unless paused; called by the frontend 60
     * times per second (via its FrameClock).
     *
     * @return how many frames were completed (0 means: nothing new to show)
     */
    public int runFrames(int due) {
        int finished = 0;
        while (finished < due && !paused) {
            machine.runFrame();
            if (machine.stoppedAtBreakpoint()) {
                if (shouldStopAt(machine.cpu().pc())) {
                    paused = true;
                }
                continue;           // paused, or (step over, deeper call) continue the same frame
            }
            finished++;
            frameListener.run();
            checkFault();
        }
        return finished;
    }

    /** The machine stopped at a breakpoint at {@code pc}: should the debugger pause? */
    private boolean shouldStopAt(int pc) {
        if (stepOverActive && pc == stepOverTarget) {
            if (machine.cpu().register(Instruction.SP) == stepOverSp) {
                endStepOver();
                reason = "STEPPED OVER";
                return true;
            }
            if (stepOverTempBreakpoint) {
                return false;       // same address, deeper in a recursion: keep going
            }
        }
        endStepOver();              // a user breakpoint wins over a step over
        reason = "BREAKPOINT AT " + pc;
        return true;
    }

    private void checkFault() {
        Fault f = machine.cpu().fault();
        if (f != null && !faultReported) {
            faultReported = true;
            paused = true;
            endStepOver();
            reason = "FAULT " + f.code() + " (" + f.description().toUpperCase(Locale.ROOT) + ") AT "
                    + machine.cpu().faultPc();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void pause() {
        if (!paused) {
            paused = true;
            endStepOver();
            reason = "PAUSED";
        }
    }

    /** Continue; never stops again at the instruction it is standing on. */
    public void resume() {
        if (paused) {
            paused = false;
            reason = "";
            machine.ignoreBreakpointAtPc();
        }
    }

    public void togglePause() {
        if (paused) {
            resume();
        } else {
            pause();
        }
    }

    /** Executes one instruction (pausing first if running). */
    public void step() {
        pause();
        endStepOver();
        machine.step();
        reason = "STEP";
        checkFault();
    }

    /** Runs a JSR's whole subroutine, stopping after it; any other instruction: like {@link #step()}. */
    public void stepOver() {
        pause();
        Cpu cpu = machine.cpu();
        int pc = cpu.pc();
        if (Instruction.opcode(machine.bus().peek(pc)) != Instruction.JSR) {
            step();
            return;
        }
        int target = next(next(pc));            // JSR is two words long
        int sp = cpu.register(Instruction.SP);
        machine.step();                         // the JSR itself (pushes the return address)
        stepOverActive = true;
        stepOverTarget = target;
        stepOverSp = sp;
        stepOverTempBreakpoint = !machine.isBreakpoint(target);
        if (stepOverTempBreakpoint) {
            machine.setBreakpoint(target, true);
        }
        paused = false;
        reason = "";
        checkFault();
    }

    private void endStepOver() {
        if (stepOverActive && stepOverTempBreakpoint) {
            machine.setBreakpoint(stepOverTarget, false);
        }
        stepOverActive = false;
    }

    private static int next(int address) {
        return address == Word.MAX ? Word.MIN : address + 1;
    }

    // ================================================================ breakpoints

    public void toggleBreakpoint(int address) {
        if (stepOverActive && address == stepOverTarget) {
            endStepOver();          // don't let the temporary one get confused with a user one
        }
        if (breakpoints.remove(address)) {
            machine.setBreakpoint(address, false);
        } else {
            breakpoints.add(address);
            machine.setBreakpoint(address, true);
        }
    }

    public boolean isBreakpoint(int address) {
        return breakpoints.contains(address);
    }

    /** The user's breakpoints, in address order. */
    public List<Integer> breakpoints() {
        return new ArrayList<>(breakpoints);
    }

    // ================================================================ loading

    /**
     * "Inserts a triskette": power on (the ROM stays), boot, and start
     * {@code program} through AUTORUN. Breakpoints are kept.
     */
    public void insertTriskette(TriImage program, String name) {
        endStepOver();
        machine.powerOn();
        Rom.autorun(machine, program);
        faultReported = false;
        paused = false;
        reason = "TRISKETTE: " + name.toUpperCase(Locale.ROOT);
    }

    // ================================================================ what the panels show

    /** One-line state, e.g. "RUNNING", "PAUSED: BREAKPOINT AT 300", "PAUSED: FAULT 1 (...) AT 243". */
    public String status() {
        String state = paused ? "PAUSED" : machine.cpu().isHalted() ? "HALTED" : "RUNNING";
        if (reason.isEmpty() || reason.equals(state)) {
            return state;
        }
        return state + ": " + reason;
    }

    /**
     * The disassembly around PC: up to {@code before} instructions before it,
     * the one at PC, and {@code after} more. Instructions have 1 to 4 words,
     * so walking backwards is guesswork; this tries start points 12 … 1 words
     * before PC and uses the first one whose instructions land exactly on PC.
     */
    public List<DisasmLine> disassembly(int before, int after) {
        int pc = machine.cpu().pc();
        List<DisasmLine> lines = new ArrayList<>();
        for (int back = 12; back >= 1; back--) {
            List<Disassembler.Result> run = new ArrayList<>();
            long a = (long) pc - back;
            while (a < pc && a >= Word.MIN) {
                Disassembler.Result r = Disassembler.disassemble((int) a, machine.bus()::peek);
                run.add(r);
                a += r.length();
            }
            if (a == pc) {
                for (int i = Math.max(0, run.size() - before); i < run.size(); i++) {
                    lines.add(line(run.get(i), pc));
                }
                break;
            }
        }
        int a = pc;
        for (int i = 0; i <= after; i++) {
            Disassembler.Result r = Disassembler.disassemble(a, machine.bus()::peek);
            lines.add(line(r, pc));
            for (int k = 0; k < r.length(); k++) {
                a = next(a);
            }
        }
        return lines;
    }

    private DisasmLine line(Disassembler.Result r, int pc) {
        return new DisasmLine(r.address(), r.text(), r.address() == pc, isBreakpoint(r.address()));
    }

    /** {@code count} words from {@code address} on, read without side effects (Bus.peek). */
    public List<MemoryLine> memory(int address, int count) {
        List<MemoryLine> lines = new ArrayList<>();
        int a = address;
        for (int i = 0; i < count; i++) {
            lines.add(new MemoryLine(a, machine.bus().peek(a)));
            a = next(a);
        }
        return lines;
    }

    /** "R3   -1234 00000+--0+--" style lines for R0 … R8, PC, then the flags. */
    public List<String> registers() {
        Cpu cpu = machine.cpu();
        List<String> lines = new ArrayList<>();
        for (int n = 0; n < Instruction.REGISTER_COUNT; n++) {
            lines.add(regLine(n == Instruction.SP ? "SP" : "R" + n, cpu.register(n)));
        }
        lines.add(regLine("PC", cpu.pc()));
        lines.add("S " + trit(cpu.flagS()) + "   O " + trit(cpu.flagO()) + "   CYCLES " + cpu.cycles());
        return lines;
    }

    private static String regLine(String name, int value) {
        return String.format(Locale.ROOT, "%-2s %7d %s", name, value, Word.toString(value));
    }

    private static String trit(int t) {
        return t < 0 ? "-" : t > 0 ? "+" : "0";
    }

    /**
     * Reads an address typed by the user: decimal ({@code 243}, {@code -19683})
     * or balanced ternary ({@code 0t+-0}). Returns null if it isn't one.
     */
    public static Integer parseAddress(String text) {
        String t = text.trim();
        try {
            long v;
            if (t.length() > 2 && (t.startsWith("0t") || t.startsWith("0T"))) {
                v = Ternary.parse(t.substring(2));
            } else {
                v = Long.parseLong(t);
            }
            return Word.isValid(v) ? (int) v : null;
        } catch (IllegalArgumentException e) {   // NumberFormatException is one too
            return null;
        }
    }
}
