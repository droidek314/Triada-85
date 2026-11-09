# TRIADA-85 — Machine Specification

Version 1.1. Version 1.0 was the released machine (M0–M12). Version 1.1 adds the triskette storage device and TRIBASIC (§2.5, §5.1, §8.5, §10), built in milestones M13–M15 and released on 2026-11-08 (see `CHANGELOG.md`). This document is the **source of truth** for the machine. Its numbers (memory map, timing, screen, voices, devices, ROM images) are those of the **TRIADA-85 profile**, `MachineProfiles.TRIADA_85` in `triada-core`. Since M16 the emulator builds a machine from such a profile, so a later model would be a new profile with its own numbers (D-057). If code and spec disagree, the code is wrong — or the spec is changed on purpose and the change is logged in `docs/DECISIONS.md`.

---

## 0. The story (lore)

In our world, the Soviet ternary computer **Setun** (Moscow State University, 1958) was built, worked well, and was abandoned. Binary won.

In the world of TRIADA-85, ternary won in Eastern Europe. By 1985 the (fictional) research-and-production association **NPO "Triada"** ships the **TRIADA-85** home computer: a beige box with a membrane keyboard, a three-position joystick, a cassette port and a "triskette" drive. It's sold across the Eastern Bloc, and schoolchildren type in game listings from the (fictional) magazine *Trit & Tryte*.

The machine never existed. Everything in this repository should feel as if it did: boot screens, manuals, error messages and program listings. Lore is fun but never overrides this spec's technical rules.

---

## 1. Ternary basics

### 1.1 Balanced ternary
Normal computers use **bits** (0 or 1). TRIADA uses **trits** with three values: **−1, 0, +1**. This is *balanced* ternary: there's no separate sign, so negative numbers come naturally.

Written notation, used everywhere (debugger, docs, assembler):

| Trit value | Character |
|---|---|
| −1 | `-` |
| 0 | `0` |
| +1 | `+` |

Most significant trit first. Example: `+-0` = 1·9 − 1·3 + 0·1 = **6**. `-+` = −3 + 1 = **−2**.

### 1.2 Units

| Unit | Trits | Value range | Count of values |
|---|---|---|---|
| trit | 1 | −1 … +1 | 3 |
| tryte | 6 | −364 … +364 | 729 |
| **word** | **12** | **−265,720 … +265,720** | **531,441** |

The **word** is the machine's basic unit. Every register and every memory cell holds exactly one word.

### 1.3 Wrapping (overflow)
Any result outside the word range wraps. Formally, for a mathematical result `r`:

```
M = 531441                        // 3^12
wrapped = ((r + 265720) mod M + M) mod M − 265720
carry   = (r − wrapped) / M       // always −1, 0 or +1 for ADD/SUB
```

In balanced ternary, this is the same as "throw away the trits above position 11".

### 1.4 Trit positions
Trits in a word are numbered `t11` (most significant, weight 3^11) down to `t0` (least significant, weight 1).

---

## 2. Memory

### 2.1 Word-addressed memory
Memory is **word-addressed**: every address points to one 12-trit word. Addresses are themselves words, so they range from −265,720 to +265,720. **Address 0 is in the middle of memory.** This is a deliberate quirk of the machine.

### 2.2 Memory map

| Range (decimal) | Size | What |
|---|---|---|
| −265,720 … −59,049 | — | Unmapped (reads 0, writes ignored) |
| −59,048 … −20,413 | 38,636 | **Extension ROM**: TRIBASIC (read-only; §2.5; from M14, unmapped before) |
| −20,412 … −19,684 | 729 | I/O registers (§5) |
| −19,683 … −1 | 19,683 (3^9) | **ROM** (read-only; writes ignored) |
| 0 … 59,048 | 59,049 (3^10) | **RAM** |
| 59,049 … 265,720 | — | Unmapped (reserved for cartridges) |

### 2.3 RAM layout (convention, enforced by ROM and video chip)

| Range | Size | What |
|---|---|---|
| 0 … 242 | 243 | System variables (owned by ROM) |
| 243 … 39,999 | 39,757 | User program and data. **Programs load at 243** (= 3^5) |
| 40,000 … 43,887 | 3,888 | Tile patterns (243 tiles × 16 words) (§4) |
| 44,000 … 44,485 | 486 | Tile map (27 × 18) (§4) |
| 44,600 … 44,707 | 108 | Sprite table (27 sprites × 4 words) (§4) |
| 45,000 … 59,048 | 14,049 | Free / stack. Stack starts at 59,049 and grows **down** |

### 2.4 Power-on state
All RAM = 0. All registers = 0. Flags S = 0, O = 0. **PC = −19,683** (the first ROM word). The ROM sets SP (R8) to 59,049.

### 2.5 Extension ROM (v1.1)
TRIBASIC (§10) lives in the extension ROM, written in TRIADA assembly like the main ROM. It starts with two entry points, each a 2-word `JMP`:

