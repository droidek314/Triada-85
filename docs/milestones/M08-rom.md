# Milestone 08 — ROM: kernel and monitor

## In one paragraph
A home computer switched on in 1985 didn't show an empty screen. It played a little sound and printed a greeting, then a blinking cursor waited for you to type. That built-in software lives in the *ROM*, and on the TRIADA-85 it is written in the machine's own assembly language and translated by our own assembler. This milestone writes it:
- a **kernel** of small routines every program can call: print a character, print a text, print a number, clear the screen, wait for a key, move the cursor;
- a **boot sequence** that loads a typeface into the video chip, plays a three-note chime and prints the boot screen;
- a **monitor**: a tiny command line where you can look at and change memory, convert numbers to balanced ternary, and run programs.

The build now assembles the ROM automatically and builds it into the emulator. When you start the TRIADA-85, it boots.

## What was built
- **The ROM source** in `rom/`: 908 lines, of which about 540 are instructions (the rest comments, data and the generated font). It assembles to 1,847 words:
  - `rom.tasm`: the jump table and the includes;
  - `sysvars.tasm`: system variables and constants;
  - `kernel.tasm`: boot, font loading, chime, PUTCHAR, PRINT, PRINTNUM, CLS, GETKEY, LOCATE;
  - `monitor.tasm`: line input, command parsing, HELP, PEEK, POKE, RUN, CLS, TERN;
  - `font.tasm`: generated.
