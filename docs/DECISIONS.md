# Decision log

Every choice that the spec (`SPEC.md`) or the project rules (`CLAUDE.md`) did not settle, or that changed them, is recorded here. Entries are never deleted. A later entry can replace an earlier one and says so.

Format: see CLAUDE.md §5.4.

---

## D-001 — The spec lives in `docs/SPEC.md`   (Milestone M00, 2026-11-07)
**Situation:** The repository started with `SPEC.md` in the root folder, but `CLAUDE.md` says the spec is `docs/SPEC.md`.
**Options:** A) Leave it in the root and change CLAUDE.md. B) Move it into `docs/` as CLAUDE.md describes.
**Decision:** B. The file was moved to `docs/SPEC.md` unchanged.
**Why:** CLAUDE.md's layout keeps all documents together in `docs/`, and every other rule refers to that path.
**Consequences:** None for the machine. Links to the spec always use `docs/SPEC.md`.

## D-002 — Tool versions: JDK 25, Gradle 9.3.1, libGDX 1.14.2, JUnit 5.14.4   (Milestone M00, 2026-11-07)
**Situation:** CLAUDE.md asks for "the newest LTS JDK (21 or later)", Gradle with the Kotlin DSL, libGDX and JUnit 5, but names no exact versions.
**Options:**
- JDK: 21 (older LTS, most widely tested) or 25 (newest LTS, September 2025).
- Gradle: 9.9.0 (released the day before this decision) or 9.3.1 (already in use on the development machine, supports JDK 25).
- JUnit: 6.x (newest) or 5.14.x (the last JUnit 5 line).

**Decision:** JDK 25 via a Gradle *toolchain*, Gradle 9.3.1 through the committed wrapper, libGDX 1.14.2 (newest release on Maven Central) and JUnit 5.14.4.
**Why:** JDK 25 is the newest LTS, as CLAUDE.md asks. A day-old Gradle release gives no benefit and some risk, while 9.3.1 is proven here. CLAUDE.md names JUnit 5 explicitly.
**Consequences:**
- All library versions sit in one file, `gradle/libs.versions.toml`, so upgrading is a one-line change.
- libGDX 1.14.2 ships LWJGL 3.3.3, which prints a harmless warning on JDK 25 ("Unsupported JNI version detected"). See the M0 document, "Problems".
- A future web build (TeaVM/GWT) may need a lower language level for `triada-core`. That is checked in M6.

## D-003 — Shared build settings in the root build file; strict compiler warnings   (Milestone M00, 2026-11-07)
**Situation:** Four modules need the same Java version, test setup and compiler options.
**Options:** A) Repeat the settings in each module. B) A Gradle "convention plugin" in `buildSrc/`. C) One `subprojects {}` block in the root `build.gradle.kts`.
**Decision:** C, with `-Xlint:all -Werror`, so every compiler warning fails the build.
**Why:** C is the shortest option and the easiest for a beginner to read. B only pays off in much larger builds. Treating warnings as errors keeps a teaching codebase clean from the first day.
**Consequences:** If the build grows complicated, moving to B later is straightforward. Now and then a warning has to be fixed, or explicitly suppressed with a comment, before the code compiles.

## D-004 — Words are plain `int`s, operations are static methods   (Milestone M01, 2026-11-07)
**Situation:** The code needs a "word" type. The obvious object-oriented design is a `Word` class with 12 trit fields, or an immutable wrapper object around a number.
**Options:** A) A `Word` object per value. B) An `int` holding the value, plus static helper classes (`Word`, `WordMath`). C) An array of 12 trits per word.
**Decision:** B.
**Why:** The CPU must run about 3.5 million instructions per second (SPEC §6), and CLAUDE.md forbids creating objects in that loop. Normal integer arithmetic plus the wrap formula gives exactly the ternary result, so trits only need to be taken apart for the four trit-wise operations and for display. A, or the trits of C, would cost an object or array for every result.
**Consequences:** RAM can be a plain `int[]`. Java's type system can't tell a word from any other `int`, so methods document "inputs are valid words", and the tests check that every result stays in range.

## D-005 — Trit arrays are indexed by position, least significant first   (Milestone M01, 2026-11-07)
**Situation:** A word converted to an array of trits needs an index order. The written notation is most significant first (`+-0`).
**Options:** A) `array[0]` = leftmost written trit (t11). B) `array[i]` = trit `ti`, so `array[0]` = t0.
**Decision:** B.
**Why:** The spec numbers trits by position (`t0` … `t11`, SPEC §1.4), so `trits[i]` = `ti` needs no translation and its weight is simply 3^i. The cost is that an array printed in index order reads backwards compared with the string. Tests check that the two agree.
**Consequences:** Code that prints an array must walk it from index 11 down to 0. `Word.toString` does that for you.

## D-006 — MOD by zero also gives 0   (Milestone M01, 2026-11-07)
**Situation:** SPEC §3.5 says "Division by zero: result 0, S = 0, O = +1" in a paragraph about both DIV and MOD, and the opcode table says both set O on ÷0. But the formula for MOD (`rd − round(rd ÷ rs) × rs`) with the DIV result 0 would give `rd`, not 0.
**Options:** A) MOD by zero returns 0. B) MOD by zero returns `rd` unchanged (what the formula gives).
**Decision:** A. SPEC §3.5 now says "Division by zero (in `DIV` or `MOD`): result 0".
**Why:** It is the most literal reading of the existing sentence, and one simple rule for both instructions is easier to remember. It also matches S = 0, which the spec already requires: S is the sign of the result, and only a result of 0 has sign 0.
**Consequences:** `WordMath.mod(a, 0)` returns 0. In M3 the CPU writes 0 into `rd` for both DIV and MOD by zero.

## D-007 — The KEY queue holds 27 keys and drops new ones when full   (Milestone M02, 2026-11-07)
**Situation:** SPEC §5 says KEY returns the "next key from the keyboard queue" and that reading removes it, but doesn't say how big the queue is or what happens when it fills up.
**Options:** A) Unlimited queue. B) A fixed size, dropping the *oldest* key when full. C) A fixed size, dropping the *newest* key when full.
**Decision:** C, with 27 (3^3) keys.
**Why:** Real home computers had small keyboard buffers that lost keystrokes typed faster than the program read them, so C feels authentic. A fixed array (a ring buffer) never allocates, which keeps `triada-core` garbage-free. An unlimited queue could grow forever while a program never reads KEY, such as a game that only uses the joystick. Dropping the newest key keeps what was typed first in order, which matters for typing commands into the monitor. 27 is a round ternary number, larger than anything a person can type in one frame.
**Consequences:** `InputDevice.pushKey()` returns `false` when it drops a key, so the frontend could beep or log it. Tests that type long commands into the monitor (M8) must type at most 27 keys per frame, or run frames in between.

## D-008 — I/O devices: register numbers `k`, and separate `read` / `peek`   (Milestone M02, 2026-11-07)
**Situation:** CLAUDE.md asks for a pluggable device interface for the I/O registers. Two details were open. Should a device see the register's address or its number? And how can a debugger look at registers whose reads change something (KEY)?
**Options:** for the first, the address (−20,406), the number counted from the device's own first register, or the spec's register number k (6). For the second, accept that looking changes things, or add a second, side-effect-free read.
**Decision:** Devices receive the spec's register number `k` (0 … 728), with names in `IoRegisters`. `IoDevice` has `read(k)` (may have side effects), `peek(k)` (must not; the default calls `read`) and `write(k, value)`. The bus has a matching `Bus.peek()`.
**Why:** `k` is how SPEC §5 names registers, so device code reads like the spec table (`case IoRegisters.KEY:`). A debugger that eats the key you just typed whenever its memory viewer refreshes would be a nasty, confusing bug. The split costs one method, and most devices don't need to write it at all.
**Consequences:** The debugger (M9) must use `Bus.peek()`, never `Bus.read()`. A device attached to several registers checks `k` with a `switch`.

