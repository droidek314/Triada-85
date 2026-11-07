package org.triada.core;

import org.triada.core.cpu.Cpu;
import org.triada.core.io.InputDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.io.SystemDevice;
import org.triada.core.memory.Bus;

/**
 * The whole TRIADA-85 in one object: the bus with RAM and ROM, the CPU, and
 * the devices plugged into the I/O registers. Frontends and the headless
 * runner drive it one frame at a time with {@link #runFrame()}.
 *
 * <p>One frame (SPEC §6, 60 per second):
 * <ol>
 * <li>the CPU runs for up to {@link #CYCLES_PER_FRAME} cycles, or until WAIT or a halt;</li>
 * <li>(from M6) the video chip draws the frame;</li>
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

    /** A machine in power-on state, with an empty (all-zero) ROM. */
    public Machine() {
        bus.attach(IoRegisters.FRAME, 1, system);
        bus.attach(InputDevice.FIRST_REGISTER, InputDevice.REGISTER_COUNT, input);
        bus.attach(IoRegisters.RAND, 1, system);
    }

    /**
     * Runs one frame.
     *
     * @return the CPU cycles used in this frame
     */
    public int runFrame() {
        int used = cpu.runFrame(CYCLES_PER_FRAME);
        // M6: the video chip renders here, after the CPU's turn.
        system.advanceFrame();
        return used;
    }

    /**
     * Power-on (SPEC §2.4): RAM cleared, CPU reset, FRAME 0, key queue empty.
     * The ROM is kept, as a real ROM chip would be.
     */
    public void powerOn() {
        bus.clearRam();
        cpu.reset();
        system.resetFrame();
        input.clearKeys();
    }

    public Bus bus() {
        return bus;
    }

    public Cpu cpu() {
        return cpu;
    }

    public InputDevice input() {
        return input;
    }

    public SystemDevice system() {
        return system;
    }
}
