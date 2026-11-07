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
          | triada-assembler        |   | triada-desktop  (libGDX) |
          | text <-> machine words  |   | window, sound, keys,     |
          +--------------^---------+   | debugger UI              |
                         | uses        +--------------------------+
          +--------------+---------+     (also uses triada-assembler)
          | triada-cli              |
          | tasm, headless run,     |
          | end-to-end tests        |
          +------------------------+
```

Arrows point from a module to the module it *uses*. Nothing points into `triada-desktop` or `triada-cli`, because the machine never knows which frontend is driving it.

## Modules

| Module | What it is | May depend on |
|---|---|---|
| `triada-core` | The machine itself. No UI, no threads, no file I/O and no reflection, so a future web build can compile it. | nothing |
| `triada-assembler` | Assembler and disassembler (M4). It receives source text as strings. | `triada-core` |
| `triada-cli` | The command-line tool `triada` (M5), with `tasm` and `run`. It reads and writes files and holds the end-to-end tests. | core, assembler |
| `triada-desktop` | The libGDX window, sound, input and debugger (M6, M7, M9). | core, assembler, libGDX |

## Current state (after M2)

- `triada-core` contains `MachineInfo` (the machine's name and spec version) and the package `org.triada.core.ternary`:
  - `Trit`, `Ternary`: single trits and `-0+` notation of any width.
  - `Word`: the 12-trit word, stored as a plain `int` (D-004), with wrap, carry and trit access.
  - `WordMath`: every arithmetic and trit-wise operation the CPU needs. Static and allocation-free.
- `org.triada.core.memory`: `MemoryMap` (all address constants, `regionOf`) and `Bus` (routes every read and write to RAM, ROM, an I/O device or nowhere).
- `org.triada.core.io`: the `IoDevice` plug-in interface, the `IoRegisters` numbers, and `InputDevice` (joystick, buttons, KEY queue).
- `triada-assembler` is an empty library with a package description.
- `triada-cli` prints a usage message.
- `triada-desktop` opens a 960 × 672 window titled "TRIADA-85" and clears it to a dark color every frame.

## Inside triada-core: the bus

```
              read(address) / write(address, value)
   CPU (M3) -----------------------------------------> Bus
                                                        |
        +-------------------+---------------------------+-------------------+
        v                   v                           v                   v
   RAM  int[59049]     ROM  int[19683]        devices[k], k = address - IO_BASE   unmapped
   0 ... 59,048        -19,683 ... -1          -20,412 ... -19,684                 -> 0
                       (CPU writes ignored)     |
                                                +- k = 1 ... 6  InputDevice (joystick, buttons, KEY)
                                                +- others       not attached yet -> read 0
```

Host code (frontends, tests, the future debugger) also uses `Bus.peek()` (no side effects), `Bus.loadRom()`, `Bus.loadRam()` and `Bus.attach()`.

## Data flow (planned; filled in as milestones land)

From M6 on, each frame (1/60 s) works like this:

1. The frontend passes input (keys, joystick) into the machine's I/O registers.
2. The CPU runs up to 59,049 cycles (SPEC §6).
3. The video chip turns memory into an `int[]` of RGB pixels.
4. The frontend uploads those pixels to a texture and draws it, and sends the sound chip's samples to the audio device.