## D-009 — Source registers are read first: `PUSH R8` and `POP R8`   (Milestone M03, 2026-11-07)
**Situation:** SPEC §3.4 defines PUSH as "R8 ← R8 − 1; mem[R8] ← rd" and POP as "rd ← mem[R8]; R8 ← R8 + 1". Read literally, step by step, `PUSH R8` would store the *already decremented* R8, and `POP R8` would end with the popped value **plus one**.
**Options:** A) Follow the steps literally. B) Read every source register before writing anything: `PUSH R8` stores R8's old value, `POP R8` leaves exactly the popped value in R8.
**Decision:** B, and SPEC §3.4 now says so.
**Why:** B is what a programmer expects. "Push R8, later pop R8" saves and restores the stack pointer. Under A, that round trip silently changes SP by one, which is exactly the kind of bug nobody finds. It is also how most real processors behave (for example x86's `POP SP`).
**Consequences:** In `Cpu.step()`, PUSH evaluates `r[rd]` before calling `push()`, and POP writes `rd` after the stack pointer was incremented. Both cases are tested.

## D-010 — After a fault, PC points at the faulting instruction; fault codes are numbered   (Milestone M03, 2026-11-07)
**Situation:** SPEC §3.9 says a fault halts the CPU and records "the fault reason and PC", and §3.8 mentions a "fault code", but neither says which PC or which codes.
**Options:** For the PC: the address of the faulting instruction, or the address after it (where PC already points once the extra words are read). For the reason: free text or a numbered list.
**Decision:** The recorded fault PC *and* the PC register both hold the address of the faulting instruction. Reasons are the enum `Fault`, each with a stable number: 1 = invalid JIF condition. New faults get new numbers; old numbers never change meaning.
**Why:** When the debugger (M9) stops on a fault, the disassembly should highlight the guilty instruction, not the one after it. Numbers are what a 1985 machine would print ("FAULT 1 AT 300").
**Consequences:** The headless runner (M5) and the debugger can report "fault 1 (invalid JIF condition) at address N".

## D-011 — FRAME and RAND live in a `SystemDevice`; RAND is a seeded xorshift generator   (Milestone M03, 2026-11-07)
**Situation:** SPEC §6 says FRAME increases after every frame, and §5 says RAND reads "a random word". Neither register belongs to a device CLAUDE.md names for a later milestone, and M3's frame budget needs FRAME.
**Options:** For RAND: Java's `java.util.Random`, the operating system's secure random source, or a tiny generator written here. Its start value (seed) can be fixed or taken from the clock.
**Decision:** One `SystemDevice` owns FRAME (k = 0) and RAND (k = 19). RAND uses xorshift32 with a fixed default seed (`DEFAULT_SEED`), folded into the word range with `mod 3^12`. Its next value is computed ahead, so `peek` can show it without consuming it. `SystemDevice.seed()` lets a frontend choose another seed.
**Why:** A fixed seed makes every run of a program identical, so tests of games (M10) can compare screens exactly. xorshift is three lines of code, allocation-free, and has no platform dependencies (good for a later web build). The bias from the `mod` is about 1 part in 8,000 between the most and least likely values, which is irrelevant for games.
**Consequences:** Without a call to `seed()`, every game starts with the same "random" sequence, just like many real 1980s machines. The desktop frontend may seed from the clock; the test runner should not.

## D-012 — Program images: default origin 243, one block, gaps filled with 0   (Milestone M04, 2026-11-07)
**Situation:** SPEC §9.5 defines a `.tri` file as *one* block (load address + N words), but a source can use several `.org` directives, and may have none at all.
**Options:** For a missing `.org`: start at 0, or at 243 where programs load (SPEC §2.3). For several blocks: extend the file format with multiple segments, or write one block from the lowest to the highest address used, with zeros in the gaps.
**Decision:** Without `.org`, code starts at 243 (`Assembler.DEFAULT_ORIGIN`). The image is one block from the lowest to the highest address any line produced; gaps are 0. Two lines producing the same address is an error.
**Why:** 243 is where every user program belongs, so the most common case needs no `.org`. One block keeps the spec's file format unchanged, and the gaps are rarely large. An overlapping `.org` is almost always a mistake, so it must not silently overwrite code.
**Consequences:** A program with blocks far apart, for example code at 243 and data at 40,000, produces a `.tri` that contains the whole stretch in between, as zeros. When the block includes the tile area, loading it also clears that area. A program that minds this can keep its blocks close, or set the tiles at run time.

## D-013 — Assembler syntax details the spec leaves open   (Milestone M04, 2026-11-07)
**Situation:** SPEC §9 describes the syntax in a few lines. Writing a real parser raised many small questions.
**Decision:** Each answer is the simplest one consistent with the spec:
- **`SP`** is accepted as another name for `R8`, in any case. Like `R0`–`R8`, it can't be a label or constant.
- **Characters and strings** may contain printable ASCII (32–126) and the escapes `\n \t \0 \\ \' \"`. Other characters are errors, because the TRIADA font only has tiles 32–126 (SPEC §4.8).
- **Unary `+` and `-`** are allowed anywhere in an expression (`-7`, `BASE - -5`). In a memory operand the sign belongs to the expression, so `[R1-3+1]` means `R1 + (-3 + 1)`.
- **Labels may be used before they are defined** (two passes). So may constants, except in `.org` and the count of `.fill`, which decide addresses and must therefore be known in the first pass.
- **A label on an `.org` line** gets the new address: `start: .org 500` makes `start` = 500.
- **`.include` paths** are resolved relative to the folder of the including file. An include cycle is an error.
- **`JIF` with a condition outside −2 … 3** is an assembly error, not a word that faults at run time, because "range errors are errors" (SPEC §9.3).
- **Names** are case-sensitive (labels, constants, built-ins); mnemonics, registers and directives are not.
**Why:** Each of these either follows a hint in the spec, or makes a common mistake impossible.
**Consequences:** A faulting JIF can still be produced with `.word`, for testing the debugger.

## D-014 — The disassembler prints `.word` for words without a canonical spelling   (Milestone M04, 2026-11-07)
**Situation:** Not every word is a "proper" instruction. Unused fields may be non-zero (SPEC §3.3 says the CPU ignores them), a JIF condition may be invalid, and data such as string characters looks like instructions too. Printing these as instructions would lose information: `NOP` re-assembles with zero fields, not with the original junk.
**Options:** A) Print them as instructions anyway (pretty, not exact). B) Invent syntax for every field (`NOP rd=2`). C) Print `.word N` whenever the instruction text wouldn't re-assemble to exactly the same word.
**Decision:** C. `Disassembler.decode()` builds the text, re-encodes it in canonical form, and falls back to `.word` if the two differ.
**Why:** It makes the round-trip promise (CLAUDE.md M4) true for *every* word, which we test exhaustively. Real programs almost never contain such words in their code, so the debugger's view stays readable.
**Consequences:** Data shown in the debugger appears partly as `.word` lines, and partly as instructions that happen to be canonical. Telling code from data needs knowledge the disassembler doesn't have, such as the symbols from the assembler.

## D-015 — Everything the programs print is plain ASCII   (Milestone M04, 2026-11-07)
**Situation:** The first error messages used typographic characters such as `−`, `…` and `→`. On a Windows console they came out as `?` and `�`.
**Decision:** All text in string literals that the tools may print (error messages, listings, exception messages) is ASCII: `-` for minus, `..` for ranges, `...` for an ellipsis, `->` for arrows. Documentation and code comments keep their typography.
**Why:** The command-line tools (M5) print these messages straight to whatever terminal the person uses. ASCII is also what a 1985 machine would print.
**Consequences:** Tests that check messages use ASCII too. A small script found and converted every literal; two test-only literals remain non-ASCII on purpose (`'é'` checks that non-ASCII is rejected, and one assertion text).

## D-016 — Report many errors, but don't encode after a layout error   (Milestone M04, 2026-11-07)
**Situation:** An assembler can stop at the first error, or report as many as possible.
**Decision:** Every line is processed. A bad line is reported and skipped, up to `Assembler.MAX_ERRORS` (100) errors. If any error happens in pass 1 (layout), pass 2 (encoding) is skipped. Operand *counts* are checked in pass 1, so they appear alongside the other pass-1 errors. An error inside a constant's definition is reported once, at that definition. Lines that use the broken constant fail silently.
**Why:** Fixing several mistakes per attempt is much nicer. But after a broken line in pass 1, every later address may be off, and pass 2 would then report confusing follow-on errors ("unknown name", "out of range") that disappear once the first error is fixed.
**Consequences:** A program with both a pass-1 error and a pass-2 error (for example an unknown name) shows the second only after the first is fixed.

## D-017 — How the headless runner starts, stops and reports   (Milestone M05, 2026-11-07)
**Situation:** CLAUDE.md fixes the runner's command line, and says it "can load a `.tri` and start at its load address". Open questions: when should it stop, what should its exit codes be, and what about programs that never stop?
**Options:**
- Start through a ROM stub, or directly.
- After HLT: keep running empty frames, or stop.
- Without `--frames`: run forever, or until a limit.

**Decision:**
- **Start:** the image is copied into RAM, PC = its load address, SP = 59,049 (what the ROM will do, SPEC §8.2). Images outside RAM are rejected. No ROM stub: the ROM stays all zeros until M8, and nothing in M5 needs it.
- **Stop and exit code:**
  - HOST_EXIT n → exit code n;
  - HLT → 0, with a note on stderr;
  - CPU fault → 3, with fault code and address on stderr;
  - frame limit reached → 0 if `--frames N` was given (running N frames was the goal), otherwise 4 (time-out).
- **Other codes:** 1 = assembly errors, 2 = usage or file error.
- **Default frame limit:** 3,600 frames, one minute of machine time.

**Why:** Starting directly keeps M5 independent of the ROM. A halted CPU can never do anything again without input, so the runner stops instead of running empty frames. HLT is a normal way to end in TRIADA, so it counts as success. The default limit makes a forgotten HOST_EXIT a clear failure instead of a hung test or terminal. Each failure kind has its own code, so scripts can tell them apart.
**Consequences:**
- Exit codes from HOST_EXIT collide with the runner's own codes 1–4. stderr always says which it was, but test programs should prefer 0 for success and values from 10 upwards for their own meanings.
- Operating systems keep only the low 8 bits of an exit code on Unix, so HOST_EXIT −1 shows as 255 there.
- `RET` from the top level of a program pops an empty stack, jumps to address 0 (a system-variable word, 0 = HLT) and halts. In M8 the monitor's `RUN` gives programs a real return address.

