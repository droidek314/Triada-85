# TRIBASIC — Phase 2 Task for the Agent

This file adds **TRIBASIC**, the TRIADA-85's built-in programming language, plus the **triskette storage device** it needs for SAVE/LOAD.

- **Part A** is the work plan. Merge its milestones into `CLAUDE.md` §3.
- **Part B** is the specification. Merge it into `docs/SPEC.md` as new sections, and from then on treat it as part of the source of truth.

All rules in `CLAUDE.md` still apply in full, especially §5 (documentation): every milestone gets its full milestone document and passes the documentation checks.

---

# PART A — Work plan

## A.0 Before writing any code
1. TRIADA-85 v1.0 is **released and deployed on GitHub Pages**. Don't break it. Every existing program, game and test must still work at the end of each milestone.
2. Read the current `docs/SPEC.md`, `docs/DECISIONS.md`, `docs/ARCHITECTURE.md` and the ROM source. The released implementation may differ from the original spec (addresses, routine names, I/O usage). **Where they differ, the released behaviour wins**, unless it's a bug. Adapt this file's numbers to reality and log every adaptation in `DECISIONS.md`.
3. Look for features that already exist (for example import/export, file loading, paste-typing). **Reuse and extend them; never duplicate them.** List what you found at the top of the M12 doc.
4. Check for conflicts: I/O registers 23–27 and the address range −59,048 … −20,413 must be free (Part B). If they aren't, pick free ones and log it.
5. Write `docs/milestones/M12-…` etc. with a "Plan" section first, as usual.

## A.1 Milestones

### M12 — Triskette storage device
- Implement the device from Part B §B.2 in `triada-core`, behind a storage interface (`TriskettePort` or similar). It needs an in-memory implementation for tests, and **no file I/O in core**.
- Backends:
  - desktop: a `triskettes/` folder (configurable path);
  - headless CLI: `--disk <dir>`;
  - **web build: browser storage**, with all access wrapped in try/catch.
- **Import/export (conditional).** Users must be able to move files between their computer and the triskette, on both desktop and web. **First check whether the released version already has import/export** (for example for `.tri` files).
  - **If it does:** don't build a second one. Connect the existing feature to the triskette so that imported files appear in `FILES`, and files on the triskette can be exported. Extend it only where it's missing something, for example `.TB` files or a platform it doesn't cover. Record what already existed and what you changed in `DECISIONS.md`.
  - **If it doesn't:** add "Import file" and "Export file" buttons in the web build. On desktop, the `triskettes/` folder is enough.
  - Either way, the M12 milestone doc explains how a user gets a program in and out, step by step.
- Existing `.tri` games become loadable from the triskette.
- New kernel routines in the ROM jump table (next free entries): `DISKSAVE`, `DISKLOAD`, `DISKDIR`, `DISKDEL`. Document the calling convention.
- Tests: every command and every error code, plus a host round trip (save from the machine, file appears on the host, load back gives identical words).

### M13 — TRIBASIC I: the interpreter core
Written **in TRIADA assembly**, in the extension ROM (Part B §B.1).
- Line editor: entering, replacing and deleting lines, and direct mode. Commands `LIST`, `RUN`, `NEW`, `CONT` is optional.
- Numbers, variables, the full expression evaluator with the operator precedence of §B.4, and **three-valued logic** (§B.5).
- `PRINT`, `INPUT`, `ASK`, `LET`, `GOTO`, `GOSUB`/`RETURN`, `FOR`/`TO`/`STEP`/`NEXT`, `IF … THEN … MAYBE … ELSE`, `REM`, `END`, `STOP`.
- All errors in §B.9. Pressing Esc stops a running program with `BREAK IN <line>`.
- **The machine now boots into TRIBASIC** (§B.10). `MON` enters the old monitor and the monitor's `BASIC` command returns, with the program kept.
- Test infrastructure, all in Java test code:
  - a **screen-text reader** that turns the tile map into a string;
  - a **typing helper** that feeds text into the KEY queue;
  - a **TRIBASIC test suite**: `tests/basic/NAME.bas` plus `NAME.expected`. The runner types the program, types `RUN`, runs until `READY.` appears again (with a frame limit), and compares the screen text.
- At least 40 test programs, covering every statement, every operator, every error and the three-valued logic truth tables.

