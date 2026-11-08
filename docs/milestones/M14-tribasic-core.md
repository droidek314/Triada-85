# Milestone 14 — TRIBASIC I: the interpreter core

## In one paragraph
A home computer of 1985 greeted you with a programming language, not with a wall of numbers. This milestone gives the TRIADA-85 its own: **TRIBASIC**. You switch on, see `READY.`, type `PRINT 6 * 7`, and the machine answers 42. You can type a numbered program, list it, change it and run it, with loops, subroutines and questions to the user. What makes it TRIADA's own is the third answer. Besides yes and no, every condition can be **unknown**, and an `IF` can have three branches: `THEN`, `MAYBE` and `ELSE`. TRIBASIC is written entirely in the machine's own assembly language, about 3,100 words of it, and lives in a second ROM.

## What was built
- **The extension ROM** (−59,048 … −20,413, SPEC §2.5): a read-only area in `MemoryMap` and `Bus`, filled at start-up from the assembled ROM (`Rom.extensionImage()`).
- **TRIBASIC** ([`rom/basic.tasm`](../../rom/basic.tasm)), assembled together with the main ROM:
  - **the line editor:** lines up to 80 characters, Backspace across screen rows, storing, replacing and deleting numbered lines;
  - **commands:** `LIST` with ranges, `RUN [line]`, `NEW`, `MON`;
  - **expressions** with SPEC §10.2's seven levels, decimal and `0t` numbers, `TRUE`/`FALSE`/`UNKNOWN`, Kleene's three-valued `AND`/`OR`/`NOT`, and the compare operator `<=>`;
  - **statements:** `PRINT`, `INPUT`, `ASK`, `LET`, `GOTO`, `GOSUB`/`RETURN`, `FOR`/`TO`/`STEP`/`NEXT`, `IF … THEN … MAYBE … ELSE`, `REM`, `END`, `STOP`;
  - **errors:** the messages of SPEC §10.7 that M14's statements can cause, and Esc as `BREAK IN <line>`.
- **Booting into TRIBASIC:** the kernel ends in `BASIC_COLD`. `MON` reaches the monitor, and the monitor's new `BASIC` command returns, with the program kept (`BASIC_WARM`).
- **The TRIBASIC test suite:** 66 programs in [`tests/basic/`](../../tests/basic/), each with its expected screen, run by `BasicSuiteTest`.
- **The Esc key** now reaches the machine on the desktop and the web (`InputMapper`).
- **Updated for the new boot:** the manual (chapters 1, 2, 3, 7, appendix), its tests, the README's boot picture and the ROM's own tests.

## What already existed (and was reused)
- **The typing helper and the screen reader** in the ROM tests (`Triada.typeOnly()`, `Triada.row()`, `Triada.screen()`), built in M8. CLAUDE.md asks for both; they were extended, not rewritten: `Triada.atPrompt()` now also recognizes TRIBASIC's READY., `Triada.clearScreen()` is new, and `Triada.basic()` boots to TRIBASIC.
- **The monitor's routines:** `putchar`, `print`, `printnum`, `cls`, `celladdr`, `runprog` and its `READY.` text. Assembling TRIBASIC with the main ROM lets it call them directly ([D-048](../DECISIONS.md#d-048--tribasics-interpreter-assembled-with-the-main-rom-tokens-records-and-the-rules-spec-10-left-open---milestone-m14-2026-11-08)).

## Why it was built this way

**In the machine's own language.** CLAUDE.md wants TRIBASIC in TRIADA assembly, like the ROM. That makes it part of the machine: it runs the same way on the desktop, in the browser and in the tests, and it obeys the same 59,049 instructions per frame as any program.

**Tokens, like 1985.** When you type `10 print a`, the word `PRINT` is stored as one number, and `a` as `A`. The line is shorter, running it is faster (one number instead of five letters to recognize), and `LIST` prints keywords neatly in capitals.

