package org.triada.core;

import org.triada.core.cpu.Cpu;
import org.triada.core.io.DebugDevice;
import org.triada.core.io.InputDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.io.SystemDevice;
import org.triada.core.memory.Bus;
import org.triada.core.memory.MemoryMap;
import org.triada.core.sound.SoundChip;
import org.triada.core.triskette.TrisketteDevice;
import org.triada.core.ternary.Word;
import org.triada.core.video.VideoChip;

/**
 * The whole TRIADA-85 in one object: the bus with RAM and ROM, the CPU, and
 * the devices plugged into the I/O registers. Frontends and the headless
 * runner drive it one frame at a time with {@link #runFrame()}.
 *
 * <p>One frame (SPEC §6, 60 per second):
 * <ol>
 * <li>the CPU runs for up to {@link #CYCLES_PER_FRAME} cycles, or until WAIT or a halt;</li>
 * <li>the video chip draws the picture from video memory;</li>
 * <li>the sound chip generates the frame's 735 samples from its registers;</li>
 * <li>FRAME increases by 1.</li>
 * </ol>
 * A halted CPU does nothing, but the frames keep coming.
 */
public final class Machine {

    /** Frames per second (SPEC §6). */
    public static final int FRAMES_PER_SECOND = 60;
    /** CPU cycles per frame: 3^10 (SPEC §6). */
    public static final int CYCLES_PER_FRAME = 59_049;

    private final Bus bus = new Bus();
    private final Cpu cpu = new Cpu(bus);
    private final InputDevice input = new InputDevice();
    private final SystemDevice system = new SystemDevice();
    private final DebugDevice debug = new DebugDevice(cpu::endFrame);
    private final VideoChip video = new VideoChip(bus);
    private final SoundChip sound = new SoundChip();
    private final TrisketteDevice triskette = new TrisketteDevice(bus);

    /** A machine in power-on state, with an empty (all-zero) ROM. */
    public Machine() {
        bus.attach(IoRegisters.FRAME, 1, system);
        bus.attach(InputDevice.FIRST_REGISTER, InputDevice.REGISTER_COUNT, input);
        bus.attach(VideoChip.FIRST_REGISTER, VideoChip.REGISTER_COUNT, video);
        bus.attach(SoundChip.FIRST_REGISTER, SoundChip.REGISTER_COUNT, sound);
        bus.attach(IoRegisters.RAND, 1, system);
        bus.attach(DebugDevice.FIRST_REGISTER, DebugDevice.REGISTER_COUNT, debug);
        bus.attach(TrisketteDevice.FIRST_REGISTER, TrisketteDevice.REGISTER_COUNT, triskette);
    }

    /** Cycles the CPU may still use in the current frame (less than 59,049 after a breakpoint stop). */
    private int cyclesLeft = CYCLES_PER_FRAME;
    /** Breakpoints, indexed by address + Word.MAX; null while there are none (no cost then). */
    private boolean[] breakAt;
    private int breakpointCount;
    /** The CPU stands ON a breakpoint: the next run executes that instruction first. */
    private boolean atBreakpoint;

    /**
     * Runs the rest of the current frame (normally all of it): the CPU uses
     * its remaining budget, or stops at WAIT or a halt; then the video chip
     * draws, the sound chip plays, and FRAME counts up.
     *
     * <p>If the CPU reaches a breakpoint first, it stops BEFORE that
     * instruction and the frame stays unfinished ({@link #stoppedAtBreakpoint()});
     * the next call continues the same frame from there.
     *
     * @return the CPU cycles used by this call
     */
    public int runFrame() {
        int used = cpu.run(cyclesLeft, breakAt, atBreakpoint);
        atBreakpoint = false;
        cyclesLeft -= used;
        if (cpu.lastStop() == Cpu.Stop.BREAKPOINT) {
            atBreakpoint = true;
            return used;
        }
        finishFrame();
        return used;
    }

    /**
     * Executes exactly one instruction (the debugger's single step), ignoring a
     * breakpoint on it. If that instruction ends the frame (a WAIT, or the last
     * cycle of the budget), the frame is finished as in {@link #runFrame()}. A
     * halted CPU executes nothing, so the step just finishes the frame.
     */
    public void step() {
        int used = cpu.run(1, null, true);
        atBreakpoint = false;
        cyclesLeft -= used;
        Cpu.Stop stop = cpu.lastStop();
        if (stop == Cpu.Stop.WAIT || cyclesLeft <= 0 || (stop == Cpu.Stop.HALT && used == 0)) {
            finishFrame();
        }
    }