| Address | Name | What |
|---|---|---|
| −59,048 | `BASIC_COLD` | clear the program, then start |
| −59,046 | `BASIC_WARM` | start, keeping the program in memory |

**RAM use** (inside the user area 243 … 39,999): the tokenized program from 243 upwards, then variables, arrays and strings. The interpreter's own stacks (FOR/GOSUB) go in the free area above 45,000. The exact layout is decided in M14 and documented with a diagram. "WORDS FREE" means the user area minus the program and variables.

---

## 3. CPU

### 3.1 Registers

| Name | Encoding (2 trits) | Notes |
|---|---|---|
| R0 … R7 | −4 … +3 | General purpose |
| R8 | +4 | General purpose, **used as SP (stack pointer)** by PUSH/POP/JSR/RET |
| PC | — | Program counter (not directly addressable) |

Register `Rn` is encoded as the value `n − 4`.

### 3.2 Flags
Both flags are **single trits**:

- **S (sign):** −1, 0 or +1. This is the sign of the last result. This replaces the separate "zero" and "negative" flags of binary CPUs.
- **O (overflow/carry):** −1, 0 or +1. This is the carry-out trit of ADD/SUB/ADDI.

### 3.3 Instruction encoding
Every instruction starts with one word:

```
 t11 t10 t9 | t8 t7 | t6 t5 | t4 t3 t2 t1 t0
   opcode   |  rd   |  rs   |     imm5
  (3 trits) |(2 tr.)|(2 tr.)|   (5 trits)
  −13…+13   | −4…+4 | −4…+4 |  −121…+121
```

Some instructions are followed by extra words (an immediate value or addresses). Unused fields must be assembled as 0; the CPU ignores them.

### 3.4 Instruction set (27 opcodes, all of them used)

"Sets S" means S = sign(result). Unless stated otherwise, an instruction leaves O unchanged.

| Op | Mnemonic | Words | Operation | Flags |
|---|---|---|---|---|
| 0 | `HLT` | 1 | Stop the CPU | — |
| 1 | `NOP` | 1 | Do nothing | — |
| 2 | `MOV rd, rs` | 1 | rd ← rs | S |
| 3 | `LDI rd, imm12` | 2 | rd ← next word | S |
| 4 | `LD rd, [rs+imm5]` | 1 | rd ← mem[rs + imm5] | S |
| 5 | `ST rd, [rs+imm5]` | 1 | mem[rs + imm5] ← rd | — |
| 6 | `PUSH rd` | 1 | R8 ← R8 − 1; mem[R8] ← rd | — |
| 7 | `POP rd` | 1 | rd ← mem[R8]; R8 ← R8 + 1 | S |
| 8 | `ADD rd, rs` | 1 | rd ← rd + rs | S, O |
| 9 | `SUB rd, rs` | 1 | rd ← rd − rs | S, O |
| 10 | `ADDI rd, imm5` | 1 | rd ← rd + imm5 | S, O |
| 11 | `MUL rd, rs` | 1 | rd ← rd × rs (wrapped) | S |
| 12 | `DIV rd, rs` | 1 | rd ← round(rd ÷ rs) (§3.5) | S, O on ÷0 |
| 13 | `MOD rd, rs` | 1 | rd ← rd − round(rd ÷ rs) × rs | S, O on ÷0 |
| −1 | `NEG rd` | 1 | rd ← −rd (flip every trit) | S |
| −2 | `MIN rd, rs` | 1 | trit-wise minimum (ternary AND) | S |
| −3 | `MAX rd, rs` | 1 | trit-wise maximum (ternary OR) | S |
| −4 | `TMUL rd, rs` | 1 | trit-wise product | S |
| −5 | `ROT rd, imm5` | 1 | rotate every trit "up" n times (§3.6) | S |
| −6 | `SHT rd, imm5` | 1 | shift by n trits (§3.7) | S |
| −7 | `CMP rd, rs` | 1 | S ← sign(rd − rs), computed without wrapping; no store | S |
| −8 | `JMP addr` | 2 | PC ← addr | — |
| −9 | `JSR addr` | 2 | PUSH return address; PC ← addr | — |
| −10 | `RET` | 1 | PC ← POP | — |
| −11 | `JIF cond, addr` | 2 | if condition(S) then PC ← addr (§3.8) | — |
| −12 | `BR3 aN, aZ, aP` | 4 | jump to aN if S=−1, aZ if S=0, aP if S=+1 | — |
| −13 | `WAIT` | 1 | Sleep until the next video frame (§6) | — |

Effective addresses (`rs + imm5`) wrap like any other word.

Source registers are read before anything is written (D-009). So `PUSH R8` pushes the value R8 had *before* the push, and after `POP R8`, R8 holds the popped value. SP (R8) and PC wrap like any other word when they pass the ends of the address range.