### M14 — TRIBASIC II: strings, arrays, machine access, storage and release
- String variables and string functions (§B.6), arrays (§B.7).
- Machine statements and functions: graphics, sound, input, `PEEK`/`POKE`/`SYS` (§B.8).
- `SAVE`, `LOAD`, `BSAVE`, `BLOAD`, `BRUN`, `FILES`, `ERASE` (§B.8.4).
- **Paste-typing:** Ctrl+V in the desktop and web frontends types clipboard text into the KEY queue, as if typed very fast. CLI: `triada run --type file.bas`.
- Example programs in `programs/basic/`:
  1. `ORACLE.bas`: a yes/no/unknown fortune teller using `ASK`.
  2. `GUESS.bas`: guess-the-number where the hints use `<=>`.
  3. `TERNCLOCK.bas`: a clock drawn in balanced ternary.
  4. `ZMEYKA.bas`: a small snake game in TRIBASIC.
  5. `TYPEIN.bas`: a short program presented as a *Trit & Tryte* magazine type-in listing (in the manual).
  Each one has a smoke test.
- In-universe manual chapter `docs/manual/tribasic.md`: *"TRIBASIC: Programming with Three Answers"*. It covers every statement with a short example, written as a friendly 1985 manual.
- **Release v1.1:** update the README (with a TRIBASIC section and screenshots), the changelog and the GitHub Pages deployment. Verify the deployed web build boots into TRIBASIC and can SAVE/LOAD through browser storage.

---

# PART B — Specification

## B.1 Memory map changes

| Range | Size | What |
|---|---|---|
| −265,720 … −59,049 | — | Unmapped (unchanged meaning) |
| **−59,048 … −20,413** | **38,636** | **Extension ROM: TRIBASIC lives here** (read-only) |
| −20,412 … −19,684 | 729 | I/O registers (unchanged) |
| −19,683 … −1 | 19,683 | Main ROM (unchanged) |

TRIBASIC entry points, each a 2-word `JMP` at the start of extension ROM:

| Address | Name |
|---|---|
| −59,048 | `BASIC_COLD`: clear the program, then start |
| −59,046 | `BASIC_WARM`: start, keeping the program in memory |

**RAM use** (inside the user area 243 … 39,999): tokenized program from 243 upwards, then variables, arrays and strings. The interpreter's own stacks (FOR/GOSUB) go in the free area above 45,000. The exact layout is the agent's decision, documented with a diagram. "WORDS FREE" means the user area minus the program and variables.

## B.2 Triskette storage device

There are no drives or tracks. A triskette is a flat list of named files on the host.

| k | Address | Name | R/W | Meaning |
|---|---|---|---|---|
| 23 | −20,389 | TS_CMD | W | Writing starts a command, which finishes instantly (see below) |
| 24 | −20,388 | TS_NAME | R/W | RAM address of the file name string (one char per word, 0-terminated) |
| 25 | −20,387 | TS_ADDR | R/W | RAM address of data |
| 26 | −20,386 | TS_LEN | R/W | Length in words (input for SAVE; output for LOAD and DIR) |
| 27 | −20,385 | TS_STATUS | R | 0 = OK, −1 = not found, −2 = no space, −3 = bad name, −4 = I/O error |

| TS_CMD | Command |
|---|---|
| 1 | SAVE: write TS_LEN words from TS_ADDR into file TS_NAME (overwrites) |
| 2 | LOAD: read the file into memory at TS_ADDR; TS_LEN ← words read |
| 3 | DIR: write all file names as consecutive 0-terminated strings starting at TS_ADDR, then one extra 0. TS_LEN is the space available on input and the words used on output |
| 4 | DELETE: remove file TS_NAME |
| 5 | LOAD-HOME: like LOAD, but to the file's own stored load address. TS_ADDR ← that address |

Rules:
- **Names** are 1–12 characters: A–Z, 0–9, and at most one `.`. Lower-case is converted to upper case.
- **Extensions by convention:** `.TB` is a TRIBASIC program and `.TRI` is machine code.
- **Host format:** every file is stored in the existing `.tri` container (header, load address, words). For SAVE, the load address is TS_ADDR.
- Writes outside RAM are silently clipped, and the status is −4.

