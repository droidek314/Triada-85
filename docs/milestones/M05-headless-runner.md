# Milestone 05 — Headless runner and first program

## In one paragraph
Every programming tradition starts with a program that says hello. This milestone makes that possible for the TRIADA-85. A program can now *talk to the outside world*. There is still no screen (that's M6), but the machine has a small "debug port": a program writes a character into a special address, and the PC running the emulator prints it. We also built the `triada` command-line tool. `triada tasm` turns a source file into a `.tri` triskette file, and `triada run` runs it without opening any window ("headless") and reports how it ended. The first real program, `hello.tasm`, prints `HELLO, WORLD` and says goodbye with "exit code 0", the universal signal for "all went well".

## What was built
- **`DebugDevice`** (in `triada-core`): the debug port on I/O registers 20–22. DEBUG_CHAR prints a character, DEBUG_NUM prints a number, and HOST_EXIT asks the host to stop. Plus **`Cpu.endFrame()`**, so HOST_EXIT stops the CPU immediately.
- **The `triada` command-line tool** (`triada-cli`):
  - `Main` dispatches commands and defines the exit codes.
  - `TasmCommand` is `triada tasm source.tasm [-o out.tri] [--listing out.lst]`.
  - `RunCommand` is `triada run program.tri [--frames N] [--dump-screen out.png]`. It also accepts a `.tasm` file and assembles it first.
  - `FileSourceLoader` lets the assembler read files from disk.
- **Three example programs** in `programs/`:
  - `hello.tasm` prints `HELLO, WORLD`;
  - `countdown.tasm` counts 10 … 0, one number per frame;
  - `ternary.tasm` prints −13 … 13 in balanced ternary, with its own decimal-printing subroutine.
- **36 new tests**:
  - 9 for the debug port;
  - 27 integration tests that run the real command line on real files, including the repository's `programs/` folder.
- Three decisions (D-017 to D-019) and a debug-port note in SPEC §5.

## Why it was built this way

**The debug port collects text; it doesn't print.** `triada-core` must not do console or file I/O (CLAUDE.md §2). So `DebugDevice` appends to a buffer, and the host decides what to do with it. The CLI prints it after every frame; the desktop frontend will show it in a console; tests compare it with expected strings.

**HOST_EXIT stops at once** ([D-018](../DECISIONS.md#d-018--the-debug-port-unprintable-characters-and-host_exit-ends-the-frame---milestone-m05-2026-11-07)). Without that, the CPU would keep running until the end of the frame after the program said "I'm done". The device calls a hook, and `Machine` connects that hook to `Cpu.endFrame()`, which ends the frame exactly like WAIT does. The device doesn't need to know about the CPU; the machine wires them together.

**The runner starts the program directly, and every way of ending has its own exit code** ([D-017](../DECISIONS.md#d-017--how-the-headless-runner-starts-stops-and-reports---milestone-m05-2026-11-07)). CLAUDE.md allows a minimal ROM stub in M5. We need none: the runner sets PC and SP itself, as the ROM's `RUN` command will do in M8.

**`--dump-screen` is refused, not faked** ([D-019](../DECISIONS.md#d-019----dump-screen-is-rejected-until-the-video-chip-exists---milestone-m05-2026-11-07)). There is no video chip to draw a screen yet. Writing an empty picture would be fake progress (CLAUDE.md §1 rule 7).

**`Main.run()` returns the exit code instead of calling `System.exit`.** The tests call the real command line in-process and check stdout, stderr and the exit code. That is much faster and easier to debug than starting a separate Java process per test.

**`triada run` also accepts `.tasm` files.** It makes the edit–run cycle one command. It costs a few lines, because the assembler is a library.

## How it works — the plain-language version

### The debug port
Picture a mail slot in the back of the TRIADA's case, leading straight to the engineer's desk. Anything the program pushes through it lands on the desk, not on the TV screen. There are three slots:

| Address | Name | What happens when the program writes value v |
|---|---|---|
| −20,392 | DEBUG_CHAR | the character with code v appears (72 → `H`) |
| −20,391 | DEBUG_NUM | the number v appears, then a new line |
| −20,390 | HOST_EXIT | the program stops, and v is reported as its exit code |

Values that aren't printable characters, such as 0 or −5, show up as `<0>` or `<-5>`, so nothing invisible slips by.

### Hello, world, step by step
`hello.tasm` walks along the text `HELLO, WORLD` (stored one character per word, ending with a 0) and pushes each character through the DEBUG_CHAR slot:

```
start:  LDI R1, IO_BASE             ; R1 points at the I/O registers
        LDI R2, msg                 ; R2 walks along the string
next:   LD R0, [R2]                 ; fetch one character (LD sets S)
        JZ done                     ; a 0 word ends the string
        ST R0, [R1+DEBUG_CHAR]      ; hand it to the host
        INC R2                      ; on to the next character
        JMP next
```

- **Lines 1–2:** R1 now holds −20,412 (the start of the I/O registers), so `[R1+DEBUG_CHAR]` is address −20,412 + 20 = −20,392. R2 holds the address of the first letter, `H`.
- **Lines 3–4:** load the letter at R2. `LD` sets the sign flag, so `JZ` jumps away when the loaded word is 0, the end of the string.
- **Lines 5–7:** write the letter to the debug port, move R2 to the next letter, and repeat.

Finally the program writes a newline and then 0 to HOST_EXIT.

### The runner
`triada run` plays the part of the computer and its owner at once:
1. Read the `.tri` file (or assemble the `.tasm` file), and copy the words into RAM at their load address.
2. Point the CPU at the first word, and set the stack pointer to the top of RAM.
3. Run frames, 60 of them per second of machine time, as fast as the PC can. After every frame, print whatever came through the debug port.
4. Stop when something final happens, and say how it ended with an *exit code* (a number every program returns to the operating system; 0 means success):

| What happened | Exit code | Also printed (on stderr) |
|---|---|---|
| program wrote HOST_EXIT n | n | — |
| CPU executed HLT | 0 | `CPU halted (HLT) in frame F, PC = …` |
| CPU fault | 3 | `FAULT 1 (invalid JIF condition) at address …` |
| `--frames N` frames have passed | 0 | — |
| 3,600 frames (1 minute) without HOST_EXIT or HLT | 4 | `stopped after 3600 frames …` |
| source has assembly errors | 1 | the errors, `file:line:column: …` |
| wrong options, unreadable or broken file | 2 | what was wrong, plus the usage text |

*stderr* is the second output channel every program has, meant for messages *about* the run. stdout carries the program's actual output. Keeping them apart means `triada run hello.tasm > out.txt` captures exactly `HELLO, WORLD`.

### A program that uses time
`countdown.tasm` prints one number and then executes WAIT, which means "sleep until the next frame". So it prints exactly one number per frame. Run it with `--frames 3` and you see 10, 9, 8, and then the runner stops it.

### Balanced ternary on the printer
`ternary.tasm` shows off the number system. To get the trits of a number n:
- `n MOD 3` (with TRIADA's rounding division) is *already* the lowest trit, −1, 0 or +1;
- `n DIV 3` removes it.

Repeat until nothing is left. The trits come out lowest first, so the program pushes them on the stack and pops them back in the right order:

```
  5 = +--          because 9 − 3 − 1 = 5
 -4 = --           because −3 − 1 = −4
```

Printing in *decimal* is actually the harder part on this machine. Rounding division makes `7 MOD 10 = −3` (7 ÷ 10 = 0.7 rounds to 1, and 7 − 10 = −3), so the program must add 10 back to get the digit 7. The program's comments walk through it.

## How it works — the programmer's version

### 1. `DebugDevice` and `Cpu.endFrame()`
[`DebugDevice.java`](../../triada-core/src/main/java/org/triada/core/io/DebugDevice.java) implements `IoDevice` for registers `FIRST_REGISTER` = 20 to 22.
- `DebugDevice.write()` handles the three registers: DEBUG_CHAR goes through `appendChar()` (printable or `<n>`), DEBUG_NUM appends the number and `\n`, and HOST_EXIT sets `exitRequested` and `exitCode` and runs the `onExit` hook.
- `DebugDevice.read()` returns 0 (write-only).
- On the host side: `DebugDevice.takeOutput()` returns and clears the buffer, and there are `DebugDevice.exitRequested()`, `DebugDevice.exitCode()` and `DebugDevice.reset()`.

[`Cpu.java`](../../triada-core/src/main/java/org/triada/core/cpu/Cpu.java): `Cpu.endFrame()` sets the same flag WAIT sets, so `runFrame()` stops after the current instruction. `runFrame()` now clears that flag when it starts, so a flag left over from outside a frame (say, a WAIT single-stepped in the debugger) can't cut the next frame short.

[`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java) creates `new DebugDevice(cpu::endFrame)`, attaches it, offers `Machine.debug()`, and resets it in `powerOn()`.

### 2. The command line
[`Main.java`](../../triada-cli/src/main/java/org/triada/cli/Main.java):
- `Main.main()` is one line: `System.exit(run(args, System.out, System.err))`.
- `Main.run()` picks the command from `args[0]`. Usage problems are thrown as `Main.UsageException` and turned into a message plus the usage text, exit code 2.
- The exit codes are constants (`EXIT_OK` … `EXIT_TIMEOUT`), and `Main.usage()` documents them.

[`TasmCommand.java`](../../triada-cli/src/main/java/org/triada/cli/TasmCommand.java): `TasmCommand.run()` parses `-o` and `--listing` and checks that the source is readable (exit 2 if not). Then it calls `new Assembler(new FileSourceLoader()).assemble(source)`. On errors it prints each `AssemblyError` (already formatted as `file:line:column: message`) and the count, and returns 1. On success it writes `image.toBytes()` and, if asked, the listing. `TasmCommand.withExtension()` derives `x.tri` from `x.tasm`.

[`FileSourceLoader.java`](../../triada-cli/src/main/java/org/triada/cli/FileSourceLoader.java) implements the assembler's `SourceLoader` with `Files.readString`, returning `null` for anything unreadable. The assembler then reports a precise "can't find included file" error.

[`RunCommand.java`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java):
- `RunCommand.run()` parses the options and rejects `--dump-screen` (D-019).
- `load()` reads a `.tri` with `TriImage.fromBytes`, or assembles a `.tasm`. Failures are printed and thrown as `RunCommand.LoadFailure` carrying the exit code.
- It checks that the image fits in RAM, copies it with `Bus.loadRam`, and sets PC and SP.
- The loop is `RunCommand.execute()`:

```java
for (int frame = 1; frame <= limit; frame++) {
    machine.runFrame();
    String text = machine.debug().takeOutput();
    if (!text.isEmpty()) { out.print(text); out.flush(); }
    if (machine.debug().exitRequested()) return machine.debug().exitCode();
    if (cpu.isHalted()) { ... fault: return 3; HLT: return 0 ... }
}
return explicitLimit ? 0 : 4;
```
- The first three lines run one frame and print its debug output at once, so a long-running program shows progress.
- Then the checks happen in a fixed order: an exit request wins over a halt, because HOST_EXIT ends the frame before the CPU could reach an HLT.
- `RunCommand.DEFAULT_FRAME_LIMIT` is 3,600.

### 3. Build wiring
[`triada-cli/build.gradle.kts`](../../triada-cli/build.gradle.kts):
- runs `./gradlew :triada-cli:run` from the project root, so `programs/…` paths work;
- passes the root folder to the tests as the system property `triada.root`;
- the `application` plugin builds a launcher script named `triada` with `installDist`.

### 4. The tests
[`Cli.java`](../../triada-cli/src/test/java/org/triada/cli/Cli.java) is the helper: `Cli.run(args…)` calls `Main.run` with captured streams and returns a `Cli.Result(code, out, err)`. `Cli.write()` creates source files in a JUnit `@TempDir`, a fresh empty folder per test.

## Build it yourself from scratch
1. Add a write-only device on registers 20–22 that buffers text. Write the formatting rule down (what happens to value 0?) and test it.
2. Make HOST_EXIT end the frame, through a hook, so the device stays independent of the CPU. Test that an instruction placed after the HOST_EXIT write never runs.
3. Make your CLI's `main` a one-liner around a `run(args, out, err)` method that returns an int. Everything else becomes testable.
4. Decide on exit codes for every way a run can end *before* writing the loop. List them in the usage text.
5. Write `hello` in assembly, run it, and then turn the run into a test that compares stdout exactly, including the final newline.
6. Test the unhappy paths: a missing file, a broken `.tri`, a bad option, an assembly error, a fault, a program that never ends. **Pitfall:** an endless program without a frame limit hangs your test suite forever. Always have a default limit.
7. **Pitfall (Windows):** compare output with `\n` after normalising `\r\n`, because `println` uses the platform's line ending.

## How we know it works

All tests:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. There are 194 tests in `triada-core` (9 new), 98 in `triada-assembler` and 28 in `triada-cli` (27 new).

| Test class | Tests | What it proves |
|---|---|---|
| [`DebugDeviceTest`](../../triada-core/src/test/java/org/triada/core/io/DebugDeviceTest.java) | 9 | The spec addresses. Characters, `<n>` for unprintables, and numbers with newline. Exit codes, write-only reads, and power-on reset. **`DebugDeviceTest.hostExitStopsTheCpuRightAway`**: the instruction after HOST_EXIT never runs. `endFrame` affects only the current frame. |
| [`MainTest`](../../triada-cli/src/test/java/org/triada/cli/MainTest.java) | 4 | Usage text, `help`, no arguments (exit 2), and unknown commands. |
| [`TasmCommandTest`](../../triada-cli/src/test/java/org/triada/cli/TasmCommandTest.java) | 6 | The default output name, `-o` and `--listing`, includes from disk, errors on stderr with exit 1 and no output file, and usage and file errors with exit 2. |
| [`RunCommandTest`](../../triada-cli/src/test/java/org/triada/cli/RunCommandTest.java) | 13 | HOST_EXIT → exit code. DEBUG_NUM/CHAR output. Nothing runs after HOST_EXIT. Output frame by frame with `--frames`. HLT → 0, fault → 3, time-out → 4. `.tri` and `.tasm` input, SP = 59,049, images outside RAM, broken files and bad options, and the honest `--dump-screen` refusal. |
| [`ProgramsTest`](../../triada-cli/src/test/java/org/triada/cli/ProgramsTest.java) | 5 | **`ProgramsTest.helloPrintsHelloWorldAndExitsWithZero`** runs the repository's `programs/hello.tasm`, directly and via a `.tri`. The countdown runs one number per frame. Every line of `ternary.tasm` is compared with `Word.toString`. Every program in `programs/` assembles. |

Run hello yourself. The simplest way uses Gradle, which picks the right Java automatically:

```bash
./gradlew :triada-cli:run --args="run programs/hello.tasm"
```

Output (plus Gradle's own lines unless you add `-q`):

```
HELLO, WORLD
```

Gradle treats any non-zero exit code as a failed task. Use the installed launcher (next) to see a program's real exit code.

Install the `triada` launcher once, then use it directly:

```bash
./gradlew :triada-cli:installDist
```

```bash
triada-cli/build/install/triada/bin/triada tasm programs/hello.tasm -o build/hello.tri --listing build/hello.lst
```

```
programs/hello.tasm: 31 words at 243 -> build/hello.tri (listing: build/hello.lst)
```

```bash
triada-cli/build/install/triada/bin/triada run programs/countdown.tasm --frames 3
```

```
10
9
8
```

(On Windows, run `triada-cli\build\install\triada\bin\triada.bat` with the same arguments.)

**Mutation check.** We broke two things on purpose: HOST_EXIT no longer ended the frame, and DEBUG_NUM forgot its newline. 8 tests failed across both modules, including `nothingRunsAfterHostExit`, `hostExitStopsTheCpuRightAway`, `debugNumPrintsDecimalAndNewline`, and the `ternary.tasm` test (without the immediate stop, the CPU ran on into the decimal-printing subroutine and printed extra characters). With the code restored, all pass.

## Problems we hit and how we solved them
- **The launcher script used the wrong Java.** The first run of `triada` failed with `UnsupportedClassVersionError … class file version 69.0`. The generated script uses the `JAVA_HOME` environment variable, which on the development PC pointed to JDK 17, while the project is compiled for Java 25. The project isn't broken, but it is easy to trip over, so the README and this document say that `JAVA_HOME` must point to a JDK 25 (or use `./gradlew :triada-cli:run`, which always uses the right one).
- **A bug in our own example program.** The first `pad3` routine in `ternary.tasm` (right-aligning numbers) would have printed one space too many for −13 … −10. We caught it by working through the cases before running, and rewrote it as an explicit table of the three cases. The `ProgramsTest` check of all 27 lines now guards it.
- **Missing source file reported as an assembly error.** A missing file came back as exit code 1 ("assembly errors"). It is a *file* problem, so both commands now check readability first and return 2.
- **A test that only claimed something.** The countdown test had a comment saying "one number per frame" without checking it. It now runs exactly 10 frames and expects exactly 10 numbers.

## Limitations and what comes next
- **No screen yet.** `--dump-screen` is refused until M6 (D-019).
- **No keyboard input in headless runs.** There is no option yet to feed keys into the KEY queue from the command line. M8's monitor tests drive `InputDevice` directly in Java. A `--keys` option can be added when needed.
- **No real ROM.** Calls to `PRINT`, `PUTCHAR` and the other ROM routines land in an all-zero ROM, which means HLT. They arrive with the kernel in M8.
- **HOST_EXIT codes overlap the runner's own codes** 1–4 (D-017). stderr tells them apart.
- **Next, M6:** the video chip. 27 colours, tiles, sprites and scrolling turn memory into a picture, the desktop window shows it, and `--dump-screen` starts working.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Host:** the real computer the emulator runs on, as opposed to the imaginary TRIADA.
- **Debug port:** I/O registers through which a program sends text and numbers to the host.
- **Exit code:** the number a program hands back to the operating system when it ends; 0 means success.
- **stdout / stderr:** a program's two output channels: one for its results, one for messages about the run.
- **Command-line interface (CLI) / Option:** controlling a program by typed commands / a setting given on that command line, like `--frames 10`.
- **Headless:** without window, sound or keyboard.
- **Integration test / End-to-end test:** a test of several parts working together / one that goes through the whole system as a user would.
- **Time-out:** stopping something that took longer than allowed.
- **Launcher script:** a small script that starts a Java program with the right settings (`triada`, `triada.bat`).
- **JAVA_HOME:** an environment setting that tells scripts which Java installation to use.