### 3.5 Division
- `round` = round to nearest; **ties round toward zero**. Examples: 7÷2 → 3 (3.5 → 3), −7÷2 → −3, 8÷3 → 3, 5÷3 → 2.
- So `MOD` results always satisfy |r| ≤ |rs|/2. Example: 8 MOD 3 = −1.
- Division by zero (in `DIV` **or** `MOD`): result 0, S = 0, **O = +1**. Otherwise DIV/MOD set O = 0. (Clarified in D-006.)

### 3.6 ROT (trit rotation)
"Up" means −1 → 0 → +1 → −1, applied to every trit independently. `n = imm5 mod 3` (mathematical mod, so n ∈ {0,1,2}). For example, `ROT R0, -1` rotates down once (= up twice). The assembler default is n = 1 when imm5 is omitted.

### 3.7 SHT (trit shift)
`n = imm5`. If n > 0: multiply by 3^n, dropping trits shifted past t11 (= wrapping). If n < 0: shift right by |n| trits, dropping the low trits. In balanced ternary that equals **rounding to nearest**, which is a nice property. The documentation should show it.

### 3.8 JIF conditions (stored in the rd field)

| cond | Assembler alias | Jumps when |
|---|---|---|
| −1 | `JN addr` | S = −1 |
| 0 | `JZ addr` | S = 0 |
| +1 | `JP addr` | S = +1 |
| +2 | `JNZ addr` | S ≠ 0 |
| −2 | `JLE addr` | S ≠ +1 |
| +3 | `JGE addr` | S ≠ −1 |
| other | — | **CPU fault** (halt with fault code; debugger shows it) |

### 3.9 Execution loop
1. Fetch the word at PC. Decode it, and read any extra words, advancing PC past the whole instruction.
2. Execute. Every instruction costs **1 cycle**, whatever its length.
3. Faults (invalid JIF condition) halt the CPU and record the fault reason and PC. The recorded PC is the address of the faulting instruction, and the PC register is left pointing at it. Fault codes: 1 = invalid JIF condition (D-010).

### 3.10 Idioms worth documenting
- Indirect jump: `PUSH Rn` then `RET` jumps to the address held in Rn.
- Clear a register: `LDI R0, 0` or `SUB R0, R0`.
- Test a register: `MOV R0, R0` sets S from R0.

---

## 4. Video chip

### 4.1 Screen
**216 × 144 pixels.** That's 27 × 18 tiles of 8 × 8 pixels, with a border area around it in the BORDER color. The border is 12 pixels wide on every side, so the whole picture is 240 × 168 pixels (D-020). The frontend scales by integer factors.

### 4.2 Colors: ternary RGB, 27 colors
A pixel value is a 3-trit number from −13 to +13. Its trits **are** its color: high trit = Red, middle = Green, low = Blue. Each channel trit maps to an intensity: −1 → 0, 0 → 128, +1 → 255.

| Value | Trits | RGB | Name |
|---|---|---|---|
| −13 | `---` | 0,0,0 | black |
| 13 | `+++` | 255,255,255 | white |
| 0 | `000` | 128,128,128 | gray (**transparent in sprites**) |
| 5 | `+--` | 255,0,0 | red |
| −7 | `-+-` | 0,255,0 | green |
| −11 | `--+` | 0,0,255 | blue |
| 11 | `++-` | 255,255,0 | yellow |

### 4.3 Pixel packing
One word holds **4 pixels**: pixel 0 (leftmost) in t11–t9, pixel 1 in t8–t6, pixel 2 in t5–t3 and pixel 3 in t2–t0.
`word = p0·19683 + p1·729 + p2·27 + p3`.

### 4.4 Tiles
243 tiles, numbered 0…242. Tile `t` occupies words `40,000 + 16t … 40,000 + 16t + 15`. Row `r` (0–7) is two words: `40,000 + 16t + 2r` (pixels 0–3) and `+1` (pixels 4–7).

### 4.5 Tile map
Cell (col, row) with col 0–26 and row 0–17 is at `44,000 + row·27 + col`. Its value is a tile index. **A negative value −t means tile t mirrored horizontally.** A value with |v| > 242 is drawn as tile 0. Background tiles draw every pixel; gray is not transparent there.

### 4.6 Scrolling
Screen pixel (x, y) shows background pixel ((x + SCROLL_X) mod 216, (y + SCROLL_Y) mod 144), using positive mod. The background wraps.

### 4.7 Sprites
There are 27 sprites. Sprite `i` is 4 words at `44,600 + 4i`:

| Offset | Field | Meaning |
|---|---|---|
| 0 | X | screen x of top-left (may be negative/off-screen) |
| 1 | Y | screen y of top-left |
| 2 | TILE | tile index; negative = mirrored horizontally |
| 3 | FLAGS | 0 = hidden, +1 = visible, −1 = visible and flipped vertically |

Sprites are drawn after the background, in order 0 → 26, so higher numbers end up on top. Pixel value 0 is transparent. They're clipped to the screen. Sprites are not affected by scrolling. FLAGS values other than +1 and −1 mean hidden, and a TILE with |v| > 242 draws tile 0, as in the tile map (D-021).