- **The font:** the public-domain font8x8 by Daniel Hepper, kept in `rom/third_party/font8x8/` and converted by `rom/tools/FontToTasm.java` ([D-026](../DECISIONS.md#d-026--the-font-font8x8-public-domain-stored-as-bits-expanded-at-boot---milestone-m08-2026-11-07)).
- **A new module, `triada-rom`.** The Gradle task `generateRom` assembles the ROM with our assembler during every build and turns it into a Java class. `Rom.newMachine()` gives a machine that boots; `Rom.autorun()` starts a program after boot ([D-028](../DECISIONS.md#d-028--system-variables-and-how-the-host-starts-a-program-autorun---milestone-m08-2026-11-07), [D-029](../DECISIONS.md#d-029--the-rom-is-assembled-during-the-build-into-a-java-class---milestone-m08-2026-11-07)).
- **The CLI and the desktop boot the ROM.** `triada run` boots, then starts the program; `--no-boot` keeps M5's direct start. A program that returns to the monitor ends the run with exit code 0. The desktop opens to the monitor.
- **58 new tests**:
  - 41 drive the monitor through the keyboard queue and read the screen;
  - 15 test the kernel with small autorun programs, including the font checked pixel by pixel against the original font file;
  - 2 more in the CLI.
- **Four decisions** (D-026 to D-029), a corrected boot line in SPEC §8.2, and a new SPEC §8.4.

## Why it was built this way

**The ROM is real TRIADA code, not Java pretending.** CLAUDE.md asks for it, and it is the best test of everything built so far: the CPU, the assembler, video, sound and input all have to work together for the boot screen to appear. Writing 900 lines in the machine's own language also showed what the instruction set is like to use. `BR3` and the rounding `MOD` turned out handy; the lack of bit operations needed a trick (below).

**A jump table at fixed addresses.** Programs call `JSR PRINT`, which is address −19,679 forever. That address holds only `JMP print`, so the real routine can move whenever the ROM changes, without breaking any program (SPEC §8.1).

**One calling convention, enforced by a test.** Arguments go in R0–R2, the result in R0, and R3–R8 are always preserved. Every routine that needs more registers saves and restores them on the stack. `KernelTest.everyRoutineKeepsR3ToR8` fills R3–R7 with marker values, calls every routine, and checks the markers.

**AUTORUN instead of jumping straight into a program.** Once there is a ROM, a program expects the font, a cleared screen and a proper stack. So the host doesn't bypass the ROM: it leaves the program's address in a system variable, and the ROM starts it at the end of boot, just as `RUN` would (D-028).

**The ROM becomes Java source at build time.** An edited ROM can never be forgotten in an old binary, and the emulator needs no files at run time, which keeps the web build easy (D-029).

**Small spec corrections.** The boot line in SPEC §8.2 was 29 characters on a 27-column screen, so it now has single spaces. Text output got "pending wrap" so that full lines don't produce empty ones ([D-027](../DECISIONS.md#d-027--text-output-and-the-monitor-the-details---milestone-m08-2026-11-07)).

## How it works — the plain-language version

### Switching on
When the power comes on, the CPU starts at the first ROM word, −19,683. There it finds `JMP boot`, and the boot routine does five things:
1. points the stack pointer at the top of RAM;
2. builds the typeface: 95 little pictures, one per character, in tiles 32–126, so tile number = ASCII code (the letter A, code 65, is tile 65);
3. fills the screen with spaces and plays the chime: C, E and G in the machine's own ternary-square sound, one after another, then fading;
4. prints the boot screen:
```
TRIADA-85 NPO "TRIADA" 1985
39757 WORDS FREE
READY.
> _
```
5. waits for your commands: the *monitor*.

All of that takes 48 frames, a little under a second, and most of it is the chime.

### Text on a tile screen
The screen is a mosaic of tiles (M6). To print a letter, PUTCHAR writes the letter's code into the tile map cell under the cursor and moves the cursor right. A newline moves to the start of the next line. Writing past the bottom row moves every row up by one: the top line disappears, and the bottom becomes empty. That is *scrolling*, done by copying 459 words.

A line is 27 characters wide. If a text is exactly 27 characters and then says "new line", a naive computer would wrap after the 27th character *and* obey the newline, leaving an empty line. The TRIADA waits: the cursor stays "just past the end" and wraps only when another *character* comes. Old terminals call this *pending wrap*.

### Bits on a machine without bits
The font is stored as rows of 8 pixels, each row a number from 0 to 255 whose binary digits (bits) say which pixels are lit. But the TRIADA has no instructions that deal with bits. It counts in trits! The trick: for a number n ≥ 0,
- `n MOD 2` is its last binary digit (0 or 1), and
- `n DIV 2` removes that digit.

TRIADA's division rounds to the nearest number, and ties go toward zero, so `7 DIV 2` = 3.5 → 3: exactly what a binary shift gives. Eight times "take the last bit, then halve" reads out a whole row. Each bit then becomes a light cyan pixel (1) or a dark blue one (0), and four pixels are packed into one word.

### Printing numbers on a machine that rounds
PRINTNUM prints a number in decimal. The usual way is "the last digit is n MOD 10; continue with n DIV 10". But TRIADA's MOD rounds: `7 MOD 10` is −3, because 7 ÷ 10 = 0.7 rounds to 1, and 7 − 10 = −3. So whenever the remainder is negative, PRINTNUM adds 10 back: −3 + 10 = 7, the real digit. Then it subtracts the digit, which leaves an exact multiple of 10, and divides.

In balanced ternary the same rounding is a *gift*: `n MOD 3` is already the lowest trit (−1, 0 or +1) with no correction at all. That's how the monitor's TERN command shows numbers in balanced ternary.

### The monitor
The monitor is a tiny command line. You type a line, see it echoed, can correct it with Backspace, and press Enter. Letters may be lower case. It understands six commands:

| Command | What it does | Example | Answer |
|---|---|---|---|
| `HELP` | lists the commands | `HELP` | the list |
| `PEEK addr [count]` | shows memory words, in decimal and ternary | `PEEK 1000` | `1000: 42 0t+---0` |
| `POKE addr value` | writes a word | `POKE 1000 42` | (nothing) |
| `RUN [addr]` | runs a program, 243 if no address | `RUN` | the program, then `READY.` |
| `CLS` | clears the screen | `CLS` | an empty screen |
| `TERN n` | shows a number in balanced ternary | `TERN 6` | `6 = 0t+-0` |

Numbers can be decimal (`-5`) or balanced ternary (`0T+-0`). Anything else gets the classic answer, `?SYNTAX ERROR`. That includes unknown words, missing or extra numbers, and numbers too large for a word.

### Starting a program from outside
When you run `triada run game.tri`, or drop a program into the window (M9), the TRIADA boots exactly as above. Before its first frame, the emulator places the program in RAM and writes the program's address into a system variable called AUTORUN. At the end of boot, the ROM sees it, erases it (so it happens only once), and starts the program as if you had typed `RUN`. When the program ends with `RET`, you are back at `READY.`

### The ROM is built from source every time
The ROM's source is ordinary text in `rom/`. During every build, our own assembler translates it into 1,847 words, and those words are written into a Java class that becomes part of the emulator. If someone edits the ROM, the next build contains the change; if they make a mistake, the build fails with the assembler's usual message: file, line, column and what is wrong (for a misspelt label, for example, `unknown name 'pk_lnie'`).

## How it works — the programmer's version

### 1. Files and layout
[`rom/rom.tasm`](../../rom/rom.tasm) includes [`rom/sysvars.tasm`](../../rom/sysvars.tasm) (constants only), sets `.org -19683`, lays out the jump table (`JMP boot`, `JMP putchar`, …), then includes [`rom/kernel.tasm`](../../rom/kernel.tasm), [`rom/monitor.tasm`](../../rom/monitor.tasm) and [`rom/font.tasm`](../../rom/font.tasm). The listing is written to `triada-rom/build/rom/rom.lst` at every build.

Internal labels are lower case (`putchar`). The jump-table names are the assembler's upper-case built-ins (`PUTCHAR` = −19,681), and labels are case-sensitive, so the two never clash.

### 2. Boot and the kernel
In `kernel.tasm`:
- **`boot`** sets SP with `LDI SP, STACK_TOP` and resets the scroll and border registers, then calls `loadfont`, `cls` and `chime`. It prints the banner, `FREE_WORDS` and `" WORDS FREE\n"`, then checks AUTORUN: either `JZ monitor`, or clear it, `JSR runprog`, then `JMP mn_prompt`.
- **`loadfont`** walks 760 font rows. For each it calls `lf_half` twice, which turns 4 bits into one tile word:
```
lh_bit: MOV R1, R5
        MOD R1, R2          ; R2 = 2: lowest bit
        DIV R5, R2          ; drop it
        ...                 ; R1 = TEXT_FG or TEXT_BG
        SHT R0, 3           ; word * 27
        ADD R0, R1          ; + pixel
```
  - The first two lines take the lowest bit of R5 (the row) and remove it.
  - `SHT R0, 3` multiplies by 3³ = 27 (M1's shift), so after four rounds the first pixel is in the highest trits, as SPEC §4.3 requires.
- **`chime`** sets the three voices to C5/E5/G5 ternary squares, turns them on 5 frames apart, holds them, fades them over 26 frames, and switches them off. `waitn` waits R2 frames using WAIT.
- **`putchar`**:
  - dispatches on 10 → `newline` (a tail jump: `newline`'s RET returns to PUTCHAR's caller) and on 8 → backspace;
  - otherwise it checks `CURSOR_X` against 27 (pending wrap), calls `celladdr` (R1 = MAP + row × 27 + column), stores, and increments the column.
- **`newline`** scrolls by copying `[R1+27]` to `[R1]` for 459 cells, then blanking 27.
- **`print`** keeps its pointer in R3 (saved first), because PUTCHAR may change R0–R2.
- **`printnum`** pushes digits with the "MOD 10, add 10 if negative" fix and pops them in order, saving R3–R5.
- **`cls`**, **`getkey`** (read KEY; if 0, WAIT and retry) and **`locate`** (clamp, then store) are short.

### 3. The monitor
In `monitor.tasm`:
- **`monitor` / `mn_prompt`**: print `READY.` once, then loop: prompt, `readline`, `execute`.
- **`readline`**:
  - Each frame it draws the cursor from `FRAME`: `SHT R0, -3` divides by 27 (rounded) and `MOD 2` picks `_` or space.
  - It polls KEY and takes the cursor away before handling a key.
  - Enter (10) ends the line with a 0. Backspace (8) deletes. Only 32–126 are stored, at most `LINE_MAX` (24), and a–z become A–Z.
- **`execute`**:
  - `skipsp`, then walks the `commands` table of (name address, handler address) pairs, using `matchword`, which compares a whole word up to a space or the line's end.
  - It jumps to the handler with the SPEC §3.10 idiom `PUSH R0` / `RET`. The handler's own RET therefore returns straight to the monitor loop, and a handler that finds a mistake can simply `JMP syntax`.
- **`parsenum`** handles `-123` and `0T+-0`.
  - Decimal overflow is checked *before* it can happen: a value above 26,572 can't take another digit, and exactly 26,572 can only take a 0. That's because 26,572 × 10 + 0 = 265,720, the largest word.
  - Ternary allows at most 12 trits. `SHT R5, 1` shifts up one trit (×3) before adding the next.
  - A number must be followed by a space or the end of the line.
- **`atend`** leaves S = 0 only if the line has ended. RET doesn't change the flags (M3), so callers write `JSR atend` / `JNZ syntax`.
- **`runprog`** "calls" an address held in a register: `PUSH rp_back`, `PUSH R0`, `RET`. At `rp_back` it increments `RUN_DONE` and prints `READY.`
- **`printtern`** collects 12 trits with `MOD 3` / `DIV 3`, pushes them, and pops them highest first, skipping leading zeros. It picks the character with `BR3` on the trit, the ternary three-way jump.

### 4. Building the ROM into the emulator
[`triada-rom/build.gradle.kts`](../../triada-rom/build.gradle.kts) defines a `generator` source set, which may use the assembler, and the task `generateRom`. Its output folder is added to the main sources, so compiling `triada-rom` always assembles the ROM first.

[`RomGenerator.java`](../../triada-rom/src/generator/java/org/triada/rom/tools/RomGenerator.java):
- `RomGenerator.main()` assembles with a file-reading `SourceLoader`. It fails the build on errors, and checks that the image starts at −19,683 and fits in ROM.
- It writes the listing and `RomData.java`. `RomGenerator.encode()` turns a word into 4 characters: (word + 265,720) in base 27, over the alphabet `0–9A–Q`.

[`Rom.java`](../../triada-rom/src/main/java/org/triada/rom/Rom.java):
- `Rom.image()` decodes the strings once.
- `Rom.symbols()` and `Rom.address()` expose the ROM's labels and constants.
- `Rom.newMachine()` = `new Machine()` + `loadRom` + `powerOn`.
- `Rom.autorun()` loads a program and writes AUTORUN.

### 5. The CLI and the desktop
[`RunCommand`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java):
- uses `Rom.newMachine()`, then `Rom.autorun()` (or `Machine.loadProgram()` with `--no-boot`);
- watches `RUN_DONE` and stops with exit code 0 when a program has returned.

[`DesktopLauncher`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java) does the same with `Rom.autorun()` when given a program.

### 6. The font converter
[`rom/tools/FontToTasm.java`](../../rom/tools/FontToTasm.java) is a single-file Java program, run once by hand with the JDK alone. It reads the C header with a regular expression (`{ 0x.., … }, // U+XXXX`) and writes `font.tasm` with one `.word` line of 8 row values per character.

## Build it yourself from scratch
1. **Decide the system variables first** (cursor, input line, AUTORUN) and put them in a file the ROM includes.
2. **Write the jump table** at the very start of ROM, one `JMP` per public routine, and never reorder it.
3. **PUTCHAR, then PRINT, PRINTNUM, CLS.** Write a tiny test program for each and start it through AUTORUN, or jump to it directly at first. **Pitfall:** a routine that calls PUTCHAR in a loop must keep its loop variable in R3 or higher and save it, because PUTCHAR may change R0–R2.
4. **Numbers:** remember that MOD rounds. Test 7, 10, 100, 0, the negative numbers, and both extremes.
5. **The font:** store bits, extract them with MOD 2 / DIV 2, and pack 4 pixels per word with "× 27, + pixel". Compare every pixel with the original font in a test.
6. **The monitor:** line input first (echo, Backspace, Enter), then a table of commands, then the parsers. **Pitfall:** check decimal overflow *before* multiplying, because the CPU wraps silently.
7. **Test by typing.** Push keys into the KEY queue (at most 27 per frame) and read the screen back from the tile map as text. Wait for the prompt: cursor at column 2, on a line starting with `>`.
8. **Build integration:** assemble at build time into a generated class, and fail the build on any assembly error.

## How we know it works

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`, and among the output the line `ROM: 1847 of 19683 words, 125 symbols` (when `generateRom` runs). Tests: 239 in `triada-core`, 98 in `triada-assembler`, **56 in `triada-rom`**, 48 in `triada-cli` and 14 in `triada-desktop`.

| Test class | Tests | What it proves |
|---|---|---|
| [`MonitorTest`](../../triada-rom/src/test/java/org/triada/rom/MonitorTest.java) | 41 | The exact boot screen and cursor position. HELP; POKE then PEEK; PEEK with a count and with negative values; PEEK of ROM (wrapping a 30-character line); POKE into ROM ignored. TERN of decimal and ternary input, zero and both extremes. Lower case, CLS, an empty line, RUN of a program at 300 and at 243 (and RUN_DONE counting). Backspace editing, the 24-character limit, ignored keys, scrolling, AUTORUN. **`nonsenseGetsSyntaxError`**: 22 bad commands, each answered `?SYNTAX ERROR`. |
| [`KernelTest`](../../triada-rom/src/test/java/org/triada/rom/KernelTest.java) | 15 | The jump table is a JMP at each spec address, pointing at the right routine. The ROM fits. Boot sets the colours and stack. **The chime plays C-E-G ternary squares and ends in silence.** Boot takes under 60 frames. **`fontTilesMatchTheOriginalFontBitForBit`**: all 95 characters × 64 pixels against `font8x8_basic.h`. PRINTNUM; pending wrap; ordinary wrap; backspace; LOCATE with clamping; scrolling; GETKEY waits. **`everyRoutineKeepsR3ToR8`**. A golden image of the boot screen. |
| [`RunCommandTest`](../../triada-cli/src/test/java/org/triada/cli/RunCommandTest.java) | 16 (2 new) | `triada run` boots and then starts the program (PRINT output visible on a screen dump); a program that returns ends the run with exit code 0. |

Try it in the window:

```bash
./gradlew :triada-desktop:run
```

The window shows the boot screen above. We tried it by sending real keystrokes to the window:

```
> TERN 0T+-0++
58 = 0t+-0++
> PEEK -20412
-20412: 2849 0t++0-+---
> HELP
COMMANDS:
...
```

(`-20412` is the FRAME register, so its value depends on when you look.) Backspace editing worked (`hlep` ⌫⌫⌫ `elp` became `HELP`), and the screen scrolled when the 19th line arrived.

Run a program headless; the machine now boots its ROM first, then starts the program:

```bash
triada-cli/build/install/triada/bin/triada run programs/hello.tasm
```

This still prints `HELLO, WORLD` and exits with code 0. Only now the machine booted first, 48 frames earlier.

**Web spike re-run** (D-022 asks for it after milestones that add core code; M7 added the sound chip): `./gradlew -p docs/spikes/m06-teavm teavm` still compiles today's `triada-core` and assembler to JavaScript, and Node.js still prints `HELLO FROM TERNARY JS`. The ROM module itself is not part of the spike yet.

**Mutation check.** We broke the ROM's *assembly source* three ways at once:
- PUTCHAR wrapped at column 26 instead of using pending wrap;
- PRINTNUM lost its "add 10" fix;
- the overflow limit became 26,573.

13 tests failed, among them `printnumPrintsDecimalNumbers`, `putcharWithoutNewlineWrapsToTheNextLine`, `ternOfTheExtremes`, `nonsenseGetsSyntaxError("TERN 265721")`, `bootScreen` and the golden boot screen. With the source restored, all pass.

## Problems we hit and how we solved them
- **The spec's boot line didn't fit.** `TRIADA-85   NPO "TRIADA" 1985` is 29 characters on a 27-column screen. It now has single spaces: 27 characters, one line (D-027).
- **Headless runs would have hung.** With a ROM, `RET` at the end of a program returns to the monitor, which waits for keys forever, so `triada run` would only stop at its 1-minute time-out. The ROM now counts finished programs in `RUN_DONE`, and the runner stops when it changes. It is a *counter*, not an "is running" flag, so a program that starts and returns within one frame is still noticed.
- **Tests that relied on M5's direct start.** Booting first shifts frame numbers and the stack pointer, and the boot chime fills the first frames' sound. The tests that measure exactly those things now use `--no-boot`: frame-by-frame output, HLT and fault frame numbers, SP, the 10-frame countdown, the colours demo, and the audio dump. All other CLI tests run through the ROM.
- **Mistakes in our own tooling and tests:**
  - The font converter's first pattern also matched the opening `{` of the whole C array.
  - One test used `\b` in a string, an escape the assembler deliberately doesn't know (D-013).
  - One test expected a 30-character PEEK line on one row.
  - One test checked nothing real and was deleted.
- **An unexplained extra command in one window session.** In the first real-window session, the output of a `HELP` that we hadn't typed appeared before our first command. A fresh session with only a screenshot before typing showed a clean boot screen, and repeating the exact same keystrokes produced no extra output. We believe it came from the keystroke-injection tool (Windows' `SendKeys`), not from the TRIADA. But we couldn't prove it, so it is recorded here.

## Limitations and what comes next
- **Boot overwrites tiles 32–126 and the tile map.** A program whose `.tri` already contains graphics there loses them, so programs should set up graphics at run time (D-028).
- **The monitor has no editor or assembler,** no `LOAD`/`SAVE`, and nothing to show registers. The debugger (M9) covers looking inside the machine.
- **PEEK of an I/O register really reads it,** so `PEEK -20406` takes a key from the queue. That is documented, and authentic.
- **Only the jump table's seven routines are public.** PRINTTERN, for example, is internal; adding entries means extending SPEC §8.1.
- **Next, M9:** the debugger in the desktop window (F1). It will pause, single-step, show registers in decimal and ternary, show the disassembly around PC and memory, and load programs by `--load` and drag-and-drop.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Kernel / Monitor:** the ROM's routines that programs call / its little command line.
- **Boot / Boot screen / Boot chime:** starting up / what it shows / what it plays.
- **Jump table:** a list of jumps at fixed addresses, so routines can move without breaking programs.
- **Calling convention:** the agreement on which registers carry arguments and which a routine must keep.
- **Font / Glyph:** a set of character pictures / one of them.
- **Bit:** one binary digit, 0 or 1.
- **Pending wrap:** wrapping to the next line only when another character arrives.
- **Scrolling (text):** moving all lines up by one when the screen is full.
- **Parser:** see M4; here, the monitor's code that reads commands and numbers.
- **Tail call:** ending a routine by *jumping* to another, so that one's RET returns to the original caller.
- **System variable:** a RAM word the ROM uses for its own state.
- **Auto-start (AUTORUN):** a program started by the ROM right after boot.
- **Code generation:** a build step that writes source code (here, Java) from other files.
- **Public domain:** free of copyright: anyone may use it for anything.