    /** End of a frame (SPEC §6): picture, sound, FRAME + 1, and a fresh cycle budget. */
    private void finishFrame() {
        video.render();             // the picture shows memory as the CPU left it
        sound.renderFrame();        // and the sound plays the registers as the CPU left them
        system.advanceFrame();
        cyclesLeft = CYCLES_PER_FRAME;
    }

    /** True if the last {@link #runFrame()} stopped at a breakpoint (the frame isn't finished). */
    public boolean stoppedAtBreakpoint() {
        return atBreakpoint;
    }

    /**
     * Makes the next {@link #runFrame()} execute the instruction at PC even if
     * it is a breakpoint. The debugger calls this when it continues from a
     * position it paused at, so "continue" never stops at the same spot again.
     */
    public void ignoreBreakpointAtPc() {
        atBreakpoint = true;
    }

    /** Cycles the CPU may still use in the current frame. */
    public int cyclesLeftInFrame() {
        return cyclesLeft;
    }

    /** Sets or clears a breakpoint: the CPU will stop before executing the instruction at {@code address}. */
    public void setBreakpoint(int address, boolean on) {
        if (!Word.isValid(address)) {
            throw new IllegalArgumentException(address + " is not an address");
        }
        if (on && breakAt == null) {
            breakAt = new boolean[Word.COUNT];
        }
        if (breakAt == null || breakAt[address + Word.MAX] == on) {
            return;
        }
        breakAt[address + Word.MAX] = on;
        breakpointCount += on ? 1 : -1;
        if (breakpointCount == 0) {
            breakAt = null;             // none left: back to zero cost
        }
    }

    /** True if there is a breakpoint at {@code address}. */
    public boolean isBreakpoint(int address) {
        return breakAt != null && breakAt[address + Word.MAX];
    }

    /** Removes every breakpoint. */
    public void clearBreakpoints() {
        breakAt = null;
        breakpointCount = 0;
    }

    /**
     * Power-on (SPEC §2.4): RAM cleared, CPU reset, FRAME 0, key queue and debug output empty,
     * video and sound registers 0.
     * The ROM is kept, as a real ROM chip would be.
     */
    public void powerOn() {
        bus.clearRam();
        cpu.reset();
        cyclesLeft = CYCLES_PER_FRAME;
        atBreakpoint = false;
        system.resetFrame();
        input.clearKeys();
        debug.reset();
        video.reset();
        video.render();
        sound.reset();
        triskette.reset();
    }

    /**
     * Loads a program image into RAM and points the CPU at its first word, with
     * SP = 59,049: what the headless runner and the desktop do with a {@code .tri}
     * file (DECISIONS.md D-017).
     *
     * @throws IllegalArgumentException if the image isn't entirely inside RAM
     */
    public void loadProgram(TriImage image) {
        long last = (long) image.loadAddress() + image.size() - 1;
        if (image.loadAddress() < MemoryMap.RAM_START || last > MemoryMap.RAM_END) {
            throw new IllegalArgumentException("the program occupies addresses " + image.loadAddress() + " .. "
                    + last + ", which is not inside RAM (" + MemoryMap.RAM_START + " .. " + MemoryMap.RAM_END + ")");
        }
        bus.loadRam(image.loadAddress(), image.words());
        cpu.setPc(image.loadAddress());
        cpu.setRegister(8, MemoryMap.STACK_TOP);
    }

    public Bus bus() {
        return bus;
    }

    public Cpu cpu() {
        return cpu;
    }

    /** The triskette drive (M13); {@code triskette().setPort(...)} puts in the host's triskette. */
    public TrisketteDevice triskette() {
        return triskette;
    }

    public InputDevice input() {
        return input;
    }

    /** The debug port: DEBUG_CHAR / DEBUG_NUM output and HOST_EXIT requests. */
    public DebugDevice debug() {
        return debug;
    }

    /** The video chip: {@code video().frame()} is the latest picture. */
    public VideoChip video() {
        return video;
    }

    /** The sound chip: {@code sound().frameSamples()} are the latest frame's samples. */
    public SoundChip sound() {
        return sound;
    }

    public SystemDevice system() {
        return system;
    }
}