### 4.8 Characters
The ROM copies an 8×8 font into tiles **32–126** at boot, so tile index = ASCII code for printable characters. Text is just tiles in the tile map.

---

## 5. I/O registers

Register `k` lives at address `IO_BASE + k`, where **IO_BASE = −20,412**. All of them fit within ±121 of IO_BASE, so `LDI R1, -20412` followed by `LD R0, [R1+k]` reaches any of them.

| k | Address | Name | R/W | Meaning |
|---|---|---|---|---|
| 0 | −20,412 | FRAME | R | Frame counter (wraps like any word) |
| 1 | −20,411 | JOY_X | R | −1 left, 0, +1 right |
| 2 | −20,410 | JOY_Y | R | −1 up, 0, +1 down |
| 3 | −20,409 | BTN_A | R | 0 / 1 |
| 4 | −20,408 | BTN_B | R | 0 / 1 |
| 5 | −20,407 | BTN_C | R | 0 / 1 |
| 6 | −20,406 | KEY | R | Next key from the keyboard queue (ASCII), or 0. **Reading removes it**. Enter = 10, Backspace = 8, Tab = 9, Escape = 27 (D-024) |
| 7 | −20,405 | SCROLL_X | R/W | Background scroll x |
| 8 | −20,404 | SCROLL_Y | R/W | Background scroll y |
| 9 | −20,403 | BORDER | R/W | Border color (pixel value; only the lowest 3 trits are used, D-021) |
| 10–12 | −20,402…−20,400 | V1_FREQ, V1_VOL, V1_WAVE | R/W | Sound voice 1 (§7) |
| 13–15 | −20,399…−20,397 | V2_… | R/W | Voice 2 |
| 16–18 | −20,396…−20,394 | V3_… | R/W | Voice 3 |
| 19 | −20,393 | RAND | R | Random word |
| 20 | −20,392 | DEBUG_CHAR | W | Host prints the value as a character (development aid) |
| 21 | −20,391 | DEBUG_NUM | W | Host prints the value as a decimal number plus newline |
| 22 | −20,390 | HOST_EXIT | W | Headless runner stops with this exit code (for tests) |
| 23 | −20,389 | TS_CMD | W | Triskette: writing starts a command (§5.1) |
| 24 | −20,388 | TS_NAME | R/W | Triskette: RAM address of the file name |
| 25 | −20,387 | TS_ADDR | R/W | Triskette: RAM address of the data |
| 26 | −20,386 | TS_LEN | R/W | Triskette: length in words |
| 27 | −20,385 | TS_STATUS | R | Triskette: result of the last command |
| 28–728 | | reserved | | Read 0, writes ignored |

Debug port details (D-018): DEBUG_CHAR prints printable ASCII (32–126), newline, carriage return and tab as themselves, and any other value as its number in angle brackets, e.g. `<0>`. Writing HOST_EXIT ends the CPU's current frame immediately, so no instruction runs after it.

### 5.1 Triskette storage device (v1.1, M13)
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
- **Host format:** every file is stored in the existing `.tri` container (§9.5: header, load address, words). For SAVE, the load address is TS_ADDR.
- Writes outside RAM are silently clipped, and the status is −4.

Details settled while building the device (D-045):
- A name that has a character outside 32–126, or no 0 within its first 13 words, is a bad name (−3).
- SAVE only saves a whole block of RAM: if TS_LEN is negative or the block doesn't lie entirely inside RAM, nothing is saved and the status is −4. TS_LEN = 0 saves an empty file.
- LOAD and LOAD-HOME set TS_LEN to the file's length even when some words were clipped.
- DIR lists the names in alphabetical order. If they don't fit in TS_LEN words, nothing is written, the status is −2 and TS_LEN is set to the number of words needed.
- Any other TS_CMD value does nothing but set the status to −4.
- Power-on clears the five registers; the files on the triskette stay.

---

## 6. Timing
- **60 frames per second.**
- Each frame the CPU gets **59,049 cycles** (3^10, about 3.5 million instructions per second).
- `WAIT` ends the CPU's work for the current frame. The CPU continues with the next instruction at the start of the next frame.
- After the CPU's budget for the frame, the video chip draws the frame and FRAME increases by 1.
- A halted CPU does nothing, but video and sound keep running.

---

## 7. Sound chip
There are three voices, mixed together.
- **FREQ:** frequency in Hz (0 = silent).
- **VOL:** 0…13 (values outside are clamped).
- **WAVE:** −1 = noise, 0 = triangle, +1 = **ternary square**. The ternary square is a three-level wave that steps +1, 0, −1, 0 over each period, in four equal quarters. It's the machine's signature sound.

Details (D-023): output is 44,100 samples per second, 16-bit mono, 735 per frame. One voice at volume 13 peaks at ±10,000, and three voices together stay within ±30,000. The triangle goes 0 → +1 → 0 → −1 → 0 over a period. Noise holds a random trit (−1, 0, +1) for each quarter period, so FREQ sets how grainy it is. FREQ ≤ 0 is silent, and WAVE uses only its lowest trit. The chip reads its registers once per frame, after the CPU's turn.