## D-018 — The debug port: unprintable characters, and HOST_EXIT ends the frame   (Milestone M05, 2026-11-07)
**Situation:** SPEC §5 says DEBUG_CHAR prints "the value as a character" and HOST_EXIT makes the runner stop. A word can hold values that are not characters at all (0, −5, 1,000), and the CPU would keep running for the rest of the frame after HOST_EXIT.
**Decision:**
- DEBUG_CHAR prints printable ASCII (32–126), newline, carriage return and tab as themselves. Every other value prints as its number in angle brackets, e.g. `<0>`.
- DEBUG_NUM prints the decimal value and a newline.
- Writing HOST_EXIT records the code and ends the CPU's frame immediately (`Cpu.endFrame()`), so no further instruction runs.
- The output collects in `DebugDevice`; the host drains it with `takeOutput()` after each frame.

**Why:** Invisible or terminal-confusing characters would hide bugs. A visible `<0>` shows exactly what the program wrote. Stopping right at HOST_EXIT means "the last thing the program did" really is the last thing. Buffering keeps `triada-core` free of console I/O.
**Consequences:** A program can't print characters beyond ASCII through the debug port (the TRIADA font doesn't have them either). SPEC §5 notes these rules.

## D-019 — `--dump-screen` is rejected until the video chip exists   (Milestone M05, 2026-11-07)
**Situation:** CLAUDE.md lists `--dump-screen out.png` in M5's command line, but the video chip that would draw the screen is M6.
**Options:** A) Write a blank or placeholder image. B) Accept the option and silently do nothing. C) Reject it with a clear message until M6.
**Decision:** C. `triada run … --dump-screen x.png` exits with code 2 and says the option needs the video chip, which arrives in M6. M6 implements it.
**Why:** CLAUDE.md §1 rule 7: never fake progress. A blank PNG would look like a working feature and make the first M6 golden-image tests misleading.
**Consequences:** The option is parsed and documented now, and a test checks that no file is written. M6 replaces the rejection with real output.

## D-020 — A 12-pixel border: the picture is 240 × 168   (Milestone M06, 2026-11-07)
**Situation:** SPEC §4.1 says the 216 × 144 screen has "a border area around it in the BORDER color", but not how wide.
**Options:** No border in the picture (the frontend draws it), or a fixed width in the picture: 8, 12 or 16 pixels.
**Decision:** The video chip's output includes a 12-pixel border on every side, so a picture is 240 × 168 pixels (`VideoChip.FRAME_WIDTH`, `FRAME_HEIGHT`). The desktop window opens at 4× (960 × 672).
**Why:** The border belongs to the machine (a program sets its colour), so it belongs in the chip's output, in screen dumps and in golden images. 12 pixels looks like the generous borders of 1980s home computers on a TV, and 240 × 168 keeps the 10 : 7 shape. At 4× it fits a 1080p screen.
**Consequences:** Screen coordinates inside the picture are offset by 12: screen pixel (x, y) is picture pixel (x + 12, y + 12). Golden images and `--dump-screen` PNGs are 240 × 168. SPEC §4.1 now says so.

## D-021 — Video values outside the defined range   (Milestone M06, 2026-11-07)
**Situation:** Registers and table entries hold whole words, but SPEC §4 only defines some values: pixel values −13 … 13 for BORDER, FLAGS ∈ {−1, 0, +1}, and (for the tile map only) what a TILE beyond ±242 means.
**Decision:**
- **BORDER** keeps the whole word written to it (it reads back unchanged), but the chip uses only its lowest three trits (`Palette.fold`): 32 shows as 5 (red).
- **Sprite FLAGS** other than +1 and −1 mean hidden, just like 0.
- **Sprite TILE** beyond ±242 draws tile 0, the same rule as the tile map.

**Why:** A chip with three colour wires *can* only see three trits, so folding is what hardware would do. "Only +1 and −1 show" means a sprite table full of junk stays invisible instead of spraying sprites everywhere. Using the map's rule for sprite tiles keeps one rule for both.
**Consequences:** All three are tested in `VideoChipTest`. SPEC §4.7 and §5 note them.

## D-022 — Web spike: TeaVM compiles triada-core and the assembler   (Milestone M06, 2026-11-07)
**Situation:** CLAUDE.md asks for an early check that a current libGDX web backend can compile `triada-core`, so problems surface now rather than at the end.
**Options:** libGDX has two web routes:
- The official **GWT** backend (`gdx-backend-gwt` 1.14.2, built on GWT 2.11). GWT compiles Java *source code* and supports only part of the newest Java language and library.
- The community **TeaVM** backend (`gdx-teavm` 1.4.0, November 2025, built on TeaVM 0.13.0). TeaVM compiles *bytecode*, so the Java language version matters much less.

**What we did:** A throwaway build (`docs/spikes/m06-teavm/`) compiles the project's real `triada-core` and `triada-assembler` sources with TeaVM 0.13.0 into one JavaScript file. A small main program then:
- assembles a "hello" program;
- runs it on a `Machine`;
- renders a frame;
- times 60 frames of a trit-heavy loop.

We ran the result in Node.js 22.

**Result:**
- It works, with Java 25 bytecode, after one fix. TeaVM's class library lacks `Integer.toUnsignedLong`, which `SystemDevice` used for RAND. It was replaced by the equivalent `x & 0xFFFFFFFFL`.
- Output: `HELLO FROM TERNARY JS`, exit code 0, red border pixel.
- 60 frames (3.5 million instructions, mostly MIN/ROT, plus 60 renders) took about 0.7 s, roughly 1.3–1.4× real time.

**Decision:** The planned web build (a stretch goal) will use the TeaVM route. GWT was not tried: TeaVM already answers the question, and GWT's source-level compiler would need checking against records, sealed interfaces and pattern matching, which `triada-assembler` uses.
**Why:** CLAUDE.md asked whether it *can* work, early. It can, and the one incompatibility was trivial to fix.
**Consequences:**
- Keep `triada-core` and `triada-assembler` to plain JDK APIs. Re-run the spike (`./gradlew -p docs/spikes/m06-teavm teavm`) after milestones that add core code, to catch new incompatibilities early.
- Browser speed is enough but not lavish (about 1.3× real time in the worst case, measured in Node). If the web build needs headroom, the trit-wise operations are the first candidates for lookup tables (M1/M3 notes).
- The libGDX side of gdx-teavm (window, textures, sound) was **not** tested; that belongs to the web build itself.

## D-023 — Sound: 44,100 Hz, exact phase, levels, noise, once per frame   (Milestone M07, 2026-11-07)
**Situation:** SPEC §7 names the three registers and waveforms. It leaves open the sample rate, the loudness levels, what "noise" exactly is, what odd WAVE values do, and *when* a register change is heard.
**Decision:**
- **Sample rate** 44,100 Hz, 16-bit mono: exactly 735 samples per frame.
- **Exact phase:** each voice counts `phase += FREQ` per sample, wrapping at 44,100. Its position is exactly phase / 44,100 of a period, so pitches never drift.
- **Levels:** one voice at volume 13 swings ±10,000, and the sum of three voices stays within ±30,000, so it never clips. Volume scales linearly.
- **Triangle:** 0 → +1 → 0 → −1 → 0, aligned with the ternary square's quarters.
- **Noise:** a new random trit (−1, 0, +1) at every quarter period, from a seeded xorshift generator. So FREQ sets how "grainy" the noise is, and runs repeat exactly.
- **Odd values:** FREQ ≤ 0 is silent; VOL is clamped to 0 … 13 (as the spec says); WAVE uses its lowest trit, like BORDER (D-021).
- **Timing:** the chip reads its registers once per frame, after the CPU's turn. Every change within a frame is heard from the start of that frame's 735 samples.

**Why:** 44,100 Hz is the CD standard and divides evenly into 60 frames. Exact integer phase makes the sound testable sample by sample (a 441 Hz tone is exactly 100 samples per period). Noise made of trits fits a ternary machine, and tying its rate to FREQ makes the register mean something for noise too. Once-per-frame timing is simple and matches how most programs drive sound (from their frame loop). The alternative, timing each register write to the exact CPU cycle, would couple the chip to the CPU's cycle counter.
**Consequences:** A program can't make sound effects shorter than a frame (16.7 ms), or change pitch mid-frame for vibrato-like tricks. If that's ever needed, the chip could generate samples "up to now" whenever a register is written. SPEC §7 now lists these details.

## D-024 — Keys, joystick and buttons on a PC keyboard   (Milestone M07, 2026-11-07)
**Situation:** CLAUDE.md maps arrows to the joystick and Z/X/C to the buttons, and keyboard to the KEY queue "(ASCII)". It doesn't say which codes non-printing keys produce, or what happens when one key means two things.
**Decision:**
- **Typed printable ASCII** (32–126) enters the KEY queue as itself, upper or lower case as typed.
- **Special keys:** Enter is 10 (newline, the same code DEBUG_CHAR prints as a line break; Windows' `\r` is translated), Backspace is 8, Tab 9, Escape 27. Anything else, such as Delete or accented letters, is ignored.
- **Arrow keys** give JOY_X = right − left and JOY_Y = down − up, so both directions at once cancel out.
- **Z/X/C** press BTN_A/B/C *and* type their letters into the KEY queue; a program uses whichever it wants.
- **Focus:** when the window loses focus, all keys and buttons are released, because their key-up events would be missed.

**Why:** These are the plain-ASCII codes a 1985 machine would use. Making Enter equal to the newline means a program can print what it reads without translating. Feeding a letter to both places costs nothing and keeps the mapping simple.
**Consequences:** The monitor (M8) ends a command line on key 10 and deletes on key 8. A program that only wants joystick and buttons simply ignores the KEY queue; the queue just fills up and then drops keys (D-007). Remapping keys is not implemented; CLAUDE.md marks it optional.

## D-025 — Desktop audio: an audio thread fed through a ring buffer   (Milestone M07, 2026-11-07)
**Situation:** CLAUDE.md asks for samples to be streamed through libGDX's `AudioDevice` on its own thread, without stutter. `AudioDevice.writeSamples` blocks until the sound card has room. Two clocks are involved, the screen refresh and the sound card, and they never agree exactly.
**Options:**
- A) Write samples from the render thread: every wait would stall the picture.
- B) Run the whole emulation on the audio thread, so the sound card is the master clock: video and input would then need locking between threads.
- C) Emulate on the render thread (as in M6), and pass samples through a thread-safe buffer to an audio thread that only feeds the device.

