# Documentation index

Read the milestone documents in order. Together they form the book *"How to build a computer that never existed."*

## Start here

1. [../README.md](../README.md): what this project is, in a few paragraphs.
2. [SPEC.md](SPEC.md): the exact description of the TRIADA-85 machine. This is the law.
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

## Reference documents

- [GLOSSARY.md](GLOSSARY.md): every technical word, explained simply.
- [DECISIONS.md](DECISIONS.md): every design decision, with the alternatives we rejected.
- [PROGRESS.md](PROGRESS.md): which milestones are done.
- [manual/](manual/README.md): the in-universe user manual (from M11).
- [spikes/m06-teavm/](spikes/m06-teavm/build.gradle.kts): the reproducible web spike from M6 (D-022).
