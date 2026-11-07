# Architecture

How the parts of TRIADA-85 fit together. This document always describes the **current** state and is updated whenever the structure changes.

## The big picture

Think of a real 1985 home computer. Inside the box is the *machine*: processor, memory, video and sound chips. Outside are the *things a person touches*: the TV set, the speaker, the keyboard and the joystick. TRIADA-85 is split the same way.

```
                 +------------------------------------------+
                 | triada-core   (the box: pure Java)        |
                 |  ternary math, memory/bus, CPU,           |
                 |  video chip, sound chip, I/O registers    |
                 +-------^-----------------------^----------+
                         | uses                  | uses
          +--------------+---------+   +---------+----------------+
          | triada-assembler        |   | triada-rom                |
          | text <-> machine words  |   | the ROM (rom/*.tasm),     |
          +---^----------^---------+   | assembled at BUILD time   |
              |          |  build-time | by the assembler          |
              |          +-------------+ -> Rom.newMachine()       |
              |                        +----^-----------------^----+
              |                             |                 |
          +---+-----------------------------+---+   +---------+----------------+
          | triada-cli                          |   | triada-desktop  (libGDX) |
          | tasm, headless run, end-to-end tests|   | window, sound, keys,     |
          +-------------------------------------+   | debugger UI              |
                                                    +--------------------------+
                                                     (also uses triada-assembler)
```

Arrows point from a module to the module it *uses*. Nothing points into `triada-desktop` or `triada-cli`, because the machine never knows which frontend is driving it.

## Modules

| Module | What it is | May depend on |
|---|---|---|
| `triada-core` | The machine itself. No UI, no threads, no file I/O and no reflection, so a future web build can compile it. | nothing |
| `triada-assembler` | Assembler and disassembler (M4). It receives source text as strings. | `triada-core` |
| `triada-rom` | The ROM (M8): kernel, monitor, font, written in TRIADA assembly in `rom/`. A build-time step assembles it into a generated Java class; `Rom.newMachine()` gives a machine that boots. Holds the ROM's integration tests. | core (and the assembler at build time only) |
| `triada-cli` | The command-line tool `triada` (M5), with `tasm` and `run`. It reads and writes files and holds the end-to-end tests. | core, assembler, rom |
| `triada-desktop` | The libGDX window, sound, input and debugger (M6, M7, M9). | core, assembler, rom, libGDX |

## Current state (after M8)