**Decision:** C. `SampleRing` holds at most 4 frames (2,940 samples, 67 ms). When it is full, the oldest samples are dropped; when it is empty, the last sample value is repeated. The audio thread `SoundOutput` writes 512-sample blocks. OpenAL is configured with 6 buffers of 512 samples.
**Why:** C keeps the machine single-threaded (CLAUDE.md wants triada-core free of threads), and only one tiny, well-tested class is shared between threads. Dropping the *oldest* samples keeps the delay between key and sound short. Holding the last value instead of jumping to 0 avoids a click on an underrun.
**Consequences:** Total delay from key to sound is roughly one frame plus up to 67 ms of ring plus about 70 ms of device buffers. Fine for a piano demo, though a rhythm game might want it smaller. If the machine runs slower than real time, the sound has gaps (held values) rather than slowing down. "No stutter" has been verified by tests of the buffer logic and by checking that the audio device opens and the program runs. It has **not** been verified by a person listening.

## D-026 — The font: font8x8 (public domain), stored as bits, expanded at boot   (Milestone M08, 2026-11-07)
**Situation:** CLAUDE.md asks for a public-domain 8×8 font (it suggests font8x8 by Daniel Hepper) or our own, and for its source and licence to be recorded here.
**Decision:**
- **Source:** `font8x8_basic.h` from https://github.com/dhepper/font8x8, by Daniel Hepper. Its header and README both say "License: Public Domain". It is based on Marcel Sondaar's font8x8, itself based on IBM's public-domain VGA fonts. A copy of the file and its README is kept in `rom/third_party/font8x8/`.
- **Characters:** only printable ASCII 32–126 is used.
- **Conversion:** `rom/tools/FontToTasm.java` (JDK only, run once by hand) writes `rom/font.tasm` as 8 row values (0–255) per character, bit 0 = leftmost pixel, exactly as in the original. That is 760 words.
- **Expansion:** at boot, `loadfont` in the kernel turns the bits into tile pixels, using `MOD 2` and `DIV 2` (for numbers ≥ 0, TRIADA's rounding division acts exactly like a binary shift).
- **Colours:** text is light cyan (4, trits `0++`) on dark blue (−12, `--0`), with a blue border (−11).

**Why:** A well-known, legible font with an unambiguous licence. Storing bits (760 words) instead of finished tiles (1,520 words) keeps the ROM small, keeps the colours in one place, and shows the ternary-machine trick of extracting bits.
**Consequences:**
- The font's colours are fixed at boot. A program that wants other text colours redefines tiles 32–126 itself.
- `font.tasm` is generated: edit the converter, not the file.
- `KernelTest.fontTilesMatchTheOriginalFontBitForBit` compares every pixel with the original header file.

## D-027 — Text output and the monitor: the details   (Milestone M08, 2026-11-07)
**Situation:** SPEC §8 describes the kernel routines and monitor commands in one line each. Several details had to be decided, and one line of the spec doesn't fit the screen.
**Decision:**
- **The boot banner** is `TRIADA-85 NPO "TRIADA" 1985`, with single spaces: exactly 27 characters. The spec's version with three spaces is 29 characters and would wrap on a 27-column screen. SPEC §8.2 now shows the new line.
- **"Pending wrap":** PUTCHAR moves to the next line only when a character arrives *after* the 27th column is filled. So a full line followed by a newline makes no blank line.
- **PUTCHAR control codes:** 10 is a newline; 8 is a backspace (one column back, erasing; never past column 0). Every other value is written as a tile number, and 32–126 are the font. Below the last row the screen scrolls up by one row.
- **LOCATE** clamps the column to 0–26 and the row to 0–17.
- **CLS** fills the map with spaces and homes the cursor; it does not touch scroll registers or sprites.
- **The monitor's prompt** is `> `. Input lines hold at most 24 characters. Letters are made upper case. Only printable ASCII, Enter (10) and Backspace (8) are accepted. The cursor is a `_` that blinks about every 27 frames (FRAME / 27, rounded, MOD 2).
- **Output formats:**
  - `PEEK` prints `ADDR: VALUE 0tTRITS`, one line per word. Trits are shown without leading zeros. Long lines wrap; for example ROM addresses with large values take 30 characters.
  - `TERN` prints `N = 0tTRITS`.
  - `RUN` prints `READY.` when the program returns.
- **`?SYNTAX ERROR`** covers unknown commands, missing or extra arguments, a count below 1, numbers outside the word range, more than 12 trits, and characters glued to a number.

**Why:** Each choice is either what 1980s home computers did (pending wrap, upper-case monitor, `?SYNTAX ERROR`) or the simplest rule that keeps output readable on 27 columns.
**Consequences:** All of these are covered by `KernelTest` and `MonitorTest`. Programs can rely on them, and the manual (M11) will describe them.

## D-028 — System variables, and how the host starts a program (AUTORUN)   (Milestone M08, 2026-11-07)
**Situation:** The ROM needs a few RAM variables (in 0–242, SPEC §2.3). Separately, once the machine boots its ROM, the host (CLI, desktop) still has to start a given program. The real ROM sets up the stack, the font and the screen, so simply jumping into the program would skip all that.
**Options:** For starting programs:
- A) Keep jumping straight to the program: the font would be missing and the stack would not be set up by the ROM.
- B) Boot, then let the host "type" `RUN 243` into the KEY queue: fragile, and slow.
- C) A system variable the host writes before the first frame, read by the ROM at the end of boot.

**Decision:** C.
- **System variables:** AUTORUN = 0, CURSOR_X = 1, CURSOR_Y = 2, RUN_DONE = 3, LINEBUF = 162–242 (see `rom/sysvars.tasm`).
- **Starting a program:** the host loads the program into RAM and puts its address into AUTORUN (`Rom.autorun()`). After the boot screen the ROM clears AUTORUN and calls the program exactly as `RUN` would. When the program returns, the ROM prints `READY.` and shows the prompt.
- **Noticing the end:** the ROM increments RUN_DONE each time a RUN/AUTORUN program returns. That is how `triada run` notices that a program has finished (exit code 0, with a note on stderr). It is a counter rather than an "is running" flag, so a program that returns within the same frame it started is still noticed.
- **CLI default:** `triada run` now boots by default. `--no-boot` keeps M5's direct start (PC = load address, SP = 59,049), which tests use when they count frames or check SP.
- **Desktop:** boots to the monitor, or autoruns a program given as an argument.

**Why:** C is how cartridges with auto-start worked on real machines. It costs one word of RAM and four instructions, and the program gets exactly the environment it would get from `RUN`.
**Consequences:**
- A booted run takes about 48 frames (0.8 s) before the program starts: the font plus the chime.
- Boot sets up tiles 32–126 and the tile map. A program whose `.tri` image *contains* data for those areas has it overwritten by boot, so programs should set up graphics at run time, as `colors.tasm` and `piano.tasm` do.
- `RET` at the top level of a program now returns to the monitor instead of halting.

## D-029 — The ROM is assembled during the build into a Java class   (Milestone M08, 2026-11-07)
**Situation:** CLAUDE.md: "The ROM is assembled during the build and embedded in the emulator." `triada-core` can't depend on the assembler, and core does no file I/O.
**Options:**
- A) Commit a pre-assembled `.tri` file and load it at run time: it can go stale, and needs file or resource access.
- B) Assemble at build time into a resource file.
- C) Assemble at build time into Java source.

**Decision:** C, in a new module `triada-rom`.
- Its Gradle task `generateRom` runs `RomGenerator`, which lives in a separate `generator` source set that may use the assembler. It assembles `rom/rom.tasm` and writes `RomData.java` and `build/rom/rom.lst`.
- The words are stored as strings, 4 characters per word from a 27-letter alphabet. 27⁴ = 3¹², so each character holds exactly three trits. The strings are split into chunks below Java's 64 KB limit for one constant.
- The ROM's symbols are included, so tests and tools can ask for `Rom.address("AUTORUN")` instead of hard-coding numbers.
- `Rom.newMachine()` and `Rom.autorun()` are the public entry points. The CLI and desktop depend on `triada-rom`.

**Why:** Any change to `rom/*.tasm` is assembled by the next build, so the emulator can't run an old ROM by mistake. The ROM needs no file or resource loading at run time, which suits the web build (D-022). Strings avoid the method-size limit that a 19,683-entry `int[]` literal would hit.
**Consequences:** An assembly error in the ROM fails the build with the usual `file:line:column` messages. `triada-core`'s own tests keep using a machine with an empty ROM, as before.