## B.3 Program lines and editing
- A line starting with a number from 1 to 65,535 is stored in the program and replaces any line with that number. A number on its own deletes the line.
- A line without a number runs immediately (direct mode).
- Several statements go on one line, separated by `:`.
- Keywords are case-insensitive and stored as tokens. `LIST` prints them back in upper case.
- `LIST`, `LIST 100`, `LIST 100-200`, `LIST -200`, `LIST 100-`.
- `RUN [line]` clears variables, then runs. `NEW` clears program and variables.

## B.4 Values, variables and expressions

**Values:** whole numbers (machine words, ±265,720) and strings (§B.6).

**Literals:**
- decimal: `42`, `-7`
- ternary: `0t+-0`
- strings: `"HELLO"`
- truth constants: `TRUE` (= +1), `FALSE` (= −1), `UNKNOWN` (= 0)

**Variable names:** a letter followed by up to 7 letters or digits, all significant. A `$` suffix means a string variable. Keywords can't be variable names. **A variable that was never assigned is 0, so it reads as `UNKNOWN`.** Mention this in the manual.

**Operator precedence** (highest first):

| Level | Operators |
|---|---|
| 1 | `( )`, function calls |
| 2 | unary `-`, `NOT` |
| 3 | `*`, `/`, `MOD` |
| 4 | `+`, `-` (also string concatenation) |
| 5 | `=`, `<>`, `<`, `>`, `<=`, `>=`, `<=>` |
| 6 | `AND` |
| 7 | `OR` |

Arithmetic rules:
- `/` uses the machine's division, which **rounds to the nearest integer** (ties toward zero): `7/2 = 3`, `8/3 = 3`. `MOD` matches it, so `8 MOD 3 = -1`. Explain in the manual why this is natural in ternary.
- `+` and `-` raise `?OVERFLOW` when the O flag is set. `*` may wrap without an error; this is a documented limitation, unless the agent finds a cheap check (log it in DECISIONS).
- Division by zero raises `?DIVISION BY ZERO`.

## B.5 Three-valued logic (TRIBASIC's signature feature)

**Truth values:** TRUE = +1, UNKNOWN = 0, FALSE = −1. For any number used as a condition, **positive counts as TRUE, zero as UNKNOWN and negative as FALSE**.

**Comparisons:**
- `=`, `<>`, `<`, `>`, `<=` and `>=` return TRUE or FALSE.
- **`a <=> b`** ("compare") returns **−1, 0 or +1** (the sign of a − b; for strings, alphabetical order). It's exactly the CPU's CMP instruction.

**Logic operators:** `AND`, `OR` and `NOT` are the machine's trit-wise MIN, MAX and NEG. On truth values this is Kleene's three-valued logic:

| AND | F | U | T |
|---|---|---|---|
| **F** | F | F | F |
| **U** | F | U | U |
| **T** | F | U | T |

| OR | F | U | T |
|---|---|---|---|
| **F** | F | U | T |
| **U** | U | U | T |
| **T** | T | T | T |

`NOT T = F`, `NOT U = U`, `NOT F = T`.

**Three-way IF:**
```
IF cond THEN stmts MAYBE stmts ELSE stmts
```
- The THEN branch runs if cond is positive (TRUE), the MAYBE branch if cond is zero (UNKNOWN), and the ELSE branch if cond is negative (FALSE).
- Each branch runs until the next `MAYBE`/`ELSE` keyword of the same IF, or the end of the line. `MAYBE` and `ELSE` are both optional and may appear in either order.
- `THEN 100` (a bare line number) means `THEN GOTO 100`. The same goes for `MAYBE 200` and `ELSE 300`.
- It must compile to the CPU's `BR3` logic. Show the matching machine code in the milestone doc.

Example:
```
10 ASK "IS IT RAINING"; R
20 IF R THEN PRINT "TAKE AN UMBRELLA" MAYBE PRINT "TAKE IT, JUST IN CASE" ELSE PRINT "ENJOY THE SUN"
```

**`ASK "prompt"; var`** prints the prompt with `(Y/N/?)` and waits for a key: `Y` → TRUE, `N` → FALSE, `?` → UNKNOWN. Other keys are ignored.

