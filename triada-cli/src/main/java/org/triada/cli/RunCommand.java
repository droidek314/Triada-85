package org.triada.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.triada.assembler.AssemblyError;
import org.triada.assembler.AssemblyException;
import org.triada.assembler.Assembler;
import org.triada.core.Machine;
import org.triada.core.TriImage;
import org.triada.core.cpu.Cpu;
import org.triada.core.memory.MemoryMap;

/**
 * {@code triada run program.tri [--frames N] [--dump-screen out.png]}: runs a
 * program on a machine without window, sound or keyboard ("headless").
 *
 * <p>The program is copied into RAM at its load address; the CPU starts there
 * with SP = 59,049, as if the ROM had started it (the real ROM arrives in M8).
 * Then the runner runs frames, and after each one prints whatever the
 * program wrote to DEBUG_CHAR and DEBUG_NUM. It stops when
 * <ul>
 * <li>the program writes HOST_EXIT: the exit code is that value;</li>
 * <li>the CPU halts with HLT: exit code 0;</li>
 * <li>the CPU faults: exit code 3, with the fault on stderr;</li>
 * <li>the frame limit is reached: exit code 0 if {@code --frames} was given,
 *     otherwise 4 (time-out), because a program meant to finish didn't.</li>
 * </ul>
 */
final class RunCommand {

    /** Frames to run when {@code --frames} isn't given: one minute of machine time. */
    static final int DEFAULT_FRAME_LIMIT = 60 * Machine.FRAMES_PER_SECOND;

    private final PrintStream out;
    private final PrintStream err;

    RunCommand(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    int run(String[] args) throws Main.UsageException {
        String program = null;
        Integer frames = null;
        String dumpScreen = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--frames": {
                    String v = TasmCommand.value(args, ++i, "--frames");
                    try {
                        frames = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        frames = -1;
                    }
                    if (frames < 1) {
                        throw new Main.UsageException("--frames needs a whole number of at least 1, got '" + v + "'");
                    }
                    break;
                }
                case "--dump-screen":
                    dumpScreen = TasmCommand.value(args, ++i, "--dump-screen");
                    break;
                default:
                    if (args[i].startsWith("-")) {
                        throw new Main.UsageException("run: unknown option '" + args[i] + "'");
                    }
                    if (program != null) {
                        throw new Main.UsageException("run: only one program, got '" + program + "' and '"
                                + args[i] + "'");
                    }
                    program = args[i];
            }
        }
        if (program == null) {
            throw new Main.UsageException("run: which program? e.g. triada run hello.tri");
        }
        if (dumpScreen != null) {
            // Honest placeholder: there is no video chip to draw the screen until M6.
            err.println("triada: --dump-screen needs the video chip, which arrives in milestone M6");
            return Main.EXIT_USAGE;
        }

        TriImage image;
        try {
            image = load(program);
        } catch (LoadFailure f) {
            return f.exitCode;
        }
        long last = (long) image.loadAddress() + image.size() - 1;
        if (image.loadAddress() < MemoryMap.RAM_START || last > MemoryMap.RAM_END) {
            err.println("triada: " + program + " occupies addresses " + image.loadAddress() + " .. " + last
                    + ", which is not inside RAM (" + MemoryMap.RAM_START + " .. " + MemoryMap.RAM_END + ")");
            return Main.EXIT_USAGE;
        }

        Machine machine = new Machine();
        machine.bus().loadRam(image.loadAddress(), image.words());
        Cpu cpu = machine.cpu();
        cpu.setPc(image.loadAddress());
        cpu.setRegister(8, MemoryMap.STACK_TOP);
        return execute(machine, frames == null ? DEFAULT_FRAME_LIMIT : frames, frames != null);
    }

    private int execute(Machine machine, int limit, boolean explicitLimit) {
        Cpu cpu = machine.cpu();
        for (int frame = 1; frame <= limit; frame++) {
            machine.runFrame();
            String text = machine.debug().takeOutput();
            if (!text.isEmpty()) {
                out.print(text);
                out.flush();
            }
            if (machine.debug().exitRequested()) {
                return machine.debug().exitCode();
            }
            if (cpu.isHalted()) {
                if (cpu.fault() != null) {
                    err.println("triada: FAULT " + cpu.fault().code() + " (" + cpu.fault().description()
                            + ") at address " + cpu.faultPc() + " in frame " + frame);
                    return Main.EXIT_FAULT;
                }
                err.println("triada: CPU halted (HLT) in frame " + frame + ", PC = " + cpu.pc());
                return Main.EXIT_OK;
            }
        }
        if (explicitLimit) {
            return Main.EXIT_OK;
        }
        err.println("triada: stopped after " + limit + " frames without HOST_EXIT or HLT"
                + " (use --frames N to run a fixed number of frames)");
        return Main.EXIT_TIMEOUT;
    }

    /** A program that couldn't be loaded; the reason was already printed. */
    private static final class LoadFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final int exitCode;

        LoadFailure(int exitCode) {
            super(null, null, false, false);
            this.exitCode = exitCode;
        }
    }

    /** Reads a .tri file, or assembles a .tasm file; problems are printed to stderr. */
    private TriImage load(String program) throws LoadFailure {
        if (program.toLowerCase(java.util.Locale.ROOT).endsWith(".tasm")) {
            if (!Files.isReadable(Path.of(program))) {
                err.println("triada: can't read " + program);
                throw new LoadFailure(Main.EXIT_USAGE);
            }
            try {
                return new Assembler(new FileSourceLoader()).assemble(program).image();
            } catch (AssemblyException e) {
                for (AssemblyError error : e.errors()) {
                    err.println(error);
                }
                throw new LoadFailure(Main.EXIT_ASSEMBLY_ERRORS);
            }
        }
        try {
            return TriImage.fromBytes(Files.readAllBytes(Path.of(program)));
        } catch (IOException e) {
            err.println("triada: can't read " + program + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            err.println("triada: " + program + ": " + e.getMessage());
        }
        throw new LoadFailure(Main.EXIT_USAGE);
    }
}
