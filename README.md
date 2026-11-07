# TRIADA-85

**A home computer that never existed — built in software.**

In our world, computers count in *binary*: every tiny switch inside them is either off or on, 0 or 1. But in 1958 Moscow State University built a computer called **Setun** that counted in *ternary* — three states instead of two: −1, 0 and +1. It worked well, and then it was forgotten. Binary won.

TRIADA-85 imagines the other timeline. Ternary won in Eastern Europe, and by 1985 a (fictional) factory, NPO "Triada", sells a beige home computer called the TRIADA-85: a membrane keyboard, a three-position joystick, a cassette port and a "triskette" drive. Children type in game listings from the (fictional) magazine *Trit & Tryte*.

This project builds that whole machine as a program you can run on a normal PC: the three-valued arithmetic, the processor, the memory, the screen chip, the sound chip, a small operating system written in the machine's own language, a debugger, and games. Every step is documented so that someone with no technical background can follow the ideas, and a programmer can rebuild the whole thing from the documents alone.

## Status

Milestones **M0 to M3** are done: project setup, ternary math, memory, and the processor. The machine's three-valued arithmetic has been checked for every one of its 531,441 possible numbers. Its memory and its processor work: the CPU runs all 27 instructions, about 13 times faster than the real machine would need. There is no screen output yet, and programs still have to be written as raw numbers, until the assembler arrives in M4. See [docs/PROGRESS.md](docs/PROGRESS.md) for the full checklist.

## Where to start reading

- [docs/INDEX.md](docs/INDEX.md) — the reading guide to every document, in order.
- [docs/SPEC.md](docs/SPEC.md) — the exact technical description of the machine.

## How to run (for now)

You need a Java Development Kit (JDK) version 25 installed. Gradle (the build tool) downloads itself.

```bash
./gradlew build
```

```bash
./gradlew :triada-desktop:run
```

On Windows, use `gradlew.bat` instead of `./gradlew` if you are not in a Bash-style shell.
