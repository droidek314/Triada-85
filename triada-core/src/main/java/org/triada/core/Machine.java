package org.triada.core;

import org.triada.core.cpu.Cpu;
import org.triada.core.io.DebugDevice;
import org.triada.core.io.InputDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.io.SystemDevice;
import org.triada.core.memory.Bus;
import org.triada.core.memory.MemoryMap;
import org.triada.core.sound.SoundChip;
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

    /** A machine in power-on state, with an empty (all-zero) ROM. */
    public Machine() {
        bus.attach(IoRegisters.FRAME, 1, system);
        bus.attach(InputDevice.FIRST_REGISTER, InputDevice.REGISTER_COUNT, input);
        bus.attach(VideoChip.FIRST_REGISTER, VideoChip.REGISTER_COUNT, video);
        bus.attach(SoundChip.FIRST_REGISTER, SoundChip.REGISTER_COUNT, sound);
        bus.attach(IoRegisters.RAND, 1, system);
        bus.attach(DebugDevice.FIRST_REGISTER, DebugDevice.REGISTER_COUNT, debug);
    }

    /**
     * Runs one frame.
     *
     * @return the CPU cycles used in this frame
     */
    public int runFrame() {
        int used = cpu.runFrame(CYCLES_PER_FRAME);
        video.render();             // the picture shows memory as the CPU left it
        sound.renderFrame();        // and the sound plays the registers as the CPU left them
        system.advanceFrame();
        return used;
    }

    /**
     * Power-on (SPEC §2.4): RAM cleared, CPU reset, FRAME 0, key queue and debug output empty,
     * video and sound registers 0.
     * The ROM is kept, as a real ROM chip would be.
     */
    public void powerOn() {
        bus.clearRam();
        cpu.reset();
        system.resetFrame();
        input.clearKeys();
        debug.reset();
        video.reset();
        video.render();
        sound.reset();
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