## D-030 — Debugging support in the core: stop before the instruction, finish the frame later   (Milestone M09, 2026-11-07)
**Situation:** A debugger must stop the machine *at an instruction*, in the middle of a frame, and later continue as if nothing had happened. Until M8, `Machine.runFrame()` always ran a whole frame.
**Options:**
- A) Let the frontend step the CPU itself and do its own frame bookkeeping, which would duplicate `runFrame()`'s logic.
- B) Build stopping and resuming into `Cpu` and `Machine`.

**Decision:** B.
- `Cpu.run(budget, breakAt, ignoreFirst)` stops *before* an instruction whose address is a breakpoint, and reports why it stopped (`Cpu.Stop`: BUDGET, WAIT, HALT or BREAKPOINT).
- `Machine` keeps `cyclesLeft` for the current frame. A breakpoint stop leaves the frame open: no render, no sound, no FRAME + 1. The next `runFrame()` continues the same frame and executes the instruction it stopped at first.
- `Machine.step()` runs one instruction, and finishes the frame if that instruction was WAIT or the budget's last cycle.
- Breakpoints live in a `boolean[]` over all 531,441 addresses, created only when the first breakpoint is set and dropped when the last is removed.
- `ignoreBreakpointAtPc()` lets "continue" leave a spot the debugger paused on.

**Why:** The rules for when a frame ends belong in exactly one place. Stopping and continuing must not change what a program computes or when its frames end; a test checks that a frame interrupted 100 times still gets exactly 59,049 cycles. Without breakpoints the CPU loop pays one null check per instruction; the benchmark shows no measurable change (12.2× real time).
**Consequences:** The CLI and tests don't change: without breakpoints `runFrame()` always completes a frame. The picture and sound are produced only when a frame ends, so while stepping in the middle of a frame the screen doesn't change. That is authentic: the real chip draws once per frame too.

## D-031 — The debugger's interface: scene2d with a code-built skin and the ROM font   (Milestone M09, 2026-11-07)
**Situation:** CLAUDE.md asks for the debugger in scene2d.ui. scene2d needs a Skin and a font. libGDX's sample skin isn't part of the library, and its default font is proportional, which would put trits and registers out of line.
**Decision:**
- **Skin:** made in code from one white pixel, tinted per colour; no asset files.
- **Font:** the TRIADA's own ROM font at 2×. Its 95 glyphs are drawn into a Pixmap, and libGDX reads an AngelCode BMFont description generated in memory (a `FileHandle` subclass whose `read()` returns the text), so libGDX's own parser handles the metrics.
- **Layout:** with the debugger open, the screen moves to the top left at 2×, the memory viewer goes under it, and a 464-pixel panel on the right shows status, buttons, registers (decimal and 12 trits), the disassembly around PC and the breakpoints.
- **Keys** (as in common PC debuggers): F1 shows/hides; F5 continue/pause; F11 step; F10 step over. They are only active while the panel is shown, except F1.
- **Breakpoints:** click a disassembly line, or type an address (decimal or `0t…`).
- **Automatic opening:** the panel opens by itself when a breakpoint or a fault pauses the machine.
- **Step over** on a JSR runs to the instruction after it. That stop counts only when SP is back at its old value, so recursion works. A user breakpoint inside the subroutine wins.
- **Pixel coordinates:** the window uses `HdpiMode.Pixels`, so layout and mouse coordinates are real pixels.

**Why:** One font everywhere makes columns line up and makes the debugger look like part of the machine. A code-built skin keeps the project free of extra files and dependencies. All the debugger's *decisions* live in the plain class `Debugger`, which has 16 tests; `DebuggerView` only displays.
**Consequences:** The panel needs a window of about 960 × 672 to show the screen at 2×; smaller windows fall back to 1×. Typing goes to the TRIADA unless a debugger text field has the keyboard. Enter in a field applies it and gives the keyboard back.

## D-032 — Loading programs: `--load`, and dropping a triskette on the window   (Milestone M09, 2026-11-07)
**Situation:** CLAUDE.md asks for `--load file.tri` and drag-and-drop ("insert triskette").
**Decision:**
- `--load FILE` on the desktop command line, or (as before) just a file name.
- Dropping a file on the window powers the machine on (RAM cleared, ROM kept), boots, and starts the program through AUTORUN (D-028), exactly like switching on with a cartridge in. Only the first file of a multi-file drop is used.
- Both `.tri` files and `.tasm` sources (assembled on the spot) are accepted.
- Breakpoints survive a new triskette, so you can set them first and then drop the program. Errors appear in the debugger's status line and on stderr.

**Why:** Power-on plus AUTORUN is what a real machine would do, and it reuses M8's tested path.
**Consequences:** Dropping is wired to the same `Debugger.insertTriskette()` that the unit tests exercise. The drop *event* itself could not be simulated by our window-testing tools (see the M9 document).