- `triada-core` contains `MachineInfo` (the machine's name and spec version) and the package `org.triada.core.ternary`:
  - `Trit`, `Ternary`: single trits and `-0+` notation of any width.
  - `Word`: the 12-trit word, stored as a plain `int` (D-004), with wrap, carry and trit access.
  - `WordMath`: every arithmetic and trit-wise operation the CPU needs. Static and allocation-free.
- `org.triada.core.memory`: `MemoryMap` (all address constants, `regionOf`) and `Bus` (routes every read and write to RAM, ROM, an I/O device or nowhere).
- `org.triada.core.io`: the `IoDevice` plug-in interface, the `IoRegisters` numbers, `InputDevice` (joystick, buttons, KEY queue) and `SystemDevice` (FRAME, RAND).
- `org.triada.core.cpu`: `Instruction` (encoding, opcode tables), `Cpu` (registers, flags, `step()`, `runFrame()`) and `Fault`.
- `org.triada.core.Machine`: bus + CPU + devices wired together; `runFrame()` is one 1/60-second frame.
- `triada-core` also has `TriImage`: a program image (load address + words) and the `.tri` byte format.
- `triada-assembler`: `Lexer` → `Parser` → `Assembler` (two passes; `.include` through the `SourceLoader` interface, so no file I/O) → `AssemblyResult` (image, listing, symbols). `Disassembler` turns words back into text. Errors are `AssemblyError` records with file, line and column.
- `triada-core` also has `DebugDevice` (registers 20–22: DEBUG_CHAR, DEBUG_NUM, HOST_EXIT), which buffers output for the host and ends the frame on HOST_EXIT via `Cpu.endFrame()`.
- `triada-cli` is the `triada` tool: `Main` (commands, exit codes), `TasmCommand` (`tasm`), `RunCommand` (`run`: headless frames, debug output to stdout), `FileSourceLoader` (disk access for the assembler).
- `org.triada.core.video`: `Palette` (27 colours) and `VideoChip` (registers SCROLL_X, SCROLL_Y, BORDER; `render()` turns tile map, tiles and sprites into a 240 × 168 RGB `int[]`). `Machine.runFrame()` renders after the CPU; `Machine.loadProgram()` loads a `TriImage` and starts it.
- `org.triada.core.sound.SoundChip`: registers V1–V3 FREQ/VOL/WAVE; 735 samples per frame at 44,100 Hz (`renderFrame()`, `frameSamples()`).
- `triada-cli` can save the picture and the sound: `run --dump-screen out.png` (`ScreenDump`, javax.imageio) and `--dump-audio out.wav` (`WavRecorder`).
- `triada-desktop`: `DesktopLauncher` (optional program argument, window focus listener), `TriadaApp` (texture with nearest filtering, integer scale), `FrameClock` (fixed 60 Hz step), `InputMapper` (keys → KEY queue, arrows → joystick, Z/X/C → buttons), `SoundOutput` + `SampleRing` (audio thread, D-025).
- `programs/` has `hello.tasm`, `countdown.tasm`, `ternary.tasm`, `colors.tasm` and `piano.tasm`.
- `rom/` holds the ROM source (`rom.tasm`, `sysvars.tasm`, `kernel.tasm`, `monitor.tasm`, generated `font.tasm`), the public-domain font in `third_party/`, and the font converter in `tools/`. `triada-rom` assembles it at every build (D-029); the CLI and desktop boot through it and start programs via the AUTORUN system variable (D-028).
- `docs/spikes/m06-teavm/` is a separate throwaway build proving `triada-core` and `triada-assembler` compile to JavaScript with TeaVM (D-022).

## Inside triada-core: the bus

```
              read(address) / write(address, value)
   Cpu ----------------------------------------------> Bus
                                                        |
        +-------------------+---------------------------+-------------------+
        v                   v                           v                   v
   RAM  int[59049]     ROM  int[19683]        devices[k], k = address - IO_BASE   unmapped
   0 ... 59,048        -19,683 ... -1          -20,412 ... -19,684                 -> 0
                       (CPU writes ignored)     |
                                                +- k = 0, 19    SystemDevice (FRAME, RAND)
                                                +- k = 1 ... 6  InputDevice (joystick, buttons, KEY)
                                                +- k = 7 ... 9  VideoChip (SCROLL_X, SCROLL_Y, BORDER)
                                                +- k = 10 ... 18 SoundChip (V1..V3 FREQ, VOL, WAVE)
                                                +- k = 20 ... 22 DebugDevice (DEBUG_CHAR, DEBUG_NUM, HOST_EXIT)
                                                +- others       not attached yet -> read 0
```

Host code (frontends, tests, the future debugger) also uses `Bus.peek()` (no side effects), `Bus.loadRom()`, `Bus.loadRam()` and `Bus.attach()`.

## From source to running program

```
 hello.tasm --> SourceLoader --> Lexer --> Parser --> Assembler (pass 1: layout, pass 2: encode)
                (CLI: disk)                                   |
                                                              v
                                       AssemblyResult: TriImage + listing + symbols
                                                              |
                         TriImage.toBytes() -> hello.tri      |  (triada tasm writes the files)
                                                              v
                                 Bus.loadRam(loadAddress, words) -> Machine.runFrame() ...
                                                              |
                     triada run: after each frame, DebugDevice.takeOutput() -> stdout;
                     stop on HOST_EXIT (exit code), HLT (0), fault (3) or frame limit
```

The `Disassembler` goes the other way (words -> text) and will be used by the debugger (M9).

## Data flow (planned; filled in as milestones land)

Each frame (1/60 s) works like this (`Machine.runFrame()` does steps 2–3 and 5; the desktop does 1 and 4):

1. The frontend passes input into the machine's I/O registers (`InputMapper` → `InputDevice`), on the render thread.
2. The CPU runs up to 59,049 cycles, stopping early on WAIT or a halt (SPEC §6).
3. The video chip reads the tile map, tiles and sprite table through `Bus.peek()` and turns them into an `int[]` of RGB pixels (`VideoChip.render()`).
   The sound chip turns its registers into 735 samples (`SoundChip.renderFrame()`).
4. The frontend uploads the pixels to a texture and draws it, and drops the samples into `SampleRing`; the audio thread carries them to the sound card (`AudioDevice`).
5. FRAME increases by 1.
