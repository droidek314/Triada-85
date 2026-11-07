# TRIADA-85 — Machine Specification

Version 1.0. This document is the **source of truth** for the machine. If code and spec disagree, the code is wrong — or the spec is changed on purpose and the change is logged in `docs/DECISIONS.md`.

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
| −265,720 … −20,413 | — | Unmapped (reads 0, writes ignored) |
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
**216 × 144 pixels.** That's 27 × 18 tiles of 8 × 8 pixels, with a border area around it in the BORDER color. The frontend scales by integer factors.

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

Sprites are drawn after the background, in order 0 → 26, so higher numbers end up on top. Pixel value 0 is transparent. They're clipped to the screen.

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
| 6 | −20,406 | KEY | R | Next key from the keyboard queue (ASCII), or 0. **Reading removes it** |
| 7 | −20,405 | SCROLL_X | R/W | Background scroll x |
| 8 | −20,404 | SCROLL_Y | R/W | Background scroll y |
| 9 | −20,403 | BORDER | R/W | Border color (pixel value) |
| 10–12 | −20,402…−20,400 | V1_FREQ, V1_VOL, V1_WAVE | R/W | Sound voice 1 (§7) |
| 13–15 | −20,399…−20,397 | V2_… | R/W | Voice 2 |
| 16–18 | −20,396…−20,394 | V3_… | R/W | Voice 3 |
| 19 | −20,393 | RAND | R | Random word |
| 20 | −20,392 | DEBUG_CHAR | W | Host prints the value as a character (development aid) |
| 21 | −20,391 | DEBUG_NUM | W | Host prints the value as a decimal number plus newline |
| 22 | −20,390 | HOST_EXIT | W | Headless runner stops with this exit code (for tests) |
| 23–728 | | reserved | | Read 0, writes ignored |

Debug port details (D-018): DEBUG_CHAR prints printable ASCII (32–126), newline, carriage return and tab as themselves, and any other value as its number in angle brackets, e.g. `<0>`. Writing HOST_EXIT ends the CPU's current frame immediately, so no instruction runs after it.

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
| −19,669 … | reserved | | |

**Calling convention:** arguments in R0–R2, result in R0. Routines may change R0–R2 and must preserve R3–R8. A string is one character per word, ending with a 0 word.

### 8.2 Boot sequence
1. Set SP = 59,049.
2. Load the font into tiles 32–126.
3. Clear the screen and play a short three-voice boot chime.
4. Print the boot screen:
   ```
   TRIADA-85   NPO "TRIADA" 1985
   39757 WORDS FREE
   READY.
   >
   ```
5. Enter the monitor.

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

Unknown command → `?SYNTAX ERROR`.

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
- All ROM routine names (`PUTCHAR`, `PRINT`, …), `IO_BASE`, all I/O register names as offsets (`FRAME = 0`, `JOY_X = 1`, …), `TILES = 40000`, `MAP = 44000`, `SPRITES = 44600`.
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