## D-033 — The games share an include library, the same controls and the same register habits   (Milestone M10, 2026-11-08)
**Situation:** Three games in assembly need the same small chores: copy words, fill the tile map, a random number in a range, print at a position, paint tiles. They also need controls a player can guess.
**Options:** A: copy the helpers into each game (simple, but three copies to fix). B: put them into the ROM (then they would be part of the machine's fixed interface and the SPEC). C: a source file the games `.include`.
**Decision:** C. `programs/lib/game.tasm` holds the helpers and is included at the *end* of each game, because it contains code and the game's first instruction must stay at the load address. Its routines follow the ROM's calling convention (R0–R2 in, R0 out, R3–R8 kept). Every game uses the joystick (arrow keys) to move and Z (button A) to start, restart and do the main action. TROIKA also uses X and C (buttons B and C) and the R key. Presses react on the frame the button goes down, not while it is held, except OBLAKA's fire button, which repeats on purpose. Inside a game, R6 always holds IO_BASE and R7 the address of the game's variables. The game's own routines may change R0–R5.
**Why:** Including source is what a 1985 programmer would do with a library listing, and it keeps the ROM and the SPEC unchanged. A fixed R6/R7 makes every I/O access `[R6+NAME]` and every variable `[R7+V_NAME]`, which is short and easy to read.
**Consequences:** `ProgramsTest` assembles only `programs/*.tasm`, so the library is tested through the games that include it. A game must not use R6 or R7 for anything else.

## D-034 — TROIKA: a ternary "lights out" whose moves are trit-wise instructions   (Milestone M10, 2026-11-08)
**Situation:** CLAUDE.md asks for a three-state puzzle built on ternary itself, of our own design.
**Options:** A: a sliding puzzle with three colours (ternary only in the paint). B: a balance or scales puzzle (nice, but the moves are ordinary arithmetic). C: a grid of trits where a move changes a cross of cells, like the binary "lights out" toy, with moves that are exactly the CPU's trit-wise operations.
**Decision:** C. A 5 × 5 board of trits. A move works on the cell under the cursor and its four neighbours: Z rotates them up (−→0→+→−), X rotates them down, C flips them (−↔+, 0 stays). The goal is a board of all 0. Each board row is one machine word (column c is trit 4 − c), and a move is computed on whole rows with ROT, TMUL and a mask word, never cell by cell. A level is made by applying (level + 2) random moves to a solved board, so it is always solvable. That number is shown as PAR. If the random moves happen to cancel out, the level is scrambled again.
**Why:** It makes the player think in trits, and it makes the program show off what a ternary CPU can do that a binary one can't do directly (rotating every trit of a word in one instruction). Rotations alone would be a linear puzzle, where the order of moves never matters. The flip does not commute with the rotations: on a 0 cell, "up then flip" gives −1 but "flip then up" gives +1. So the order matters, which makes the puzzle harder to solve by formula.
**Consequences:** PAR is the scramble length, so it is an upper bound, not necessarily the shortest solution (a test searches for solutions within PAR and finds them). The puzzle's rules exist twice, in the game and in the test's model, and the tests compare the two after every move.

## D-035 — OBLAKA: a fixed status line made of sprites, and tiles painted from masks   (Milestone M10, 2026-11-08)
**Situation:** The shooter scrolls the whole tile map, but its score and lives must stay still. It also needs many small tiles in several colours.
**Options for the status line:** A: stop scrolling a strip of rows (the SPEC has no split screen). B: rewrite the text into the map every frame at the scrolled position (it would still jitter by up to 7 pixels). C: sprites, which never scroll.
**Options for the tiles:** draw every tile in every colour with `.pix` (long, and three copies of each enemy), or store black-and-white masks and colour them at start-up.
**Decision:** The score is 5 sprites showing digit tiles, and the ships left are 3 sprites. Text (title, GAME OVER) goes into the tile map only while the sky stands still at SCROLL_Y = 0. Tiles are "masks" (pixels 0 or 1) coloured by `maskpaint` with *word = bg · 20440 + (fg − bg) · mask*, which works because a pixel word is a plain sum of the pixels times 19683, 729, 27 and 1. With bg = 0 the background is transparent, ready for sprites. The digits are the ROM font's characters turned back into masks (`glyphmask`), using the font colours fixed in SPEC §8 (4 on −12). TROIKA paints its cells the same way.
**Why:** Sprites are what they are for, and 8 of the 27 are enough. Painting from masks is short, shows a nice property of the pixel packing, and gives a game three coloured enemies from one drawing.
**Consequences:** 17 sprites are left for the ship, 4 shots and 12 enemies, so the game never runs out of sprites. `glyphmask` depends on the ROM's font colours; if they ever change, SPEC §8 and the library must change together.

## D-036 — The owner's manual: in-universe chapters whose examples are tested   (Milestone M11, 2026-11-08)
**Situation:** CLAUDE.md asks for an in-universe user manual "styled as a 1985 manual", including a type-in listing page from *Trit & Tryte*. A manual that only *looks* right could easily say things the machine doesn't do.
**Options:** A: one long document. B: chapters like a real booklet, written in the voice of 1985, with an honest note about the emulator. For the listing: C: assembly source only (needs an assembler, which the machine itself doesn't have). D: POKE lines that the built-in monitor accepts, plus the source.
**Decision:** B and D. `docs/manual/` holds a cover with contents, seven chapters and a reference appendix. Its "About this edition" note maps the hardware words to the emulator (joystick = arrow keys, triskette = `--load` or drag-and-drop). Monitor examples are fenced as ```` ```monitor ````. `ManualTest` replays every one of them on a booted machine and compares the screen. The listing ("Triangle of Threes", `programs/triangle.tasm`, 143 words) is printed as 143 `POKE address 0t…` lines, with values in balanced ternary. The longest is 23 characters, inside the monitor's 24. The source is printed as well. The test checks that the POKE lines, the printed source and the program file are the same 143 words, then types every POKE line into the monitor, RUNs it and checks the picture.
**Why:** In 1985 a listing really was typed in at the computer's own prompt, and the monitor already has POKE. Tests keep the fiction honest: a wrong number in the manual fails the build.
**Consequences:** Changing the monitor's output or the program means changing the manual too, because the tests say so. The page is long (48 lines of three columns), like the real thing.

## D-037 — The CRT look: a shader in the frontend only, and an off-screen capture mode   (Milestone M11, 2026-11-08)
**Situation:** CLAUDE.md offers an optional CRT shader (a libGDX `ShaderProgram`). A shader can't be checked by the headless tests, and checking it in a visible window would take over the screen of whoever is using the computer (this happened in M10).
**Options:** For the effect: A: change the picture in `triada-core` (would change every golden image, and the core must stay frontend-free). B: a GLSL fragment shader used only while the desktop draws the picture. For checking it: C: screenshots of the visible window. D: a capture mode that renders into an off-screen frame buffer of a hidden window and saves a PNG.
**Decision:** B and D. `CrtEffect` loads `shaders/crt.vert` and `crt.frag`. The effects are slight barrel curvature, scanlines (each picture row bright in the middle), red-green-blue phosphor stripes, and a vignette. It is off by default; `--crt` starts with it on and F2 toggles it. If the graphics card can't compile it, it stays off and the error is logged. `--capture FILE [--frames N]` runs the machine N frames, draws the picture at 3× (with the CRT look if `--crt` is given) into a `FrameBuffer`, saves it with javax.imageio and quits, without ever showing the window. Command-line parsing moved into `LaunchOptions` so it can be unit-tested.
**Why:** The machine stays exactly as specified; only the presentation changes, and only when asked. The capture mode makes the effect checkable and makes documentation pictures reproducible, without disturbing the user.
**Consequences:** The shader's look is checked by eye on captured pictures; tests cover what can be tested without a graphics card (the options, the row order of a capture, and that the shader sources declare what the code sets). The flat picture remains the default and the reference.

## D-038 — The README's pictures are made by a test from the real machine   (Milestone M11, 2026-11-08)
**Situation:** CLAUDE.md asks for a finished README "with screenshots or GIF". Hand-made screenshots go stale silently.
**Options:** A: take screenshots by hand. B: generate them from the machine with a repeatable command and check them in the normal test run.
**Decision:** B. `DocImagesTest` (in `triada-cli`) draws the boot screen, the three games (two of them after their test autopilots have played a while), and the type-in listing. It writes each at 3× to `docs/images/` when run with `-Dtriada.updateDocImages=true`, and otherwise compares the stored files pixel by pixel. It also writes `oblaka.gif`, 3 seconds of the OBLAKA autopilot at 20 pictures per second, at 2×, as an endlessly looping GIF made with javax.imageio from a 27-colour indexed palette (exactly the machine's colours). The CRT picture comes from the desktop's capture mode (D-037).
**Why:** The pictures can't drift from the machine, and the build fails if they do. GIF needs a palette, and the TRIADA has exactly 27 colours, so nothing is lost.
**Consequences:** A change to a game's look means recreating the pictures with one command. The GIF is only checked for being there (60 pictures, 480 pixels wide), not compared picture by picture.

## D-039 — The web version: gdx-teavm, a shared frontend module, the same machine without the debugger   (Milestone M12, 2026-11-08)
**Situation:** The stretch goal asks for a web build using a libGDX web backend, published on GitHub Pages. The user asked for the web version to match the download, but without the debugger.
**Options:** For the backend: A: libGDX's official GWT backend (compiles Java *source*, behind on new Java features). B: gdx-teavm 1.6.2 (August 2026, built on TeaVM 0.15, made for libGDX 1.14.2, our version), which compiles *bytecode*. D-022 already showed TeaVM compiles `triada-core` and `triada-assembler`. For the code: C: a separate web app that copies the desktop's drawing and keyboard code. D: move those parts into a module both use.
**Decision:** B and D. The new module `triada-app` holds what both frontends share: `MachineScreen` (texture, integer scale, CRT), `CrtEffect`, `InputMapper` and `FrameClock`. `triada-desktop` keeps the window, the debugger, the command line, drag-and-drop from the OS and the sound thread. The new `triada-web` holds `WebLauncher`, `WebApp` and `WebPage` (a few lines of JavaScript glue) plus its own `index.html`. The web version has the same machine, ROM, programs, keys and CRT look (F2). It has no debugger, no command line and no capture mode. Programs are chosen with links (`?program=oblaka`) or by dropping a `.tri` or `.tasm` file on the page. The bundled programs are shipped as source and assembled in the browser.
**Why:** One shared module means the two versions can't drift apart: a change to the picture or the keyboard reaches both. TeaVM was already proven on our code. Assembling in the browser needs no extra build step, and it makes dropping `.tasm` files work like on the desktop.
**Consequences:** `triada-app` may only use libGDX's core API. Anything desktop-only (files, threads, `java.nio.file`) stays in `triada-desktop`. The web build needs the gdx-teavm Gradle plugin from Maven Central. Two compiler warnings that come from TeaVM's own jars are switched off in `triada-web` only (`-Xlint:-classfile`, `-proc:none`).

## D-040 — The CRT shader's source moves into the code   (Milestone M12, 2026-11-08)
**Situation:** In M11 `CrtEffect` read `shaders/crt.vert` and `crt.frag` from the class path. A browser has no class path: gdx-teavm would have to copy the files and download them before start.
**Options:** A: declare the shader files as web assets and load them through `Gdx.files` on both platforms. B: keep the GLSL text in Java text blocks inside `CrtEffect`.
**Decision:** B. `CrtEffect.VERTEX` and `CrtEffect.FRAGMENT` hold the source. The resource files and the loading code are gone.
**Why:** Nothing to load means nothing that can fail to load, on either platform. Java text blocks keep the GLSL as readable as the files were. This is the same idea as the ROM, which is built into the code too (D-029).
**Consequences:** `CrtEffectTest` checks the names in the text, as `LaunchOptionsTest` checked the files in M11, and also that the fragment shader declares a float precision, which WebGL requires.

## D-041 — Sound in the browser: one small buffer per frame, scheduled back to back   (Milestone M12, 2026-11-08)
**Situation:** gdx-teavm doesn't support libGDX's `AudioDevice` (raw sample output); `newAudioDevice` throws. A browser page also has no threads like the desktop's audio thread (D-025), and browsers keep sound off until the visitor clicks or types.
**Options:** A: an AudioWorklet (a sound program running beside the page) fed through shared memory: smooth, but shared memory needs special server headers that GitHub Pages can't send. B: an AudioWorklet fed by messages: more code. C: for every machine frame, turn its 735 samples into a tiny Web Audio buffer and schedule it to start exactly where the previous one ends.
**Decision:** C, about 20 lines of JavaScript in `index.html` (`triadaSound.play`). `WebPage.playSamples` hands over each frame's samples. The player keeps a 60 ms cushion. If it falls behind (the tab was busy), it starts again 60 ms ahead. If it gets more than 250 ms ahead, it drops the frame. The page starts the machine with a "Click to switch on" button, which also unlocks sound (replaced by D-043).
**Why:** It is the smallest thing that works on every current browser and on a static host. A web page's own sound clock does the timing the desktop's audio thread does.
**Consequences:** About 60 ms of delay between a key and its sound. A tab in the background stops (browsers pause hidden pages), and the sound with it. The button means every page load starts with a click, which matches switching a computer on.

## D-042 — Publishing: a GitHub Actions workflow builds, tests and deploys to GitHub Pages   (Milestone M12, 2026-11-08)
**Situation:** The web version should be on GitHub Pages (repository `droidek314/Triada-85`). Pages serves plain files only.
**Options:** A: build on a PC and commit the output to a `gh-pages` branch (build output in git, easy to forget). B: let GitHub build it on every push with a workflow and the official Pages actions.
**Decision:** B. `.github/workflows/pages.yml` runs on every push to `master`: it sets up JDK 25, runs `./gradlew build :triada-web:gdx_teavm_web_js_build` (all tests, then the web build), uploads `triada-web/build/dist/js/webapp` and deploys it. The repository's Pages source is set to "GitHub Actions".
**Why:** No build output in git, and the tests act as a gate: a commit that breaks a test is never published.
**Consequences:** The site is at https://droidek314.github.io/Triada-85/. The page uses only relative paths, so it works in that sub-folder. `gradlew` must be executable in git for the Linux build machine.

## D-043 — The web page switches itself on; sound follows the first key   (Milestone M12, 2026-11-08)
**Situation:** The first published page had a "Click to switch on" button (D-041), so that the browser would allow sound from the boot chime on. The user found the extra click unnecessary, and asked for a page with less text on it.
**Options:** A: keep the button. B: start the machine as soon as the page has loaded, with sound muted until the first key press or click (browsers don't allow more).
**Decision:** B. `index.html` calls `main()` on load and creates the `AudioContext` straight away. The first `keydown` or `pointerdown` resumes it, and until then `triadaSound.play` skips frames. The key table and the notes moved into a "Help" section that opens and closes; clicks inside it don't take the keyboard away from the machine.
**Why:** The machine is ready the moment the page is, and the first thing almost everyone does is press a key, which unmutes it.
**Consequences:** The boot chime is usually not heard (it plays while the page is still muted). Everything typed or played afterwards is heard.

## D-044 — Phase 2 (TRIBASIC) merged into CLAUDE.md and SPEC.md, adapted to the released machine   (Milestone M13, 2026-11-08)
**Situation:** The task file `TRIBASIC.md` adds the triskette storage device and TRIBASIC as milestones "M12–M14" and as SPEC sections B.1–B.10. It asks for the released behaviour to win where the two differ, and for every adaptation to be logged.
**Options:** For the numbering: A: renumber the released web version (M12) and its documents, links and decisions. B: keep M12 as the web version and number the new milestones M13–M15.
**Decision:** B. CLAUDE.md §3 gets "Phase 2" with M13 (triskette), M14 (TRIBASIC I) and M15 (TRIBASIC II). The high-level-language stretch goal is marked as replaced by TRIBASIC. SPEC.md becomes version 1.1: B.1 → §2.2 and the new §2.5, B.2 → §5 and the new §5.1, the new routines → §8.1 and the new §8.5, the monitor's `BASIC` command → §8.3, B.3–B.10 → the new §10. Checked against the released machine: I/O registers 23–27 are free (only 0–22 are defined), the range −59,048 … −20,413 is unmapped, the next free jump-table entries are −19,669 … −19,663, and Esc is already key code 27 (D-024). One adaptation: the task's boot screen shows `TRIADA-85   NPO "TRIADA" 1985` with three spaces, but the released first line uses single spaces so that it is exactly one 27-character screen line (D-027). The released line is kept.
**Why:** M12 is published and referenced from the README, the index, the glossary and four decisions; renumbering it would break links for no gain. Everything else in the task fits the released machine unchanged.
**Consequences:** Wherever the task file says M12, M13 or M14, read M13, M14 or M15. SPEC.md describes v1.1 parts before they exist; each such section is marked with the milestone that builds it.

## D-045 — The triskette device: an interface in the core, and the cases SPEC §5.1 left open   (Milestone M13, 2026-11-08)
**Situation:** SPEC §5.1 defines the drive's registers and commands, and CLAUDE.md asks for a storage interface with no file I/O in `triada-core`. Some cases weren't specified: a SAVE from outside RAM or with a negative length, a DIR buffer that is too small, an unknown command, and how DISKLOAD asks for "load to the file's own address".
**Options:** For the core: A: the device reads and writes files itself (simple, but breaks the core rule and the web build). B: the device talks to a `TriskettePort` interface that each host implements. For DIR: C: write as many names as fit. D: write nothing and say how much room is needed. For "home": E: a fourth argument register (the convention only has R0–R2). F: a negative address in R1, since negative addresses are never RAM.
**Decision:** B, D and F. `TrisketteDevice` (I/O 23–27) sits behind `TriskettePort` (list, read, write, delete). `MemoryTriskette` is the default and the tests' triskette, with an optional size limit to test −2. Ports report failures as `TrisketteException` carrying −2 or −4; any other exception (for example a broken file on the host) also becomes −4. SAVE needs a whole block inside RAM and a length ≥ 0, else −4. DIR writes nothing if it doesn't fit, returns −2 and puts the words needed into TS_LEN. An unknown command gives −4. Power-on clears the registers but not the files. The ROM routines live in `rom/disk.tasm` and leave S set from the status. SPEC §5.1 and §8.5 record all of this.
**Why:** The interface keeps the core portable (the browser build uses the same device with browser storage). Writing nothing on a too-small DIR is the simplest rule that still lets a program ask "how much room do I need?". The negative-address trick keeps the routines within the calling convention.
**Consequences:** Each new host needs only four small methods. TRIBASIC's `SAVE`, `LOAD`, `FILES`, `ERASE`, `BSAVE`, `BLOAD` and `BRUN` (M15) will be built on these routines.

## D-046 — Triskettes on the PC: a folder of .tri files, in triada-cli, shared with the desktop   (Milestone M13, 2026-11-08)
**Situation:** The desktop needs a `triskettes/` folder (path configurable), and the CLI needs `--disk <dir>`. Both need the same folder code. `triada-core` must stay free of file I/O, and `triada-app` is compiled for the browser too (D-039).
**Options:** A: a copy in each module. B: a new module just for host file helpers. C: put it in `triada-cli`, which already holds the host-side file code (`FileSourceLoader`, `ScreenDump`), and let the desktop depend on `triada-cli`.
**Decision:** C. `FolderTriskette` stores each file as `DIR/NAME` in the `.tri` format (SPEC §9.5). Names are matched without regard to case, files whose names aren't valid triskette names are ignored, and the folder is created on the first save. A full disk gives −2, other failures −4. `triada run --disk DIR` uses it; without `--disk` the triskette is empty and in memory. The desktop uses `--disk DIR`, default `triskettes/` in the working directory (which `.gitignore` excludes).
**Why:** One implementation, no new module, and no file code in the shared or core modules. The desktop already depends on the assembler and the ROM; one more library dependency costs nothing.
**Consequences:** On the desktop, export and import are just files: copy a `.tri` into `triskettes/` (with a valid name) and the machine sees it; copy one out to keep it.

## D-047 — Import and export: the existing drop becomes "import", the web page gains an export   (Milestone M13, 2026-11-08)
**Situation:** CLAUDE.md asks first whether import/export already exists. The released version has import in one direction only: dropping a `.tri` or `.tasm` file on the desktop window or the web page (and `--load`, `?program=`) loads it into RAM and runs it. Nothing stores files, and there is no export.
**Options:** A: build separate Import/Export features next to the drop. B: extend the existing drop so that it also stores the file, and add only what is missing.
**Decision:** B. A dropped file is now also stored on the triskette, under a name made from the host file name (`TrisketteNames.forHostFile`: upper case, letters and digits only, `.tasm` stored as the assembled `.TRI`, the base shortened to fit 12 characters, so `trizmeyka.tasm` becomes `TRIZMEYK.TRI`). Machine code then runs as before. A `.TB` file (TRIBASIC) is only stored, because running it as machine code would make no sense. `--load` and the web's `?program=` links stay "run only", as before. On the web, a "Triskette" panel lists the files: clicking a name downloads it (export), and an "Import a file…" button does the same as dropping, for people who don't drag files. The browser triskette keeps each file in local storage as `triada85.triskette.NAME`, the `.tri` bytes in Base64, with every access in try/catch (`WebTriskette`). On the desktop the `triskettes/` folder is the import/export (D-046).
**Why:** It reuses the drop users already know and adds the one missing direction. Names derived from file names avoid asking the user anything.
**Consequences:** Dropping a game a second time replaces its stored copy. Browser storage belongs to one browser on one computer; export is how a file leaves it. Files with long names are shortened on import, so the manual must show the stored names.

## D-048 — TRIBASIC's interpreter: assembled with the main ROM, tokens, records, and the rules SPEC §10 left open   (Milestone M14, 2026-11-08)
**Situation:** SPEC §10 says what TRIBASIC does, not how it is stored or run, and leaves some behaviour open (the layout in RAM, how keywords are stored, what FOR does when the start is already past the limit, what a nested IF's MAYBE belongs to). The interpreter must be TRIADA assembly in the extension ROM.
**Options:** For the build: A: assemble TRIBASIC on its own, reaching the kernel only through the jump table. B: assemble it together with the main ROM, so it can call the monitor's internal routines too. For storage: C: keep lines as typed text. D: tokens, as the 1985 machines did. For the open behaviour: the simplest rule, or the 1985 one where they differ.
**Decision:** B and D. `rom/basic.tasm` is included by `rom/rom.tasm` with `.org -59048`; `RomGenerator` splits the result into the two ROM areas (the I/O registers between must stay empty). Lines are records `[length][number][tokens and text][0]`; each keyword is one negative word (all 68 of SPEC §10 from the start, so saved programs keep their tokens when M15 adds statements). Variables (8-character names, 9 words each) follow the program; FOR/GOSUB frames go on a control stack at 45,000. The expression evaluator is recursive descent over SPEC §10.2's levels; comparisons use `CMP`, a `BR3` and a 3-word table per operator (the answers for <, = and >). Because no instruction can branch on the O flag, `+` and `-` detect overflow by comparing first. Behaviour choices: the FOR body runs at least once; a nested IF owns the rest of its line; keywords are whole words (`FORM` is a name); keys typed while a program runs wait in a 16-key buffer while Esc is checked before each statement; READY. starts on a new line; an AUTORUN program returns to TRIBASIC with an empty program. All of this is in SPEC §10.9.
**Why:** Sharing the monitor's routines (`printnum`, `printtern`, `celladdr`, `runprog`) avoids a second copy of each. Tokens make lines shorter and LIST tidy. The 3-word table turns six comparison operators and `<=>` into one piece of code and shows how naturally the CPU's three-way compare fits three-valued logic. The behaviour choices are the simplest consistent rules; where the 1985 machines had one, it is the same.
**Consequences:** The main ROM and TRIBASIC share one symbol table, so their label names must not clash (TRIBASIC's start with `b_`, `st_` and similar). Keywords need spaces around them. The interpreter is 3,105 words, 8 % of the extension ROM; M15 adds strings, arrays and the machine statements.

## D-049 — Booting into TRIBASIC: what changes for the released machine   (Milestone M14, 2026-11-08)
**Situation:** SPEC §10.8 makes the machine boot into TRIBASIC. Version 1.0 booted into the monitor, and the manual, its tested examples, the README's boot picture and many tests relied on that.
**Options:** A: keep the monitor as the first screen. B: boot into TRIBASIC as specified, and move everything that needs the monitor behind `MON`.
**Decision:** B. The kernel prints its banner and jumps to `BASIC_COLD`; AUTORUN programs still run first and then return to TRIBASIC. The monitor gains `BASIC`. Tests that use the monitor type `MON` first (`Triada.booted()`, `ManualTest.bootToMonitor()`); the boot screen tests, the golden boot image and the README's boot picture show TRIBASIC's screen. The manual (chapters 1, 2, 3, 7 and the appendix) now says that the computer greets you with TRIBASIC and that `MON` leads to the monitor.
**Why:** It is what the spec asks, and every monitor feature stays reachable with one word.
**Consequences:** Everything that runs machine code from the host (the games, `--load`, the web's links) works as before, because AUTORUN skips the command line entirely.

## D-050 — The TRIBASIC test suite: programs and expected screens as plain files   (Milestone M14, 2026-11-08)
**Situation:** CLAUDE.md asks for at least 40 TRIBASIC test programs in `tests/basic/`, each with an expected screen, run by typing the program, `RUN`, and waiting for READY.
**Options:** A: write the programs inside Java test code. B: plain `.bas` and `.expected` files, read by one runner.
**Decision:** B. `BasicSuiteTest` (in `triada-rom`, next to the ROM it tests) runs every `tests/basic/NAME.bas`. A line `---` splits a file into a setup (typed first) and a session (typed after the screen is cleared; default `RUN`). In the session, `{ESC}` is the Esc key, `{BS}` Backspace and `{WAIT}` lets 10 frames pass. The runner waits up to 5,000 frames for READY. with every key taken, then compares the screen's rows (trailing spaces and empty rows left out) and saves what it saw to `build/basic-actual/` on a mismatch. The expected screens were worked out by hand from the spec, and three deliberately planted bugs (AND as MAX, IF's branches swapped, a wrong `<=`) were each caught.
**Why:** Anyone can read and add a test without knowing Java, and the files double as small examples of the language. The existing typing helper and screen reader (`Triada`) are reused.
**Consequences:** `tests/basic/` is a Gradle input of the ROM tests, so changing a program re-runs them. A test can only see what is on one screen (18 rows).

## D-051 — Strings in fixed slots, typed expressions, and variables of any length   (Milestone M15, 2026-11-08)
**Situation:** SPEC §10.4 asks for strings of up to 80 characters and leaves "garbage collection or compaction" to M15; §10.5 asks for arrays, including balanced ones. M14's variables were fixed 9-word entries holding one number.
**Options:** For strings: A: a string heap with garbage collection, like the 1985 machines (compact in memory, but a collector in assembly is long and was slow on every one of them). B: a fixed 81-word slot for every string variable and array element, and temporary slots that are all freed when the next statement starts. For types: C: separate evaluators for numbers and strings. D: one evaluator that returns a value and its type.
**Decision:** B and D. A string is `[length][up to 80 characters]`. Every expression leaves its type in `B_TYPE` (0 number, 1 string; for a string R0 is the slot's address). Each operator checks its operands' types (`?TYPE MISMATCH`). Temporaries come from `b_tmpnew` (50,100 … 57,899) and are reset at the start of each statement; assignments copy into the variable's own slot. Variables became entries `[length][name][data]` of any size, so numbers, strings and arrays share one list; an array's name carries a `(`. `DIM` of an existing array is `?SYNTAX ERROR` (SPEC's error list has no "redimensioned"), and `CONT` stays unimplemented (optional).
**Why:** Nothing ever needs tidying up, so there is no collector to write, test or wait for, and every string operation takes a predictable time. Memory is plentiful: 39,757 words hold over 400 strings. One evaluator keeps the precedence rules in one place.
**Consequences:** A string variable costs 92 words, however short its text: `DIM A$(494)` already fills the memory. A statement can make at most 96 temporary strings, far more than any line of 80 characters can ask for.

## D-052 — The machine statements: quiet on bad numbers, and COLOR by pixels   (Milestone M15, 2026-11-08)
**Situation:** SPEC §10.6.2 lists TILE, DEFTILE, SPRITE, SOUND, BORDER, SCROLL, WAIT, COLOR, POKE and SYS but not what they do with numbers outside the screen or the chips, and leaves COLOR's design to M15.
**Options:** For bad numbers: A: an error (none of SPEC §10.7's messages fits). B: do nothing, as POKE into ROM does nothing. For COLOR: C: keep the font's bit patterns somewhere and redraw. D: work on the tiles' pixels: what has the font's foreground colour gets the new one, everything else the new background.
**Decision:** B and D. Columns, rows, sprites, voices and tiles outside their ranges are ignored; pixel and colour values are taken MOD 27, which in balanced ternary is exactly the range −13 … 13. COLOR reads each tile word's four pixels as `w MOD 27` (the lowest three trits) and `(w − pixel) / 27`, and builds the new word. It remembers the current colours in `B_FONTFG`/`B_FONTBG`; when both are equal the letters can't be told from the background any more, so the kernel's `loadfont` draws the font afresh first.
**Why:** Quiet limits match how the hardware itself behaves, and MOD 27 shows off balanced ternary. Working on pixels needs no copy of the font.
**Consequences:** A program that draws its own tiles 32 … 126 and then uses COLOR gets them re-coloured too. COLOR takes about one and a half frames.

## D-053 — Storage commands on top of the DISK routines   (Milestone M15, 2026-11-08)
**Situation:** SPEC §10.6.4 lists SAVE, LOAD, FILES, ERASE, BSAVE, BLOAD and BRUN. The triskette device and its routines exist since M13.
**Options:** A: drive the I/O registers again in TRIBASIC. B: call `disksave`, `diskload`, `diskdir`, `diskdel`.
**Decision:** B. A name without a dot gets `.TB` or `.TRI`; `ERASE` takes the full name. Status −1 is `?FILE NOT FOUND`, anything else `?DISK`. `SAVE` stores 243 up to and including the end marker; `LOAD` reads it back to 243 and validates it with `b_relink` (an invalid file means `NEW` and `?DISK ERROR`). `BLOAD`/`BRUN` load to the file's own address unless one is given; if the block covers the program or its variables, TRIBASIC does `NEW` and `READY.`, which is what happens after `BRUN` of a game (they load at 243).
**Why:** One implementation of the triskette, tested in M13, and simple rules a user can predict.
**Consequences:** A `.TB` file is just the program records, so it only means something to TRIBASIC; games are `.TRI` files and run with `BRUN`.

## D-054 — Paste-typing through one KeyFeeder, and typing ahead without losses   (Milestone M15, 2026-11-08)
**Situation:** CLAUDE.md asks for Ctrl+V on the desktop and the web and `triada run --type file.bas`, "as if typed very fast". The keyboard queue holds 27 keys, and M14's Esc check moved typed-ahead keys into a 16-key buffer and dropped the rest.
**Options:** A: a bigger queue. B: feed the queue only as far as it has room, each frame, and make the Esc check stop taking keys when its buffer is full.
**Decision:** B. `KeyFeeder` (triada-core) pushes keys while `InputDevice.queuedKeys()` is below 27; the desktop and the web feed once per screen refresh, `triada run` once per frame. Line ends become Enter, `\r` is dropped, characters the font lacks are left out. `b_poll` leaves keys in the queue while `B_KEYS` is full. The web page catches the browser's `paste` event; the desktop reads libGDX's clipboard on Ctrl+V.
**Why:** No change to the machine's queue (SPEC §5), one piece of code for three hosts, and nothing lost even when a program runs while text is pasted.
**Consequences:** Pasting runs at up to 27 characters per frame (about 1,600 a second). A program that reads INKEY while text is being pasted receives it.

## D-055 — Release v1.1: a changelog, and the specification's version   (Milestone M15, 2026-11-08)
**Situation:** CLAUDE.md asks for a v1.1 release: README, changelog, and the GitHub Pages deployment, verified.
**Options:** A: a version only in the documents. B: also in the code (`MachineInfo.SPEC_VERSION`) and a `CHANGELOG.md` at the top of the repository.
**Decision:** B. `MachineInfo.SPEC_VERSION` is "1.1", `CHANGELOG.md` lists v1.0 and v1.1, and the README links it. Every push to `master` publishes the web version (D-042).
**Why:** The version is visible where a programmer looks (the code) and where a user looks (the changelog).
**Consequences:** The next change to the machine's behaviour needs a new version and a changelog entry.