## B.6 Strings
- At most 80 characters each. Assigning a longer string raises `?STRING TOO LONG`.
- `+` concatenates. `=`, `<>` and `<=>` compare. Mixing strings and numbers raises `?TYPE MISMATCH`.
- Functions: `LEN(s$)`, `LEFT$(s$,n)`, `RIGHT$(s$,n)`, `MID$(s$,start,n)` (1-based), `CHR$(n)`, `ASC(s$)`, `STR$(n)`, `VAL(s$)`, **`TERN$(n)`** (the balanced-ternary text of n, e.g. `TERN$(6) = "+-0"`).
- Strings are stored one character per word. Garbage collection or compaction is the agent's design; document it.

## B.7 Arrays
- `DIM A(n)` gives indices 0 … n. **`DIM A(-n TO n)`** gives a balanced array with a centre element at 0, in the spirit of the machine. `DIM A$(n)` also works.
- One dimension only. Using an array without DIM auto-dimensions it as 0 … 10. An out-of-range index raises `?BAD SUBSCRIPT`.

## B.8 Statements and functions

### B.8.1 Core statements
`PRINT` (`;` joins items, `,` moves to the next 9-column zone, and a trailing `;` suppresses the newline), `INPUT ["prompt";] var`, `ASK "prompt"; var`, `LET`, `GOTO`, `GOSUB`, `RETURN`, `FOR v = a TO b [STEP s]`, `NEXT [v]`, `IF`, `REM`, `END`, `STOP`, `DIM`, `CLS`, `LOCATE col,row`.

### B.8.2 Machine statements

| Statement | Effect |
|---|---|
| `TILE col,row,t` | Set a tile map cell (negative t = mirrored) |
| `DEFTILE t,row,p0,…,p7` | Set one row of tile t to 8 pixel values (−13…13) |
| `SPRITE i,x,y,t[,flags]` | Set sprite i (flags default +1) |
| `SOUND v,freq,vol,wave` | Set voice v (1–3) |
| `BORDER c` | Border color |
| `SCROLL x,y` | Background scroll |
| `WAIT n` | Wait n frames |
| `COLOR fg,bg` | Re-color the font tiles 32–126 (the agent's design) |
| `POKE addr,value` | Write a memory word |
| `SYS addr` | Call machine code (`JSR`); it returns with `RET` |

### B.8.3 Functions
`ABS(n)`, `SGN(n)` (returns −1, 0 or +1), `RND(n)` (random 1 … n), `PEEK(addr)`, `JOYX`, `JOYY`, `BTN(n)` (n = 1, 2, 3 for buttons A, B, C), `INKEY` (key code, or 0), `FRAME`, plus the string functions from §B.6.

### B.8.4 Storage commands

| Command | Effect |
|---|---|
| `SAVE "NAME"` | Saves the program as `NAME.TB` |
| `LOAD "NAME"` | Loads `NAME.TB` (replaces the program) |
| `FILES` | Lists the triskette |
| `ERASE "NAME.EXT"` | Deletes a file |
| `BSAVE "NAME",addr,len` | Saves raw words as `NAME.TRI` |
| `BLOAD "NAME"[,addr]` | Loads `NAME.TRI` (to its own address by default) |
| `BRUN "NAME"` | `BLOAD`, then `SYS` to its load address. Existing games run with this |

## B.9 Errors
Format: `?<MESSAGE> ERROR IN <line>` (no `IN …` in direct mode), then `READY.`

`SYNTAX`, `UNDEFINED LINE`, `RETURN WITHOUT GOSUB`, `NEXT WITHOUT FOR`, `OUT OF MEMORY`, `DIVISION BY ZERO`, `OVERFLOW`, `TYPE MISMATCH`, `BAD SUBSCRIPT`, `STRING TOO LONG`, `FILE NOT FOUND`, `DISK`.

Esc while running prints `BREAK IN <line>`. The frontends map the Esc key to KEY code 27.

## B.10 Boot
The machine boots straight into TRIBASIC, as most 1980s home computers did:
```
TRIADA-85   NPO "TRIADA" 1985
TRIBASIC V1.0
39757 WORDS FREE
READY.
```
`MON` switches to the machine-code monitor. The monitor's new `BASIC` command returns to TRIBASIC through `BASIC_WARM`, with the program kept.
