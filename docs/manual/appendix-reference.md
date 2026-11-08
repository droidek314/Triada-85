# Appendix — Quick reference

*Cut out this page and keep it beside your computer.*

## The machine at a glance

| | |
|---|---|
| Processor | 12-trit words, 9 registers (R0–R8, R8 = stack pointer), 27 instructions, about 3.5 million instructions per second |
| Memory | 59,049 words of RAM, 19,683 words of ROM, 39,757 words free for programs |
| Screen | 216 × 144 dots, 27 × 18 squares of 8 × 8, 243 tiles, 27 sprites, 27 colours, scrolling |
| Sound | 3 voices; noise, triangle and ternary square waves; volume 0–13 |
| Input | Keyboard, joystick (3 × 3 positions), fire buttons A, B, C |

## The memory street

```
 -265,720 ... -20,413   (nothing: reads 0)
  -20,412 ... -19,684   connections: picture, sound, joystick, keyboard   (I/O)
  -19,683 ...      -1   ROM: the monitor and its routines (read-only)
        0 ...     242   the monitor's own notes
      243 ...  39,999   YOUR PROGRAMS start at 243
   40,000 ...  43,887   tiles (243 x 16 words)
   44,000 ...  44,485   tile map (27 x 18)
   44,600 ...  44,707   sprites (27 x 4 words: across, down, tile, switch)
   45,000 ...  59,048   free; the stack grows down from the top
   59,049 ... 265,720   (nothing: reserved for cartridges)
```

## The connections (I/O), house −20,412 + k

| k | Name | | k | Name | | k | Name |
|---|---|---|---|---|---|---|---|
| 0 | FRAME | | 7 | SCROLL_X | | 14 | V2_VOL |
| 1 | JOY_X | | 8 | SCROLL_Y | | 15 | V2_WAVE |
| 2 | JOY_Y | | 9 | BORDER | | 16 | V3_FREQ |
| 3 | BTN_A | | 10 | V1_FREQ | | 17 | V3_VOL |
| 4 | BTN_B | | 11 | V1_VOL | | 18 | V3_WAVE |
| 5 | BTN_C | | 12 | V1_WAVE | | 19 | RAND |
| 6 | KEY | | 13 | V2_FREQ | | | |

## ROM routines (call with JSR; input R0–R2, answer R0, R3–R8 kept)

| Address | Name | | Address | Name |
|---|---|---|---|---|
| −19,681 | PUTCHAR (R0 = letter) | | −19,675 | CLS |
| −19,679 | PRINT (R0 = address of text) | | −19,673 | GETKEY (R0 = key) |
| −19,677 | PRINTNUM (R0 = number) | | −19,671 | LOCATE (R0 = column, R1 = line) |

## Instructions and their operation codes

| Code | | Code | | Code | |
|---|---|---|---|---|---|
| 0 | `HLT` | 9 | `SUB rd, rs` | −5 | `ROT rd, n` |
| 1 | `NOP` | 10 | `ADDI rd, n` | −6 | `SHT rd, n` |
| 2 | `MOV rd, rs` | 11 | `MUL rd, rs` | −7 | `CMP rd, rs` |
| 3 | `LDI rd, value` | 12 | `DIV rd, rs` | −8 | `JMP addr` |
| 4 | `LD rd, [rs+n]` | 13 | `MOD rd, rs` | −9 | `JSR addr` |
| 5 | `ST rd, [rs+n]` | −1 | `NEG rd` | −10 | `RET` |
| 6 | `PUSH rd` | −2 | `MIN rd, rs` | −11 | `JIF cond, addr` |
| 7 | `POP rd` | −3 | `MAX rd, rs` | −12 | `BR3 aN, aZ, aP` |
| 8 | `ADD rd, rs` | −4 | `TMUL rd, rs` | −13 | `WAIT` |

Jump conditions for `JIF`: −1 `JN`, 0 `JZ`, +1 `JP`, +2 `JNZ`, −2 `JLE`, +3 `JGE`. Any other condition stops the processor with a fault.

## Colours (red, green, blue trits)

| Value | Trits | Colour | | Value | Trits | Colour | | Value | Trits | Colour |
|---|---|---|---|---|---|---|---|---|---|---|
| −13 | `---` | black | | −4 | `0--` | dark red | | 5 | `+--` | red |
| −12 | `--0` | dark blue | | −3 | `0-0` | purple | | 6 | `+-0` | rose |
| −11 | `--+` | blue | | −2 | `0-+` | violet | | 7 | `+-+` | magenta |
| −10 | `-0-` | dark green | | −1 | `00-` | olive | | 8 | `+0-` | orange |
| −9 | `-00` | teal | | 0 | `000` | grey | | 9 | `+00` | salmon |
| −8 | `-0+` | azure | | 1 | `00+` | lavender | | 10 | `+0+` | pink |
| −7 | `-+-` | green | | 2 | `0+-` | chartreuse | | 11 | `++-` | yellow |
| −6 | `-+0` | spring green | | 3 | `0+0` | light green | | 12 | `++0` | light yellow |
| −5 | `-++` | cyan | | 4 | `0++` | light cyan | | 13 | `+++` | white |

Four dots of colour c: the word c × 20,440. One tile line of four dots p0 p1 p2 p3: p0 × 19,683 + p1 × 729 + p2 × 27 + p3.

## Monitor commands

`HELP` · `TERN n` · `PEEK addr [count]` · `POKE addr value` · `RUN [addr]` · `CLS` — numbers as `123`, `-5` or `0t+-0`. Anything else: `?SYNTAX ERROR`.

---
*TRIADA-85 Owner's Manual · Appendix*
