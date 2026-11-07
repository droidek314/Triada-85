# TRIADA-85

**A home computer that never existed — built in software.**

In our world, computers count in *binary*: every tiny switch inside them is either off or on, 0 or 1. But in 1958 Moscow State University built a computer called **Setun** that counted in *ternary* — three states instead of two: −1, 0 and +1. It worked well, and then it was forgotten. Binary won.

TRIADA-85 imagines the other timeline. Ternary won in Eastern Europe, and by 1985 a (fictional) factory, NPO "Triada", sells a beige home computer called the TRIADA-85: a membrane keyboard, a three-position joystick, a cassette port and a "triskette" drive. Children type in game listings from the (fictional) magazine *Trit & Tryte*.

This project builds that whole machine as a program you can run on a normal PC: the three-valued arithmetic, the processor, the memory, the screen chip, the sound chip, a small operating system written in the machine's own language, a debugger, and games. Every step is documented so that someone with no technical background can follow the ideas, and a programmer can rebuild the whole thing from the documents alone.

## Status

Milestones **M0 to M8** are done: project setup, ternary math, memory, the processor, the assembler, a command-line tool that runs programs without a window, the video chip (27 colours, tiles, scrolling, sprites), keyboard, joystick and a three-voice sound chip, and now its own built-in software: switched on, it plays a chime, shows its boot screen and waits for commands. The machine's three-valued arithmetic has been checked for every one of its 531,441 possible numbers. Its memory and its processor work: the CPU runs all 27 instructions, about 13 times faster than the real machine would need. With M4, programs can be written in TRIADA assembly language (`LDI R0, 5`, `JMP loop`) and are translated by our own assembler, which also turns machine code back into text. The first program, `programs/hello.tasm`, prints HELLO, WORLD. `programs/colors.tasm` shows all 27 colours and a bouncing ball; `programs/piano.tasm` turns your keyboard into a piano (A–K and W, E, T, Y, U play notes; the arrow keys change the sound). The built-in software (the "ROM") is itself written in TRIADA assembly; a built-in debugger comes next (M9). See [docs/PROGRESS.md](docs/PROGRESS.md) for the full checklist.

## Where to start reading

- [docs/INDEX.md](docs/INDEX.md) — the reading guide to every document, in order.
- [docs/SPEC.md](docs/SPEC.md) — the exact technical description of the machine.

## How to run (for now)

You need a Java Development Kit (JDK) version 25 installed. Gradle (the build tool) downloads itself. On Windows, use `gradlew.bat` instead of `./gradlew` if you are not in a Bash-style shell.

Build everything and run all tests:

```bash
./gradlew build
```

Run the first program (it prints `HELLO, WORLD`):

```bash
./gradlew :triada-cli:run --args="run programs/hello.tasm"
```

Or install the `triada` command once and use it directly. Its launcher uses the `JAVA_HOME` environment variable, which must point to a JDK 25:

```bash
./gradlew :triada-cli:installDist
```

```bash
triada-cli/build/install/triada/bin/triada run programs/ternary.tasm
```

`triada help` lists all commands and exit codes. Switch the machine on and type commands at its `READY.` prompt (try `HELP`, `TERN 42`, `PEEK 243 3`):

```bash
./gradlew :triada-desktop:run
```

To see a program in a window:

```bash
./gradlew :triada-desktop:run --args="programs/colors.tasm"
```

```bash
./gradlew :triada-desktop:run --args="programs/piano.tasm"
```