**Three answers, built from the CPU's own parts.** `AND`, `OR` and `NOT` are exactly the CPU's `MIN`, `MAX` and `NEG`. The compare operator `<=>` is exactly `CMP`. And the three-way `IF` is exactly `BR3`, the instruction that jumps three ways. TRIBASIC adds almost no logic of its own here; it shows what the machine already is.

**Open questions, answered simply.** The spec doesn't say whether `FOR I = 5 TO 1` runs its body. On the 1985 machines it ran once, and so it does here. Where a nested `IF` takes the next `ELSE`, the simplest rule is "the inner IF owns the rest of the line". All such choices are in SPEC §10.9 and D-048.

**Tests anyone can read.** Each test is a tiny program and the screen it should leave, as two text files ([D-050](../DECISIONS.md#d-050--the-tribasic-test-suite-programs-and-expected-screens-as-plain-files---milestone-m14-2026-11-08)). Every expected screen was worked out by hand from the spec, not copied from the interpreter's output.

## How it works — the plain-language version

### Two kinds of line
Type a line **with** a number in front, and TRIBASIC stores it in the program, in number order. Type the same number again and the new line replaces the old; type the number alone and the line is gone. Type a line **without** a number, and TRIBASIC does it at once. That is "direct mode", a calculator that also understands `PRINT`.

```
10 FOR I = 1 TO 3          stored
20 PRINT I * I             stored
RUN                        done at once: runs lines 10, 20, ...
1
4
9
READY.
```

### How a line is stored
Memory from address 243 holds the program, one line after another, like index cards in a box:

```
 243: [ 11 ][ 10 ][ " " ][ FOR ][ " " ][ I ][ " " ][ = ] ... [ 0 ]   line 10
 254: [ ...][ 20 ][ " " ][ PRINT ] ...                      [ 0 ]   line 20
      [ 0 ]                                                         the end
```

Each card starts with its own length, so TRIBASIC can jump from card to card. Then comes the line number, then the text with each keyword squeezed into one negative number (`FOR` is −8, `PRINT` is −1), then a 0. Letters and digits are ordinary positive numbers (their character codes), so a keyword can never be mistaken for text. After the last card comes a single 0, and after that the variables, 9 words each: the name (up to 8 letters) and the value.

### Three-valued logic
Everything TRIBASIC tests is a number, and its sign is the answer:

| Value | Meaning |
|---|---|
| positive (`TRUE` = +1) | yes |
| zero (`UNKNOWN` = 0) | don't know |
| negative (`FALSE` = −1) | no |

`AND` takes the smaller of two answers, `OR` the larger, and `NOT` flips the sign. With three values, these give Kleene's logic: "no AND don't know" is no, but "yes AND don't know" is don't know. A variable that was never given a value is 0, so it is honestly UNKNOWN.

```
10 ASK "IS IT RAINING"; R
20 IF R THEN PRINT "TAKE AN UMBRELLA" MAYBE PRINT "TAKE IT, JUST IN CASE" ELSE PRINT "ENJOY THE SUN"
```

`ASK` waits for Y, N or ?. Then line 20 takes one of three roads.

### Rounding division
`7 / 2` is 3, and `8 / 3` is also 3. TRIBASIC rounds to the **nearest** whole number, with halves rounded toward zero, because that is how the TRIADA's CPU divides. In balanced ternary this is the natural way: dropping the last trit of a number rounds it to the nearest multiple of 3, just as dropping the last digit of 47 to get 4 means 40 in decimal, but in ternary the rounding comes out *nearest* instead of *down*. The remainder `MOD` matches: `8 MOD 3` is −1, because 8 = 3 × 3 − 1.

### Stopping a program
Before each statement, TRIBASIC peeks at the keyboard. If you pressed **Esc**, the program stops with `BREAK IN 120` (the line it was on). Other keys you typed are kept for the next `INPUT` or `ASK`.

## How it works — the programmer's version

Read in this order.

### 1. The extension ROM and the build
[`MemoryMap.java`](../../triada-core/src/main/java/org/triada/core/memory/MemoryMap.java) has `EXT_ROM_START`, `EXT_ROM_END` and `EXT_ROM_SIZE`. `MemoryMap.regionOf()` calls the area ROM, and [`Bus.java`](../../triada-core/src/main/java/org/triada/core/memory/Bus.java) reads it from its own array, ignores writes, and fills it with `Bus.loadExtensionRom()`.

[`rom/rom.tasm`](../../rom/rom.tasm) includes `basic.tasm`, which begins with `.org -59048`, so one assembly produces one image from −59,048 to −1. [`RomGenerator.java`](../../triada-rom/src/generator/java/org/triada/rom/tools/RomGenerator.java):
- `RomGenerator.mainRom()` cuts out −19,683 … −1 and checks that nothing landed in the I/O registers between the two ROMs;
- `RomGenerator.extensionRom()` cuts out the extension, without trailing zeros.

[`Rom.java`](../../triada-rom/src/main/java/org/triada/rom/Rom.java) decodes both (`Rom.image()`, `Rom.extensionImage()`), and `Rom.newMachine()` loads both.

### 2. Boot and the main loop
[`rom/kernel.tasm`](../../rom/kernel.tasm) prints the banner, then `JZ BASIC_COLD` if no AUTORUN program is waiting. `BASIC_COLD` and `BASIC_WARM` are constants in [`rom/sysvars.tasm`](../../rom/sysvars.tasm), together with TRIBASIC's state at RAM 4 … 37 (`B_PROGEND`, `B_VAREND`, `B_CSP`, `B_LINE`, `B_LINEPTR`, `B_RUNNING`, `B_ERRSP`, `B_KEYN`, `B_KEYS`, `B_IDENT`).

In `basic.tasm`:
- `b_ready` prints READY. and falls into `b_main`;
- `b_main` saves SP in `B_ERRSP`, reads a line with `b_readline`, and stores it with `b_store` (if it starts with a digit) or tokenizes and runs it.

### 3. Tokenizing and storing
`b_tokenize` works in place: the write position never overtakes the read position, because a token (one word) replaces a keyword of at least two letters.
- Text in quotes is copied as is.
- A word is collected by `b_scanword` into `B_IDENT` and looked up by `b_keyword` in `kwtab`; a keyword becomes its token, and after `REM` the rest is copied.
- Any other word is copied in upper case.

`b_store` reads the line number (`b_linenum`, 1 … 65,535), finds the first record with a number at least that big, removes an old record with the same number (`b_delrec`), shifts the rest of memory up and writes the new record. Every edit clears the variables (`b_clearvars`). `b_relink` checks the records when coming back from the monitor (`BASIC_WARM`).

### 4. Running
`b_stmt` checks for Esc (`b_poll`), skips spaces, and dispatches:
- a token goes through `sttab` (routine addresses in token order);
- a letter means a LET without the word.

After a statement, `b_after` expects `:`, the end of the line, or a `MAYBE`/`ELSE` (which ends an IF's branch). At the end of a line, `b_eol` steps to the next record (`b_goline`). Statements that jump (`GOTO`, `NEXT`, `RETURN`, `IF`) reset SP to `B_ERRSP` through `b_goline`, `b_resume` or `b_comeback`, which also throws away their own return address. An error (`b_error`) resets SP the same way, prints `?MESSAGE ERROR IN line` (from `errtab`) and goes to `b_ready`.

### 5. Expressions
Each level calls the next stronger one and keeps its left-hand value on the stack: `b_expr` (OR) → `b_and` → `b_cmp` → `b_add` → `b_mul` → `b_unary` → `b_primary`. The comparisons:

```
        CMP R0, R1                      ; S = sign(left - right), never wraps
        BR3 bc_lt, bc_eq, bc_gt
bc_lt:  LDI R2, 0
        JMP bc_pick
bc_eq:  LDI R2, 1
        JMP bc_pick
bc_gt:  LDI R2, 2
bc_pick: MOV R0, R3
        SHT R0, 1                       ; operator * 3
        ADD R0, R2
        LDI R2, reltab
        ADD R0, R2
        LD R0, [R0]
```

- `CMP` leaves −1, 0 or +1 in the sign flag; `BR3` turns it into 0, 1 or 2.
- R3 is the operator's number (0 for `=` … 6 for `<=>`). `SHT R0, 1` multiplies it by 3, because each operator has three answers in `reltab`: for "less", "equal" and "greater". `<` has `1, -1, -1`; `<=>` has `-1, 0, 1`.

`b_addflag` adds with an overflow check. No instruction can branch on the O flag, so it compares first: for b ≥ 0, a + b passes 265,720 exactly when a > 265,720 − b, and 265,720 − b can't overflow.

### 6. The three-way IF in machine code
SPEC §10.3 asks for the IF to be built on `BR3`. This is `st_if`:

```
st_if:  JSR b_expr                      ; R0 = the condition
        PUSH R0
        JSR b_skip
        LDI R2, T_THEN
        CMP R0, R2
        JNZ err_syntax
        INC R7
        POP R0
        MOV R0, R0                      ; S = sign of the condition
        BR3 sif_false, sif_maybe, sif_then
```

- `JSR b_expr` evaluates the condition; it is kept on the stack while `THEN` is checked.
- `MOV R0, R0` sets the sign flag from the value, and `BR3` jumps three ways: negative to `sif_false`, zero to `sif_maybe`, positive to `sif_then`.
- `sif_false` and `sif_maybe` look for their keyword with `b_findbranch` and continue after it; `sif_then` simply continues.
- A bare line number after the keyword means GOTO. Running into the other branches' keywords ends the line (`b_after`).

### 7. The control stack
`GOSUB` pushes a 4-word frame and `FOR` a 7-word one, onto the control stack at 45,000. The frame's last word is its kind, so a search from the top always knows the frame's size:

```
 GOSUB: [line record][position][line number][F_GOSUB = 1]
 FOR:   [variable][limit][step][line record][position][line number][F_FOR = 2]
```

- `RETURN` drops FOR frames until it finds a GOSUB frame.
- `NEXT` drops inner loops until it finds its own; a GOSUB frame in the way is `?NEXT WITHOUT FOR`.
- `b_dropfor` ends an old loop over the same variable when a new FOR starts.

### 8. The tests
[`BasicSuiteTest.java`](../../triada-rom/src/test/java/org/triada/rom/BasicSuiteTest.java) reads `tests/basic/*.bas`, types the setup, clears the screen behind the machine's back (`Triada.clearScreen()`), types the session (default `RUN`) and waits for READY. (`Triada.waitForPrompt()`). It then compares the screen with `NAME.expected`.

The token table, for reference (−1 is first):

```
 -1 PRINT   -2 INPUT   -3 ASK     -4 LET     -5 GOTO    -6 GOSUB   -7 RETURN
 -8 FOR     -9 TO     -10 STEP   -11 NEXT   -12 IF     -13 THEN   -14 MAYBE
-15 ELSE   -16 REM    -17 END    -18 STOP   -19 DIM    -20 CLS    -21 LOCATE
-22 LIST   -23 RUN    -24 NEW    -25 MON    -26 CONT   -27 AND    -28 OR
-29 NOT    -30 MOD    -31 TRUE   -32 FALSE  -33 UNKNOWN            -34 TILE
-35 DEFTILE -36 SPRITE -37 SOUND -38 BORDER -39 SCROLL -40 WAIT   -41 COLOR
-42 POKE   -43 SYS    -44 SAVE   -45 LOAD   -46 FILES  -47 ERASE  -48 BSAVE
-49 BLOAD  -50 BRUN   -51 ABS    -52 SGN    -53 RND    -54 PEEK   -55 JOYX
-56 JOYY   -57 BTN    -58 INKEY  -59 FRAME  -60 LEN    -61 LEFT$  -62 RIGHT$
-63 MID$   -64 CHR$   -65 ASC    -66 STR$   -67 VAL    -68 TERN$
```

## Build it yourself from scratch
1. **Decide the memory layout first:** where lines go, how a line is stored, where variables and the FOR/GOSUB stack live. Draw it.
2. **Write the line editor** (read a line, store or delete a numbered line, `LIST`) before any statement. With `LIST` working, you can see what you stored.
3. **Write the tokenizer** and make `LIST` turn tokens back into words. Test it on lower case, quotes and `REM`.
4. **Write the expression evaluator one level at a time**, from numbers and parentheses up to `OR`, testing each with `PRINT` in direct mode.
5. **Add statements:** `PRINT`, `LET`, `GOTO`, `IF`, then `FOR`/`NEXT` and `GOSUB`/`RETURN` with one shared control stack, then `INPUT` and `ASK`.
6. **Make every error go back to one place** that restores the stack. Then nothing can leave the stack in a mess, however deep the error happens.
7. **Write the tests as files** (program and expected screen), and work out each expected screen by hand. Then break the interpreter on purpose, and check that a test notices.

What tends to go wrong:
- An instruction that sets the flags between your test and your jump (`POP` sets S too).
- Forgetting that a keyword glued to a name is no keyword (`IF X=0THEN` reads `0T` as the start of a ternary number).
- Tests that wait for a prompt with no frame limit hang forever when the prompt changes. One did, here (see "Problems").

## How we know it works

```bash
./gradlew build
```

You should see `BUILD SUCCESSFUL`, with 639 tests and no failures (core 269, assembler 98, ROM 129, CLI 103, app 11, desktop 29). The ROM's 129 include the 66 programs of [`tests/basic/`](../../tests/basic/), each counted as one test. To run only the TRIBASIC suite:

```bash
./gradlew :triada-rom:test --tests "*BasicSuiteTest*"
```

The 66 programs cover:
- **every statement:** PRINT forms, INPUT (number, negative, `0t`, ?REDO), ASK with Y, N, ? and ignored keys, LET with and without the word, GOTO computed, GOSUB nested, RUN from a line, END, STOP, REM, FOR with steps, nesting, restart and the run-once rule, NEXT with and without a name;
- **every operator,** with precedence, rounding division and `MOD`, ternary numbers, and the documented wrap of `*`;
- **the truth tables** of AND, OR and NOT, the three-way IF in every order, missing branches, nested IFs, line-number branches and `:` inside branches;
- **every error M14 can cause:** SYNTAX, UNDEFINED LINE, RETURN WITHOUT GOSUB, NEXT WITHOUT FOR (also across a GOSUB), OUT OF MEMORY, DIVISION BY ZERO, OVERFLOW, TYPE MISMATCH, in a program and in direct mode;
- **the line editor:** replace, delete, LIST ranges, NEW, lower case, text in quotes that looks like keywords, and Backspace across screen rows;
- **Esc:** during a loop, during INPUT, and before a direct line has started.

Three planted bugs were each caught by the suite: AND computed as MAX failed 2 programs, IF's MAYBE and ELSE swapped failed 9, and a wrong answer for `<=` failed 1.

Other tests:
- `KernelTest`: the TRIBASIC boot screen, its golden image, the two entry points, and boot in under a second.
- `MonitorTest`: MON and BASIC, the program kept, a destroyed program forgotten, and AUTORUN returning to TRIBASIC.
- `MemoryMapTest` and `BusTest`: the extension ROM's boundaries.

In the browser (`./gradlew :triada-web:gdx_teavm_web_js_run`) the page boots straight into TRIBASIC, and a program typed in lower case (`10 print 6 7`; the browser pane dropped the `*` we meant to type) was stored and ran, printing `67`.

## Problems we hit and how we solved them
- **A test that never ended.** `KernelTest.bootIsQuick` waited for the monitor's `>` prompt with no frame limit. After the boot change that prompt never came, and the whole test run hung for ten minutes, until a thread dump showed where. It now uses `Triada.atPrompt()` with a 600-frame limit.
- **Esc that came too early.** The first `break_esc` test typed `RUN` and Esc at once. TRIBASIC saw the Esc before the direct line `RUN` had started and printed `BREAK` without a line number. That is correct behaviour; the test was wrong. The session format gained `{WAIT}`, and the early case became its own test (`break_direct`).
- **A flag overwritten by POP.** In `NEXT`, the overflow test was `MOV R2, R2` followed by `POP R1` and then the jump. `POP` sets the sign flag too, so the jump tested the wrong value. The test now comes after the `POP`. It was found by reading the code before the first run.
- **The O flag can't be tested.** The spec says `+` raises OVERFLOW "when the O flag is set", but no jump looks at O. The check compares before adding instead, with the same result (D-048).
- **READY. after `PRINT …;`.** In direct mode `FOR I=1 TO 3:PRINT I;:NEXT` would have printed `123READY.`. READY. now always starts a new line (`b_freshline`).
- **Esc never reached the machine.** D-024 had given Escape the key code 27, but only for a *typed* Esc character, and neither libGDX backend produces one: the desktop types characters only for Tab, Enter, Backspace and Delete, and browsers send no character for Esc. The suite types code 27 directly, so it couldn't notice. `InputMapper` now puts 27 into the queue when the Esc key goes down (`InputMapperTest.escapeKeyIsCode27`). Pressing a real Esc in a browser or window wasn't tried by us after the fix.
- **The manual was wrong after the boot change.** Its examples start at the monitor's `>` sign. The manual now says to type `MON` first, and its test does the same (D-049).

## Limitations and what comes next
- **No strings, arrays or machine statements yet:** `A$`, `DIM`, `CLS`, `LOCATE`, `POKE`, `SOUND`, `TILE`, `SAVE` and the rest give `?SYNTAX ERROR` until M15. Their keywords already exist as tokens.
- **Keywords need spaces:** `IFX>5THEN` is not understood.
- **`*` wraps** silently past ±265,720 (SPEC §10.2's documented limitation).
- **No `CONT`** after STOP or BREAK (optional in CLAUDE.md).
- **Errors are found when a line runs,** not when it is typed (as on the 1985 machines).
- **Speed:** a simple statement takes a few hundred instructions, so TRIBASIC runs roughly 100–200 statements per frame, several thousand per second. That is plenty for the 1985-style programs it is meant for, but it wasn't measured more exactly.
- **Next, M15:** strings, arrays, machine access, `SAVE`/`LOAD` on the triskette (M13), paste-typing, example programs, the TRIBASIC manual chapter, and the v1.1 release.

## New words
- **Control stack:** TRIBASIC's own stack of FOR and GOSUB frames, separate from the CPU's stack.
- **Direct mode:** a line typed without a number, done at once.
- **Interpreter:** a program that reads another program and carries it out step by step, instead of translating it first.
- **Kleene logic:** a logic with a third value, "unknown", in which "no AND unknown" is no and "yes AND unknown" is unknown.
- **Line number:** the number in front of a program line; it decides the line's place in the program.
- **Precedence:** which operator is worked out first, as × before + in school.
- **Recursive descent:** a way of reading expressions with one routine per precedence level, each calling the next stronger one.
- **Token:** a keyword stored as one number.
- **Tokenizer:** the part that turns typed keywords into tokens.
- **Truth table:** a table of the answer of a logic operator for every combination of inputs.
