# Documentation index

Read the milestone documents in order. Together they form the book *"How to build a computer that never existed."*

## Start here

1. [../README.md](../README.md): what this project is, in a few paragraphs, with pictures.
2. [SPEC.md](SPEC.md): the exact description of the TRIADA-85 machine. Its companion [NET-SPEC.md](NET-SPEC.md) describes the TRIADA-NET server and the protocol a machine uses to dial it (Phase 3). This is the law.
3. [ARCHITECTURE.md](ARCHITECTURE.md): how the software parts fit together.

## The milestones, in reading order

| # | Document | What you learn |
|---|---|---|
| M0 | [Project setup](milestones/M00-project-setup.md) | How the project is organised, how it is built and tested, and how a window gets onto the screen. |
| M1 | [Ternary math](milestones/M01-ternary-math.md) | Counting with −1, 0 and +1; wrapping, carry, rounding division, and the trit-by-trit operations of a ternary machine. |
| M2 | [Memory and bus](milestones/M02-memory-and-bus.md) | The memory map with address 0 in the middle: RAM, ROM, I/O registers and empty space, the bus that routes every access, and the keyboard queue. |
| M3 | [CPU core](milestones/M03-cpu-core.md) | How a processor reads and runs instructions: the 27 opcodes, flags, jumps, subroutines, faults, and the 60-frames-per-second rhythm. Plus how fast it is. |
| M4 | [Assembler and disassembler](milestones/M04-assembler.md) | How text like `JMP loop` becomes machine words: tokens, parsing, two passes, error messages that point at the column, the listing, the `.tri` file, and the way back. |
| M5 | [Headless runner and first program](milestones/M05-headless-runner.md) | The debug port, the `triada` command-line tool (`tasm`, `run`), exit codes, and HELLO, WORLD. |
| M6 | [Video chip and frontend display](milestones/M06-video.md) | 27 colours from three trits, tiles, scrolling, sprites, the border; the desktop window at integer scale; golden-image tests; and an early check that the machine can run in a browser. |
| M7 | [Input and sound](milestones/M07-input-and-sound.md) | Keyboard, joystick and buttons; a three-voice sound chip with the ternary square wave; exact pitch; an audio thread fed through a ring buffer; and a playable piano. |
| M8 | [ROM: kernel and monitor](milestones/M08-rom.md) | The built-in software, written in TRIADA assembly: boot, font, chime, the kernel routines and the monitor; bits on a machine without bits; building the ROM into the emulator. |
| M9 | [Debugger](milestones/M09-debugger.md) | Stopping a machine at exactly the right instruction and continuing as if nothing happened; step, step over (even through recursion), breakpoints; a debugger panel in the machine's own font; inserting triskettes. |
| M10 | [Games](milestones/M10-games.md) | Three games in TRIADA assembly: a snake with trit-coloured food, a puzzle whose moves are the CPU's own trit-wise instructions, and a scrolling shooter with two dozen sprites; painting tiles with arithmetic; tests that play. |
| M11 | [Lore and presentation](milestones/M11-lore-and-presentation.md) | The owner's manual whose examples are tested against the machine, a type-in listing typed in by a test, a CRT shader, pictures made by the machine itself, and the finished README. |
| M12 | [The web version](milestones/M12-web-version.md) | Translating the machine to JavaScript, sharing one frontend between desktop and browser, sound in a web page, and publishing on GitHub Pages behind the tests. |
| M13 | [The triskette storage device](milestones/M13-triskette.md) | Phase 2 begins: a disk drive driven through five I/O registers, one storage interface with three homes (memory, a PC folder, browser storage), ROM routines, and getting files in and out. |
| M14 | [TRIBASIC I: the interpreter core](milestones/M14-tribasic-core.md) | A programming language in the machine's own assembly: storing lines as tokens, evaluating expressions, three-valued logic and the three-way IF on BR3, and a test suite of 66 small programs. |
| M15 | [TRIBASIC II and release 1.1](milestones/M15-tribasic-complete.md) | Strings without garbage collection, arrays with a middle, the picture and sound chips from BASIC, SAVE and LOAD, paste-typing, example programs, the manual chapter, and releasing version 1.1. |
| M16 | [Housekeeping: prior art and machine profiles](milestones/M16-housekeeping.md) | Who came before TRIADA, and the machine's birth certificate: every model-specific number in one record, the machine built from it, and a test with a made-up model proving nothing is still hard-coded. |
| M17 | [The modem, the wire protocol and TRIMODEM](milestones/M17-modem.md) | Phase 3 begins: a modem that really calls a server, at a 1985 pace; one protocol over TCP, WebSocket or an in-process pipe; three characters per word; an echo server; TRIBASIC's modem words. |

Then read the [retrospective](RETROSPECTIVE.md): what went well, what was hard, and what we would do differently.

## Reference documents

- [GLOSSARY.md](GLOSSARY.md): every technical word, explained simply.
- [PRIOR-ART.md](PRIOR-ART.md): the projects that came before TRIADA or inspired it, and how TRIADA differs (M16).
- [DECISIONS.md](DECISIONS.md): every design decision, with the alternatives we rejected.
- [PROGRESS.md](PROGRESS.md): which milestones are done.
- [manual/](manual/README.md): the TRIADA-85 Owner's Manual, written as if printed in 1985 (M11). Start here if you just want to use the machine.
- [RETROSPECTIVE.md](RETROSPECTIVE.md): looking back on the whole project.
- [../CHANGELOG.md](../CHANGELOG.md): what changed in each released version.
- [spikes/m06-teavm/](spikes/m06-teavm/build.gradle.kts): the reproducible web spike from M6 (D-022).
