# Milestone 15 — TRIBASIC II: strings, arrays, machine access, storage, and release 1.1

## In one paragraph
M14 gave the TRIADA-85 a language that counts and decides. This milestone makes it a complete home-computer BASIC:
- **text** to store and cut up, and **arrays** of numbered boxes, including the very ternary `DIM T(-5 TO 5)`, which has its box 0 in the middle;
- **statements that reach the picture and sound chips:** a program can now draw tiles, move sprites and play notes;
- **keeping programs:** `SAVE` and `LOAD` put them on the triskette from M13;
- **paste-typing:** you can paste a listing instead of typing it;
- **five example programs** and a manual chapter, *TRIBASIC: Programming with Three Answers*.

With that, the machine is released as **version 1.1**, on GitHub Pages like version 1.0.

## What was built
- **Typed expressions:** every value is a number or a string, and operators check that the types fit ([D-051](../DECISIONS.md#d-051--strings-in-fixed-slots-typed-expressions-and-variables-of-any-length---milestone-m15-2026-11-08)).
- **Strings** of up to 80 characters: `+`, `=`, `<>`, `<=>` and the rest, plus `LEN`, `LEFT$`, `RIGHT$`, `MID$`, `CHR$`, `ASC`, `STR$`, `VAL` and `TERN$`.
- **Arrays:** `DIM A(n)`, `DIM A(low TO high)` and `DIM A$(n)`; an array used without `DIM` gets 0 … 10. Using an index outside the bounds is `?BAD SUBSCRIPT`.
- **Machine statements:** `CLS`, `LOCATE`, `TILE`, `DEFTILE`, `SPRITE`, `SOUND`, `BORDER`, `SCROLL`, `WAIT`, `COLOR`, `POKE`, `SYS` ([D-052](../DECISIONS.md#d-052--the-machine-statements-quiet-on-bad-numbers-and-color-by-pixels---milestone-m15-2026-11-08)).
- **Machine functions:** `ABS`, `SGN`, `RND`, `PEEK`, `JOYX`, `JOYY`, `BTN`, `INKEY`, `FRAME`.
- **Storage:** `SAVE`, `LOAD`, `FILES`, `ERASE`, `BSAVE`, `BLOAD`, `BRUN` ([D-053](../DECISIONS.md#d-053--storage-commands-on-top-of-the-disk-routines---milestone-m15-2026-11-08)).
- **Paste-typing:** `KeyFeeder` in the core; Ctrl+V on the desktop and the web; `triada run --type FILE` ([D-054](../DECISIONS.md#d-054--paste-typing-through-one-keyfeeder-and-typing-ahead-without-losses---milestone-m15-2026-11-08)).
- **Examples** in [`programs/basic/`](../../programs/basic/): `ORACLE.bas`, `GUESS.bas`, `TERNCLOCK.bas`, `ZMEYKA.bas`, `TYPEIN.bas`, each with a smoke test.
- **The manual chapter** [`docs/manual/tribasic.md`](../manual/tribasic.md). Its 13 screen examples are checked by `BasicManualTest`, and its type-in listing is `TYPEIN.bas`.
- **Release v1.1:** [`CHANGELOG.md`](../../CHANGELOG.md), a README section on TRIBASIC with two new pictures, and `MachineInfo.SPEC_VERSION` = "1.1" ([D-055](../DECISIONS.md#d-055--release-v11-a-changelog-and-the-specifications-version---milestone-m15-2026-11-08)).
- **A fix found by the tests:** typing ahead while a statement ran could lose keys (see "Problems").

## Why it was built this way

**Strings without a garbage collector.** The 1985 BASICs kept strings in a heap that slowly filled with old text, then stopped for a "garbage collection" that could take seconds. TRIBASIC gives every string variable its own box of 81 words: the length and 80 characters. Strings worked out in the middle of a statement go into scratch boxes, which are all thrown away when the next statement starts. Nothing ever needs tidying up. The price is memory, about 92 words per string variable, and the TRIADA has plenty.

**One expression evaluator for both types.** Each value comes back with its type, number or string. `+` adds numbers or joins strings; comparing a number with a string is `?TYPE MISMATCH`. Keeping one evaluator keeps SPEC §10.2's precedence rules in one place.

**Quiet limits, as the hardware has them.** `TILE 30, 0, 65` is off the screen, and the real machine would simply not show it. So TRIBASIC does nothing, instead of inventing an error the spec doesn't list. Colours and pixel values are taken MOD 27, which in balanced ternary gives exactly the 27 colours −13 … 13.

**Storage on the routines that already exist.** `SAVE` and the rest call the `DISKSAVE` … `DISKDEL` routines from M13, so there is one tested way to the triskette.

**One paste-typist for three hosts.** The desktop, the web page and the command line all hand the text to the same `KeyFeeder`. It types only as fast as the 27-key queue takes it.

## How it works — the plain-language version

### Text in boxes
A string is a row of words: first how many letters, then the letters, one per word.

```
   A$ = "TRI"            [ 3 ][ T ][ R ][ I ][   ][   ] ... 81 words in all
   B$ = A$ + "ADA"       [ 6 ][ T ][ R ][ I ][ A ][ D ][ A ] ...
```

`A$ + "ADA"` is worked out in a scratch box, and then copied into B$'s own box. The next statement throws all scratch boxes away at once.

### Arrays with a middle
`DIM T(-2 TO 2)` makes five boxes numbered −2, −1, 0, 1, 2. TRIBASIC remembers the lowest and highest number. `T(I)` checks that I lies between them, then counts from the lowest. In a ternary world, a row of boxes around 0 is often what you want: one box for "less", "same" and "more".

### Re-colouring the letters
Every word of a tile holds four pixels, each three trits. `w MOD 27` is the lowest pixel, because the lowest three trits make a number from −13 to 13. Then `(w − pixel) / 27` moves the next pixel down. `COLOR` takes each word of the letters apart like this, gives every pixel that had the letter colour the new one, and every other pixel the new background, and puts the word together again.

Worked example: a word with pixels 4, −12, −12, 4 (light cyan, dark blue, dark blue, light cyan) is 4·19683 − 12·729 − 12·27 + 4 = 69,664. `69,664 MOD 27` is 4: the last pixel. `(69,664 − 4) / 27` = 2,580, and `2,580 MOD 27` = −12: the third pixel.

### The triskette from BASIC
`SAVE "SQUARES"` writes the program's memory, from 243 to the end marker, as the file `SQUARES.TB`. `LOAD "SQUARES"` puts it back at 243 and first checks that it really is a program. `BRUN "TROIKA"` loads a machine-code game to its own address and starts it. Games live where TRIBASIC keeps its program, so when the game ends TRIBASIC starts afresh.

### Pasting a listing
Copy a listing, press Ctrl+V in the TRIADA's window or page, and the text arrives as if typed by a very fast typist: 27 keys per frame, about 1,600 a second. A 20-line program is in within a second.

## How it works — the programmer's version

Read in this order, in [`rom/basic.tasm`](../../rom/basic.tasm).

### 1. Variables as entries
`b_varref` is now the single way to a variable. It reads the name (`b_varname`, which says whether it is a string), and then:
- for a plain variable, finds or makes its entry (`b_findvar`, `b_newvar`);
- for an array, after `(`, evaluates the index first. That evaluation may use other names, so it then reads the name again, adds `(` (`b_arrname`), finds or makes the array (`b_mkarray`, 0 … 10 if not DIMmed) and picks the element (`b_element`).

The result is an address, and `B_TYPE` says whether it holds a number or a string slot.

```
 entry:  [length][name: 10 words, 0-padded][data]
 number: data = 1 word         string: data = 81 words (a slot)
 array:  data = [low][high] and the elements (1 word or a slot each)
```

`st_dim` makes arrays with `b_mkarray`. It checks the size before multiplying (`count × 81` could pass the word's range) and refuses an array that already exists.

### 2. Typed expressions
Each level of the evaluator (`b_expr`, `b_and`, `b_cmp`, `b_add`, `b_mul`, `b_unary`, `b_primary`) now pushes the left side's type with its value. `b_neednum` and `b_strexpr` check types. In `b_cmp`, two strings go through `b_strcmp`, which returns −1, 0 or +1 just like `CMP`, so the same `BR3` and `reltab` give every comparison operator for text too:

```
        MOV R2, R2
        JZ bc_compare
        JSR b_strcmp                    ; strings: R0 = -1, 0 or +1 ...
        LDI R1, 0                       ; ... compared with 0 below
bc_compare: CMP R0, R1
        BR3 bc_lt, bc_eq, bc_gt
```

- R2 is the left side's type. For numbers (0), `CMP R0, R1` compares the two values directly.
- For strings, `b_strcmp` turns the two strings into one number with the right sign. Comparing that with 0 gives the same three outcomes, so the rest of the code is shared.

`b_add` joins strings with `b_concat` (over 80 characters is `?STRING TOO LONG`). Text in quotes becomes a temporary string (`b_littemp`).

### 3. Temporary strings
`b_tmpnew` hands out the next slot between `TEMP_START` and `TEMP_END`; `b_stmt` resets the pointer `B_TMP` at the start of every statement. The string functions all build their result in a new temporary:
- `b_substr` serves `LEFT$`, `RIGHT$` and `MID$`, clamping its start and length to the string;
- `b_numtostr` serves `STR$`, and `b_terntostr` serves `TERN$`.

`st_letvar` copies the result into the variable's own slot with `b_strcopy`.

### 4. Functions
In `b_primary`, a token from −51 (ABS) to −68 (TERN$) jumps through `fntab` to its routine (`fn_abs`, `fn_left`, `fn_val` …). Each routine reads its own arguments with `b_numarg`, `b_strarg`, `b_open`, `b_comma` and `b_close`, and returns through `b_isnum` or `b_isstr`, which set the type.

### 5. Machine statements
`b_args` reads a list of numbers into `B_ARGS`. `b_inrange` checks a value against the screen's or a chip's range.
- `st_deftile` packs four pixels at a time with `b_pack4` (Horner's rule with MOD 27).
- `st_color` takes every word of tiles 32 … 126 apart into pixels and puts it together again. Its loop ends when the place value, multiplied by 27 a fourth time, wraps from 3¹² to 0.
- `st_sys` (and `BRUN`) call machine code through `b_call`: push R7, push the return address, push the target, `RET`.

### 6. Storage
- `b_fname` evaluates the name and copies it into `FNAME`, adding `.TB` or `.TRI` if there is no dot.
- `b_diskstatus` turns a routine's status into `?FILE NOT FOUND` or `?DISK`.
- `st_load` validates what it loaded with `b_relink`, which now reports whether the records were a program.
- `b_afterload` does `NEW` if `BLOAD`/`BRUN` loaded over the program.

### 7. Paste-typing
[`KeyFeeder.java`](../../triada-core/src/main/java/org/triada/core/io/KeyFeeder.java): `KeyFeeder.feed()` pushes keys while `InputDevice.queuedKeys()` is below 27. Three hosts use it:
- [`TriadaApp.java`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java) starts one on Ctrl+V with libGDX's clipboard;
- [`WebApp.java`](../../triada-web/src/main/java/org/triada/web/WebApp.java) starts one when the page caught a `paste` event (`WebPage.takePaste()`);
- [`RunCommand.java`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java) starts one for `--type`, without a program if none is given.

In the ROM, `b_poll` now stops taking keys while `B_KEYS` is full, so the queue keeps them.

## Build it yourself from scratch
1. **Give every value a type** before adding strings: a flag next to R0 that every level of the evaluator sets and checks.
2. **Choose the string storage.** Fixed slots plus per-statement temporaries are the simplest that works. If you choose a heap instead, you need a garbage collector and a test that runs it.
3. **Make variable entries carry their length.** Then numbers, strings and arrays live in one list, and finding one is "skip by length".
4. **Arrays:** store the bounds with the elements and check them on every access. Compute the size carefully, since the multiplication can overflow.
5. **Machine statements:** decide what happens to numbers outside the screen or the chip before writing them, and write the decision down.
6. **Storage:** build on the disk routines, map their status codes to errors, and validate what `LOAD` brings in.
7. **Paste-typing:** never push more keys than the queue takes. Check the interpreter doesn't drop keys while a statement runs.
8. **Test the examples by playing them** (the GUESS test halves the range like a person would), and **test the manual** by typing its examples in.

What tends to go wrong:
- An index expression that uses another variable overwrites the name you were looking up: read it again afterwards.
- `POP` sets the flags: test a value after the last `POP`, not before.
- Example lines longer than 80 characters, or wider than a 27-column screen row in a manual transcript.

## How we know it works

```bash
./gradlew build
```

You should see `BUILD SUCCESSFUL`, with 691 tests and no failures (core 271, assembler 98, ROM 176, CLI 106, app 11, desktop 29).

- **The TRIBASIC suite** ([`tests/basic/`](../../tests/basic/)), 27 new programs, 93 in all:
  - strings (variables, every function, comparison, clamping, too long, type errors, INPUT into a string);
  - arrays (plain, balanced, of strings, automatic, re-DIM, array beside a scalar of the same name);
  - functions (ABS, SGN, RND's range, PEEK, BTN, JOYX, INKEY, FRAME);
  - the machine statements (TILE, CLS, LOCATE, POKE, DEFTILE with its MOD 27 wrap, SPRITE, SOUND, BORDER, SCROLL, COLOR, SYS);
  - storage (SAVE/LOAD round trip, FILES, ERASE, BSAVE, BLOAD, BRUN, every disk error, BLOAD over the program).
- **`BasicExamplesTest`** (6 tests):
  - ORACLE answers and sleeps;
  - GUESS is won by halving the range after each hint;
  - TERNCLOCK shows 12 as `++0` in red-red-grey tiles, and stops on a key;
  - ZMEYKA runs into the wall and shows its score, and also turns when the joystick is pushed;
  - TYPEIN weaves its pattern and thanks you.
- **`BasicManualTest`** (14 tests): all 13 `tribasic` examples of the manual chapter, typed in and compared with the screen, and the type-in listing equal to `TYPEIN.bas`.
- **`KeyFeederTest`** (2 tests) and **`RunCommandTest.typeFeedsTheKeyboardAndTribasicRunsIt`**: `triada run --type` with a TRIBASIC program that writes to the debug port and exits with code 7.
- **`DocImagesTest`**: the README's new pictures `tribasic.png` and `ternclock.png`, made from the real machine and compared pixel by pixel.

The previous TRIBASIC tests (66 programs) all pass unchanged after the evaluator was rewritten, apart from one that now uses `CONT` instead of `DIM` as an example of a statement that doesn't exist.

To try paste-typing from the command line:

```bash
./gradlew :triada-cli:run --args="run --type programs/basic/TYPEIN.bas --frames 600 --dump-screen build/typein.png"
```

`build/typein.png` then shows the end of the listing as it was typed in (the file has no `RUN`, so it is only typed; a file that ends with a `RUN` line also runs, as in `RunCommandTest`).

## Problems we hit and how we solved them
- **Typing ahead lost keys.** The first `files_erase` test typed six commands at once. Part of `BSAVE` arrived as `BSAV"DATA"`, because while `SAVE` ran, the Esc check moved the waiting keys into its 16-key buffer and dropped what didn't fit. Paste-typing would have lost far more. `b_poll` now leaves the keys in the 27-key queue while its buffer is full (D-054).
- **Four bugs found by reading before the first run:**
  - `VAL` lost its argument, because the temporary allocator overwrites R1;
  - the file-name builder tested a flag after a `POP`;
  - `SAVE` used an immediate value (−242) outside the ±121 an instruction can hold;
  - `DEFTILE` computed tile × 16 in a clumsy way.
- **Test lines that were too long.** Two suite programs and two manual examples were longer than a line (80) or a screen row (27) allows, so the machine cut or wrapped them. The tests and the manual were shortened; the machine was right.
- **ORACLE's questions wrapped.** "DO YOU BELIEVE IN TRITS (Y/N/?)" is wider than the screen, which looked untidy; the questions became shorter.
- **The index overwrote the array's name.** In `A(I)`, evaluating `I` looks up I's name and overwrites `A`'s. The name is read again after the index, as the "What tends to go wrong" list above warns.

## Limitations and what comes next
- **Memory per string:** 92 words for a string variable however short; `DIM A$(494)` fills the memory.
- **Not reported:** `*` beyond ±265,720 wraps (SPEC §10.2), and machine statements with numbers outside the screen or chip do nothing (D-052).
- **No `CONT`.**
- **`BRUN` of a game ends the TRIBASIC program,** because the game loads where the program lives.
- **Paste-typing types exactly what was copied,** so a listing with very long lines is cut at 80 characters per line, as when typing.
- **In the browser,** TRIBASIC's `SAVE`, `LOAD` and `FILES` go through the same triskette device and the same browser triskette as M13, whose writing, listing and reading were checked in a browser then. TRIBASIC's own SAVE/LOAD in the deployed page was **not** tried by us: the release was published (build, 691 tests and deployment passed on GitHub, and the new page is served), but the browser pane we use pauses pages that aren't on screen, so the page could not be driven at release time.
- **Phase 2 is complete.** What could come next: the cassette-loading effect (the last stretch goal in CLAUDE.md), `CONT`, or a TRIBASIC that compiles to machine code for speed.

## New words
- **Array:** a row of numbered boxes under one name.
- **Changelog:** a list of what changed in each released version.
- **Clipboard:** where a computer keeps the text you copied, until you paste it.
- **Garbage collection:** tidying up memory that old values no longer use; TRIBASIC avoids it with fixed slots.
- **Paste-typing:** pasting text into the TRIADA, which types it in as if from the keyboard.
- **Release:** a version of a program given to its users, with a number like 1.1.
- **Slot:** TRIBASIC's 81-word box for one string: its length and up to 80 characters.
- **String:** a piece of text as a value, like "TRIADA".
- **Type (of a value):** whether a value is a number or a string.