---

## 8. ROM (kernel and monitor)
The ROM is written **in TRIADA assembly**. It's built by our own assembler and embedded into the emulator.

### 8.1 Jump table
The jump table sits at the start of ROM, and each entry is a 2-word `JMP routine`. Programs call these addresses with `JSR`, so they never change.

| Address | Name | In | Out |
|---|---|---|---|
| −19,683 | RESET | — | (boot entry; PC starts here) |
| −19,681 | PUTCHAR | R0 = char | prints at cursor, advances, scrolls |
| −19,679 | PRINT | R0 = address of string | — |
| −19,677 | PRINTNUM | R0 = number | prints decimal |
| −19,675 | CLS | — | clears screen, cursor home |
| −19,673 | GETKEY | — | R0 = key (waits) |
| −19,671 | LOCATE | R0 = col, R1 = row | moves cursor |
| −19,669 | DISKSAVE | see §8.5 | (v1.1, M13) |
| −19,667 | DISKLOAD | see §8.5 | (v1.1, M13) |
| −19,665 | DISKDIR | see §8.5 | (v1.1, M13) |
| −19,663 | DISKDEL | see §8.5 | (v1.1, M13) |
| −19,661 … | reserved | | |

**Calling convention:** arguments in R0–R2, result in R0. Routines may change R0–R2 and must preserve R3–R8. A string is one character per word, ending with a 0 word.

### 8.2 Boot sequence
1. Set SP = 59,049.
2. Load the font into tiles 32–126.
3. Clear the screen and play a short three-voice boot chime.
4. Print the boot screen:
   ```
   TRIADA-85 NPO "TRIADA" 1985
   39757 WORDS FREE
   READY.
   >
   ```
