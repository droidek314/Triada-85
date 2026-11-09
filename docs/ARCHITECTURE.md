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
| `triada-net-core` | TRIADA-NET without network code (M17): TRIMODEM, the server side of the wire protocol, the echo exchange, an in-process network. Boards from M18. | core |
| `triada-net-server` | The TRIADA-NET server (M17): TCP and WebSocket (Javalin) around `triada-net-core`. The emulator never depends on it; the CLI's tests do. | net-core, Javalin |

## Current state (after M17)

- **The modem (M17):** `org.triada.core.modem` has `ModemDevice` (registers 28–35, plugged in when the profile lists `Device.MODEM`), `ModemPort` (the line behind it; `ModemPort.OFFLINE` by default), `WireModemPort` (the client side of the wire protocol over a `ModemLink`) and `LoopbackLink` (an in-process pipe). The transports are `TcpModemLink` (triada-cli; `run --modem`, desktop `--modem`) and `WebModemLink` (triada-web; the page's `triadaModem`). See [The modem and TRIADA-NET](#the-modem-and-triada-net).

- `triada-core` has `MachineProfile` and `MachineProfiles` (M16, D-057): every number that makes a model what it is, with one model today, `MachineProfiles.TRIADA_85`. `Machine`, `Bus`, `Cpu`, `VideoChip`, `SoundChip` and `TrisketteDevice` read their sizes from it; see [Machine profiles](#machine-profiles).
- `triada-core` contains `MachineInfo` (the machine's name and spec version) and the package `org.triada.core.ternary`:
  - `Trit`, `Ternary`: single trits and `-0+` notation of any width.
  - `Word`: the 12-trit word, stored as a plain `int` (D-004), with wrap, carry and trit access.
  - `WordMath`: every arithmetic and trit-wise operation the CPU needs. Static and allocation-free.
- `org.triada.core.memory`: `MemoryMap` (all address constants, `regionOf`) and `Bus` (routes every read and write to RAM, ROM, an I/O device or nowhere). Since M14 the bus also holds the extension ROM (−59,048 … −20,413, `Bus.loadExtensionRom`).
- `org.triada.core.triskette` (M13): `TrisketteDevice` (the drive on I/O registers 23–27), the `TriskettePort` interface it stores files through, `MemoryTriskette` (default and tests), `TrisketteNames` (name rules) and `TrisketteException` (D-045). `Machine.triskette()` lets the host plug in its own port.
- `org.triada.core.io.KeyFeeder` (M15, D-054): paste-typing into the keyboard queue, used by the desktop (Ctrl+V), the web page (paste) and `triada run --type`.
- `org.triada.core.io`: the `IoDevice` plug-in interface, the `IoRegisters` numbers, `InputDevice` (joystick, buttons, KEY queue) and `SystemDevice` (FRAME, RAND).
- `org.triada.core.cpu`: `Instruction` (encoding, opcode tables), `Cpu` (registers, flags, `step()`, `runFrame()`) and `Fault`.
- `org.triada.core.Machine`: bus + CPU + devices wired together; `runFrame()` is one 1/60-second frame. Since M9 it can stop at breakpoints in the middle of a frame and continue it later (`setBreakpoint`, `step`, `stoppedAtBreakpoint`; `Cpu.run` / `Cpu.Stop`, D-030).
- `triada-core` also has `TriImage`: a program image (load address + words) and the `.tri` byte format.
- `triada-assembler`: `Lexer` → `Parser` → `Assembler` (two passes; `.include` through the `SourceLoader` interface, so no file I/O) → `AssemblyResult` (image, listing, symbols). `Disassembler` turns words back into text. Errors are `AssemblyError` records with file, line and column.
- `triada-core` also has `DebugDevice` (registers 20–22: DEBUG_CHAR, DEBUG_NUM, HOST_EXIT), which buffers output for the host and ends the frame on HOST_EXIT via `Cpu.endFrame()`.
- `triada-cli` is the `triada` tool: `Main` (commands, exit codes), `TasmCommand` (`tasm`), `RunCommand` (`run`: headless frames, debug output to stdout), `FileSourceLoader` (disk access for the assembler). Since M13 also `FolderTriskette` (a folder of `.tri` files as a triskette, `run --disk DIR`), which the desktop uses too (D-046).
- `org.triada.core.video`: `Palette` (27 colours) and `VideoChip` (registers SCROLL_X, SCROLL_Y, BORDER; `render()` turns tile map, tiles and sprites into a 240 × 168 RGB `int[]`). `Machine.runFrame()` renders after the CPU; `Machine.loadProgram()` loads a `TriImage` and starts it.
- `org.triada.core.sound.SoundChip`: registers V1–V3 FREQ/VOL/WAVE; 735 samples per frame at 44,100 Hz (`renderFrame()`, `frameSamples()`).
- `triada-cli` can save the picture and the sound: `run --dump-screen out.png` (`ScreenDump`, javax.imageio) and `--dump-audio out.wav` (`WavRecorder`).
- `triada-app` (M12, D-039): the frontend parts every platform shares, on libGDX's core API only: `MachineScreen` (texture with nearest filtering, integer scale, CRT), `CrtEffect` (the shader, its GLSL in the code, D-040), `FrameClock` (fixed 60 Hz step), `InputMapper` (keys → KEY queue, arrows → joystick, Z/X/C → buttons).
- `triada-desktop`: `DesktopLauncher` (`--load`, drag-and-drop, window focus listener, the `triskettes/` folder or `--disk DIR` since M13), `TriadaApp` (runs the machine through the debugger, draws with `MachineScreen`), `SoundOutput` + `SampleRing` (audio thread, D-025). Since M11: `LaunchOptions` (the command line), F2 or `--crt` for the CRT look, and `Capture` (`--capture`: draw into an off-screen frame buffer of a hidden window, save a PNG, quit), D-037. Package `debug`: `Debugger` (pause/step/step over/breakpoints, tested logic), `DebuggerView` (scene2d.ui panel, F1) and `TriadaFont` (the ROM font as a BitmapFont), D-031.
- `programs/` has `hello.tasm`, `countdown.tasm`, `ternary.tasm`, `colors.tasm`, `piano.tasm` and the three M10 games `trizmeyka.tasm` (snake), `troika.tasm` (ternary puzzle) and `oblaka.tasm` (scrolling shooter). They share `programs/lib/game.tasm`, an include library (D-033). The games run on the ROM: they are started through AUTORUN and call PRINT and LOCATE. Their tests (`GameHarness` and one test class per game in `triada-cli`) boot the ROM and play them through `InputDevice`. M11 added `triangle.tasm`, the manual's type-in listing.
- `docs/manual/` is the owner's manual (M11). `ManualTest` replays its monitor examples and types its listing in (D-036). `DocImagesTest` makes and checks the pictures in `docs/images/` (D-038). The CLI tests declare `programs/`, `docs/manual/` and `docs/images/` as Gradle inputs.
- `rom/` holds the ROM source (`rom.tasm`, `sysvars.tasm`, `kernel.tasm`, `monitor.tasm`, `disk.tasm` since M13, `basic.tasm` since M14, generated `font.tasm`), the public-domain font in `third_party/`, and the font converter in `tools/`. `triada-rom` assembles it at every build (D-029); the CLI and desktop boot through it and start programs via the AUTORUN system variable (D-028).
- `triada-web` (M12): the browser version, compiled to JavaScript by gdx-teavm. `WebLauncher` → `WebApp` (the same machine, `MachineScreen` and `InputMapper`, no debugger; programs from `?program=NAME` or a dropped file, assembled in the browser) and `WebPage` (one-line JavaScript glue). Since M13 `WebTriskette` keeps the triskette in browser storage, and the page has a Triskette panel for import and export (D-047). Its `index.html` holds the sound player (D-041) and the file drop. `.github/workflows/pages.yml` tests, builds and publishes it to GitHub Pages (D-042).
- TRIBASIC (M14, D-048): `rom/basic.tasm` is assembled together with the main ROM (one symbol table) at `.org -59048`; `RomGenerator` splits the image into the main ROM and the extension ROM, and `Rom.newMachine()` loads both. The kernel boots into `BASIC_COLD`; `MON` and the monitor's `BASIC` switch between the two. Its tests: `BasicSuiteTest` runs `tests/basic/*.bas` against `*.expected` (D-050). Since M15 TRIBASIC is complete (D-051 … D-053): typed values, strings in fixed slots with per-statement temporaries, variables and arrays as entries of any length, functions through `fntab`, machine statements, and storage through the DISK routines. `BasicExamplesTest` plays `programs/basic/*.bas`; `BasicManualTest` checks `docs/manual/tribasic.md`.
- `docs/spikes/m06-teavm/` is a separate throwaway build proving `triada-core` and `triada-assembler` compile to JavaScript with TeaVM (D-022).

## The modem and TRIADA-NET

Since M17 the machine has a modem, and there is a server for it to call (D-061, D-062). The parts, from the program in the machine to the board on the server:

```
  TRIBASIC: MDIAL, MSEND, MRECV$     (or any program writing registers 28-35)
        |
  ModemDevice  (triada-core)   buffers of 729, 2 characters per frame each way,
        |                      the dialing pause; moved in Machine.finishFrame
  ModemPort ---- OFFLINE (no network)  or
        |
  WireModemPort  HELLO / WELCOME / DIAL / CONNECT, then raw bytes
        |
  ModemLink ---- TcpModemLink (CLI, desktop) | WebModemLink (web) | LoopbackLink (tests)
        |                  ~~~ TCP 2385 ~~~      ~~~ WebSocket 2386 /net ~~~    (in process)
  NetServer  (triada-net-server)  one virtual thread per TCP caller; Javalin for WebSocket
        |
  ServerConnection (triada-net-core)  reads HELLO and DIAL, asks the Exchange
        |
  Exchange -> Answer -> Call     EchoExchange today; the boards from M18
```

Every layer above the link is pure Java with nothing that waits: the device asks the port once per frame, and the port reads whatever the link has. Only the transports touch the network. `LoopbackNetwork` (triada-net-core) joins a `LoopbackLink` straight to a `ServerConnection`, so a test machine calls a server inside the same program. TRIMODEM (`Trimodem`, `TrimodemSender`, `TrimodemReceiver`) moves files over a connected line; the server and the tests use it, and the machine gets its own version in assembly in M19.

## Machine profiles

Since M16 a machine is built from a **profile**: an immutable record, `MachineProfile`, that says how big each part is and what is plugged in (D-057). Today there is one, `MachineProfiles.TRIADA_85`, which holds exactly the numbers of [SPEC.md](SPEC.md).

```
              MachineProfiles.TRIADA_85   (or byId("triada-85") from --model)
                          |
        +-----------------+------------------+---------------------+
        |                 |                  |                     |
        v                 v                  v                     v
  new Machine(p)    Rom.newMachine(p)   BuiltinSymbols.of(p)   frontends / CLI
        |           = Machine(p) + the  -> new Assembler(       pick p at startup
        |             ROM images p names    loader, p)
        v
  Bus(p)  ............ RAM / ROM / ext. ROM / I/O bounds and array sizes
  Cpu(bus) ........... PC after reset = p.romStart()
  VideoChip(bus) ..... screen, border, map size, TILES / MAP / SPRITES addresses
  SoundChip(voices, fps)  samples per frame, voice count
  TrisketteDevice .... RAM bounds for loads and names
  devices ............ plugged in only if p.has(Device.X)
  cycles per frame ... p.cyclesPerFrame()
```

What stays the same for every model is not in the profile: the 12-trit word, the instruction set, the pixel packing and the I/O register numbers. The old constants (`MemoryMap.RAM_END`, `VideoChip.FRAME_WIDTH`, `Machine.CYCLES_PER_FRAME`, …) are now computed from the TRIADA-85 profile and are used only by code that is about that model: its tests and the frontends. `ModifiedProfileTest` builds a smaller made-up model and checks that the machine follows it.

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

## The desktop frontend, with the debugger

```
 render() (60, 75, 144 ... times per second)
   FrameClock.advance(dt) -> frames due
   Debugger.runFrames(due) -> Machine.runFrame() ... (not while paused)
        | stopped at a breakpoint?  -> Debugger decides: pause, or continue (step over, deeper level)
        | each finished frame       -> frame listener -> SoundOutput.submit(samples)
   upload VideoChip.frame() -> Texture -> SpriteBatch (full size, or 2x beside the panel)
        (through the CRT shader if F2 / --crt: CrtEffect)
   DebuggerView.refresh() + stage.draw()  (if F1)

 --capture: run N frames -> draw into an off-screen FrameBuffer at 3x -> Capture.writePng -> exit

 input: F-keys -> debugger | panel (Stage) | InputMapper -> InputDevice (KEY queue, joystick, buttons)

 web (triada-web): FrameClock -> Machine.runFrame() -> WebPage.playSamples -> page's Web Audio player
                   MachineScreen.upload + drawCentred -> canvas (WebGL); keys -> InputMapper; F2 -> CRT
 drop a file -> TriadaApp.insertTriskette -> Debugger.insertTriskette -> powerOn + Rom.autorun
```

## Data flow, frame by frame

Each frame (1/60 s) works like this (`Machine.runFrame()` does steps 2–3 and 5; the desktop does 1 and 4):

1. The frontend passes input into the machine's I/O registers (`InputMapper` → `InputDevice`), on the render thread.
2. The CPU runs up to 59,049 cycles, stopping early on WAIT or a halt (SPEC §6).
3. The video chip reads the tile map, tiles and sprite table through `Bus.peek()` and turns them into an `int[]` of RGB pixels (`VideoChip.render()`).
   The sound chip turns its registers into 735 samples (`SoundChip.renderFrame()`).
4. The frontend uploads the pixels to a texture and draws it, and drops the samples into `SampleRing`; the audio thread carries them to the sound card (`AudioDevice`).
5. FRAME increases by 1.