5. Enter the monitor. From v1.1 (M14) the machine enters TRIBASIC instead (§10.8); `MON` reaches the monitor. (If the host has put a program's address into the system variable AUTORUN, the ROM first clears it and runs that program as if `RUN` had been typed; D-028.)

The first line has single spaces so that it is exactly 27 characters, one full screen line (D-027).

### 8.3 Monitor commands
Numbers can be decimal (`123`, `-5`) or ternary (`0t+-0`).

| Command | Effect |
|---|---|
| `HELP` | List commands |
| `PEEK addr [count]` | Show memory words (decimal and ternary) |
| `POKE addr value` | Write a word |
| `RUN [addr]` | `JSR` to addr (default 243). When the program `RET`s, back to the monitor |
| `CLS` | Clear screen |
| `TERN n` | Show n in balanced ternary (fun and educational) |
| `BASIC` | Return to TRIBASIC through `BASIC_WARM`, keeping the program (v1.1, M14) |

Unknown command → `?SYNTAX ERROR`.

### 8.4 Details settled while building the ROM
These follow from D-026 … D-028 in `docs/DECISIONS.md`.
- **Font:** font8x8 (public domain), light cyan (4) on dark blue (−12); blue border (−11).
- **System variables:** AUTORUN = 0, CURSOR_X = 1, CURSOR_Y = 2, RUN_DONE = 3 (programs finished by RUN/AUTORUN), LINEBUF = 162 … 242. Since M14, TRIBASIC's state is at 4 … 37 (`rom/sysvars.tasm`).
- **PUTCHAR:** 10 = newline, 8 = backspace. A full line wraps only when the next character arrives ("pending wrap"). The screen scrolls at the bottom. LOCATE clamps to the screen.
- **Monitor:** prompt `> `; at most 24 characters per line; letters made upper case; blinking `_` cursor. `PEEK` prints `ADDR: VALUE 0tTRITS` per word, `TERN` prints `N = 0tTRITS`, and trits are shown without leading zeros. Missing or extra arguments, counts below 1 and out-of-range numbers are `?SYNTAX ERROR`.

### 8.5 Triskette routines (v1.1, M13)
`DISKSAVE`, `DISKLOAD`, `DISKDIR` and `DISKDEL` drive the triskette device (§5.1) for programs that don't want to touch its registers. They follow the calling convention of §8.1: arguments in R0–R2, R3–R8 kept. Each returns the status in R0 (§5.1), and S is set from it, so `JNZ` right after the `JSR` catches any error.

| Routine | In | Out |
|---|---|---|
| `DISKSAVE` | R0 = name, R1 = address, R2 = length | R0 = status |
| `DISKLOAD` | R0 = name, R1 = address, or any negative number for "the file's own address" (LOAD-HOME) | R0 = status, R1 = address loaded to, R2 = length |
| `DISKDIR` | R0 = buffer, R1 = room in words | R0 = status, R1 = words used (or needed, with −2) |
| `DISKDEL` | R0 = name | R0 = status |

A name is a string (one character per word, ending with 0). Negative addresses are never RAM, which is why a negative R1 can mean "home".

---

## 9. Assembler (`tasm`)

### 9.1 Syntax
```
; comment
NAME = 59049              ; constant
        .org 243
start:  LDI R0, msg       ; label as immediate
        JSR PRINT
loop:   WAIT
        JMP loop
msg:    .str "HELLO, WORLD"
```
- One instruction per line. Labels end with `:`. Mnemonics and register names are case-insensitive. Labels are case-sensitive.
- Numbers: decimal `123` / `-7`, ternary `0t+-0`, character `'A'`.
- Expressions: numbers, labels and constants combined with `+` and `-`.
- Memory operands: `[R2]`, `[R2+5]`, `[R2-3]`.

### 9.2 Directives

| Directive | Meaning |
|---|---|
| `.org addr` | Set the current address |
| `.word a, b, …` | Raw words |
| `.str "text"` | One character per word, plus a terminating 0 |
| `.fill count, value` | Repeat a word |
| `.pix p0, p1, p2, p3` | One word made of 4 pixel values (§4.3) |
| `.include "file"` | Include another source file |

### 9.3 Built-in symbols and pseudo-instructions
- All ROM routine names (`PUTCHAR`, `PRINT`, …), `IO_BASE`, all I/O register names as offsets (`FRAME = 0`, `JOY_X = 1`, …), `TILES = 40000`, `MAP = 44000`, `SPRITES = 44600`. Since M16 also `ROM_START = -19683`, `EXT_ROM_START = -59048`, `PROGRAM_START = 243`, `STACK_TOP = 59049`, `MAP_COLS = 27`, `MAP_ROWS = 18`, `MAP_CELLS = 486` (D-058). All these values come from the machine profile the source is assembled for (`tasm --model`).
- `JN JZ JP JNZ JLE JGE addr` → `JIF`.
- `INC rd` → `ADDI rd, 1`. `DEC rd` → `ADDI rd, -1`.
- `ROT rd` → `ROT rd, 1`.
- Range errors (for example an imm5 outside ±121) are **errors, not silent truncation**.

### 9.4 Errors
Every error states the file, the line, the column, what was expected and what was found.

### 9.5 Output: `.tri` file
```
bytes 0–7 : ASCII "TRIADA85"
int32 LE  : load address
int32 LE  : word count N
N × int32 LE : word values
```
The assembler can also write a **listing** (`.lst`) showing address, words in ternary, words in decimal and the source line.

### 9.6 Details settled while building the assembler
These follow from D-012 and D-013 in `docs/DECISIONS.md`.
- Without `.org`, code starts at 243. A `.tri` image is one block from the lowest to the highest address used; gaps are 0, and two lines producing the same address is an error.
- `SP` is another name for `R8`. Register names (`R0`–`R8`, `SP`) can't be labels or constants.
- Character literals and strings may contain printable ASCII (32–126) and the escapes `\n \t \0 \\ \' \"`.
- Unary `+` and `-` are allowed in expressions. In `[R1-3+1]` the sign belongs to the offset expression.
- Labels and constants may be used before they are defined, except in `.org` and the count of `.fill`.
- A label on an `.org` line gets the new address. `.include` paths are relative to the including file.
- `JIF` with a condition outside −2 … 3 is an assembly error.

---

## 10. TRIBASIC (v1.1, M14–M15)
TRIBASIC is the TRIADA-85's built-in programming language, an interpreter written in TRIADA assembly in the extension ROM (§2.5).

### 10.1 Program lines and editing
- A line starting with a number from 1 to 65,535 is stored in the program and replaces any line with that number. A number on its own deletes the line.
- A line without a number runs immediately (direct mode).
- Several statements go on one line, separated by `:`.
- Keywords are case-insensitive and stored as tokens. `LIST` prints them back in upper case.
- `LIST`, `LIST 100`, `LIST 100-200`, `LIST -200`, `LIST 100-`.
- `RUN [line]` clears variables, then runs. `NEW` clears program and variables.

### 10.2 Values, variables and expressions
**Values:** whole numbers (machine words, ±265,720) and strings (§10.4).

**Literals:**
- decimal: `42`, `-7`
- ternary: `0t+-0`
- strings: `"HELLO"`
- truth constants: `TRUE` (= +1), `FALSE` (= −1), `UNKNOWN` (= 0)

**Variable names:** a letter followed by up to 7 letters or digits, all significant. A `$` suffix means a string variable. Keywords can't be variable names. **A variable that was never assigned is 0, so it reads as `UNKNOWN`.** The manual mentions this.

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
- `/` uses the machine's division, which **rounds to the nearest integer** (ties toward zero, §3.5): `7/2 = 3`, `8/3 = 3`. `MOD` matches it, so `8 MOD 3 = -1`. The manual explains why this is natural in ternary.
- `+` and `-` raise `?OVERFLOW` when the O flag is set. `*` may wrap without an error; this is a documented limitation, unless a cheap check is found (logged in DECISIONS).
- Division by zero raises `?DIVISION BY ZERO`.

### 10.3 Three-valued logic (TRIBASIC's signature feature)
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
- It is implemented with the CPU's `BR3`; the M14 document shows the machine code.

Example:
```
10 ASK "IS IT RAINING"; R
20 IF R THEN PRINT "TAKE AN UMBRELLA" MAYBE PRINT "TAKE IT, JUST IN CASE" ELSE PRINT "ENJOY THE SUN"
```

**`ASK "prompt"; var`** prints the prompt with `(Y/N/?)` and waits for a key: `Y` → TRUE, `N` → FALSE, `?` → UNKNOWN. Other keys are ignored.

### 10.4 Strings
- At most 80 characters each. Assigning a longer string raises `?STRING TOO LONG`.
- `+` concatenates. `=`, `<>` and `<=>` compare. Mixing strings and numbers raises `?TYPE MISMATCH`.
- Functions: `LEN(s$)`, `LEFT$(s$,n)`, `RIGHT$(s$,n)`, `MID$(s$,start,n)` (1-based), `CHR$(n)`, `ASC(s$)`, `STR$(n)`, `VAL(s$)`, **`TERN$(n)`** (the balanced-ternary text of n, e.g. `TERN$(6) = "+-0"`).
- Strings are stored one character per word. Every string variable and string array element owns a fixed 81-word slot (its length and 80 characters), so no garbage collection is needed (M15, D-051).

### 10.5 Arrays
- `DIM A(n)` gives indices 0 … n. **`DIM A(-n TO n)`** gives a balanced array with a centre element at 0, in the spirit of the machine. `DIM A$(n)` also works.
- One dimension only. Using an array without DIM auto-dimensions it as 0 … 10. An out-of-range index raises `?BAD SUBSCRIPT`.

### 10.6 Statements and functions

#### 10.6.1 Core statements
`PRINT` (`;` joins items, `,` moves to the next 9-column zone, and a trailing `;` suppresses the newline), `INPUT ["prompt";] var`, `ASK "prompt"; var`, `LET`, `GOTO`, `GOSUB`, `RETURN`, `FOR v = a TO b [STEP s]`, `NEXT [v]`, `IF`, `REM`, `END`, `STOP`, `DIM`, `CLS`, `LOCATE col,row`.

#### 10.6.2 Machine statements

| Statement | Effect |
|---|---|
| `TILE col,row,t` | Set a tile map cell (negative t = mirrored) |
| `DEFTILE t,row,p0,…,p7` | Set one row of tile t to 8 pixel values (−13…13) |
| `SPRITE i,x,y,t[,flags]` | Set sprite i (flags default +1) |
| `SOUND v,freq,vol,wave` | Set voice v (1–3) |
| `BORDER c` | Border color |
| `SCROLL x,y` | Background scroll |
| `WAIT n` | Wait n frames |
| `COLOR fg,bg` | Re-color the font tiles 32–126: every pixel of the old foreground colour becomes fg, every other pixel bg (M15, D-052) |
| `POKE addr,value` | Write a memory word |
| `SYS addr` | Call machine code (`JSR`); it returns with `RET` |

#### 10.6.3 Functions
`ABS(n)`, `SGN(n)` (returns −1, 0 or +1), `RND(n)` (random 1 … n), `PEEK(addr)`, `JOYX`, `JOYY`, `BTN(n)` (n = 1, 2, 3 for buttons A, B, C), `INKEY` (key code, or 0), `FRAME`, plus the string functions from §10.4.

#### 10.6.4 Storage commands

| Command | Effect |
|---|---|
| `SAVE "NAME"` | Saves the program as `NAME.TB` |
| `LOAD "NAME"` | Loads `NAME.TB` (replaces the program) |
| `FILES` | Lists the triskette |
| `ERASE "NAME.EXT"` | Deletes a file |
| `BSAVE "NAME",addr,len` | Saves raw words as `NAME.TRI` |
| `BLOAD "NAME"[,addr]` | Loads `NAME.TRI` (to its own address by default) |
| `BRUN "NAME"` | `BLOAD`, then `SYS` to its load address. Existing games run with this |

### 10.7 Errors
Format: `?<MESSAGE> ERROR IN <line>` (no `IN …` in direct mode), then `READY.`

`SYNTAX`, `UNDEFINED LINE`, `RETURN WITHOUT GOSUB`, `NEXT WITHOUT FOR`, `OUT OF MEMORY`, `DIVISION BY ZERO`, `OVERFLOW`, `TYPE MISMATCH`, `BAD SUBSCRIPT`, `STRING TOO LONG`, `FILE NOT FOUND`, `DISK`.

Esc while running prints `BREAK IN <line>`. The frontends map the Esc key to KEY code 27: D-024 gave Escape the code, and since M14 `InputMapper` sends it when the Esc key goes down (the libGDX backends make no typed character for Esc).

### 10.8 Boot
The machine boots straight into TRIBASIC, as most 1980s home computers did:
```
TRIADA-85 NPO "TRIADA" 1985
TRIBASIC V1.0
39757 WORDS FREE
READY.
```
The first line keeps the released single spacing, exactly 27 characters (D-027, D-044). `MON` switches to the machine-code monitor. The monitor's `BASIC` command returns to TRIBASIC through `BASIC_WARM`, with the program kept. A program the host starts through AUTORUN returns to TRIBASIC too, with an empty program (it may have used the program area).

### 10.9 Details settled while building TRIBASIC (M14, D-048)
- **Memory:** each program line is a record `[length][line number][text][0]` from 243 up, in line-number order, ended by a single 0 word. Variables follow the program, 9 words each (8 name characters, 0-padded, and the value). The FOR/GOSUB control stack grows up from 45,000 (limit 49,990), and INPUT reads into 50,000 … 50,080. "WORDS FREE" at boot is 39,757; the end marker isn't counted.
- **Tokens:** every keyword of §10 (68 of them, including those only M15 uses) is one negative word, −1 for PRINT … −68 for TERN$, in the order of the M14 milestone document's table. A keyword is a whole word: `FORM` is a variable, not `FOR M`, so keywords must be separated from names and numbers by a space or another sign. Text in quotes and after `REM` is stored as typed; everything else in upper case. `0t` is stored as `0T`.
- **Lines:** up to 80 characters; Backspace works across screen rows. Editing a line clears the variables. `BASIC_WARM` checks that RAM from 243 still holds a valid program and does `NEW` if not.
- **READY.** always starts on a new line. A direct-mode error has no `IN …`; Esc before a direct line has run prints `BREAK` without a line number.
- **Keys** that arrive while a program runs (Esc is checked before every statement) wait in a buffer of 16 for INPUT and ASK.
- **FOR:** the body always runs at least once (as on the home computers of 1985); `NEXT` then adds the step and compares. A new `FOR` over the same variable ends the old loop and every loop inside it; `RETURN` ends loops started inside the subroutine. `NEXT` past the word's range ends the loop.
- **IF:** a nested `IF` owns the rest of its line, so `MAYBE`/`ELSE` after it belong to it.
- **Overflow:** no instruction can branch on the O flag, so `+` and `-` compare before adding (§10.2's rule, same result).
- **INPUT** accepts a whole number with an optional sign, in decimal or `0t` trits; anything else prints `?REDO` and asks again. **ASK** echoes the key (Y, N or ?).
- `CONT` is not implemented (it is optional, CLAUDE.md M14) and gives `?SYNTAX ERROR`.

### 10.10 Details settled while finishing TRIBASIC (M15, D-051 … D-054)
- **Memory:** every variable is an entry `[length][name: 10 words][data]`; a number is 1 word, a string an 81-word slot, an array `[low][high]` and its elements. Arrays are told apart from plain variables by a `(` after the name, so `A` and `A()` are different. String values in the middle of a statement live in temporary slots at 50,100 … 57,899, all freed when the next statement starts. File names are built at 50,082.
- **Strings:** a name is a letter, up to 7 more letters or digits, and `$`. `MID$`, `LEFT$` and `RIGHT$` clamp their numbers to the string, so they never fail. `ASC("")` is 0. `VAL` reads spaces, an optional `-`, and a decimal or `0T` number at the start of the text (0 if there is none). `STR$` and `TERN$` have no leading space. INPUT into a string variable takes the line as typed.
- **Arrays:** `DIM` of an array that already exists is `?SYNTAX ERROR`. `DIM A(n)` and `DIM A(low TO high)`, several separated by commas. An array used before `DIM` gets 0 … 10.
- **Functions:** `RND(n)` is 0 for n < 1; `BTN(n)` is 0 for n other than 1, 2, 3. `JOYX`, `JOYY`, `INKEY` and `FRAME` take no parentheses; `INKEY` takes keys typed earlier first.
- **Machine statements:** `TILE`, `SPRITE` and `SOUND` with a column, row, sprite or voice outside the screen or the chip do nothing. `DEFTILE` takes each pixel value MOD 27, which is −13 … 13 exactly; a tile or row outside 0 … 242, 0 … 7 does nothing. `COLOR` takes its colours MOD 27 too; if the font has a single colour (after `COLOR c, c`), it is first drawn afresh. `WAIT` checks Esc every frame. `SYS` keeps TRIBASIC's place (R7) on the stack.
- **Storage:** a name without a `.` gets `.TB` (`SAVE`, `LOAD`) or `.TRI` (`BSAVE`, `BLOAD`, `BRUN`); `ERASE` takes the name as it is. Status −1 is `?FILE NOT FOUND`, any other failure `?DISK`. `SAVE` writes 243 up to and including the end marker, loaded back to 243; `LOAD` checks the records and does `NEW` plus `?DISK ERROR` if they aren't a program, and ends a running program. If `BLOAD` or `BRUN` load over the program or its variables, TRIBASIC does `NEW` and says `READY.`. `FILES` lists one name per line.
- **Keys:** while B_KEYS (16 keys) is full, further keys stay in the keyboard queue, so typing ahead and paste-typing lose nothing.
