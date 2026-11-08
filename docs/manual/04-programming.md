# Chapter 4 — Programming in assembly language

## 4.1 How your computer thinks

Inside your TRIADA-85 is the **processor**, which does the actual work. It is very fast: it carries out about 3½ million **instructions** every second. But each instruction is very simple: "add these two numbers", "fetch the word from house 300", "if the result was negative, continue over there". A **program** is a list of such instructions, stored in the memory one after another, starting at house 243.

The processor keeps the numbers it is working on in nine **registers**, named R0 to R8. A register is like a small notepad on the processor's desk: much faster to use than walking to a house in the memory street. A tenth register, the **program counter**, always points at the next instruction.

Every instruction is itself a word. Its first three trits say *what* to do (the **operation code**). Three trits give 27 operation codes, and your TRIADA-85 uses every one of them.

```
   trits  11 10  9 |  8  7 |  6  5 |  4  3  2  1  0
          operation | reg. A| reg. B|  a small number
```

## 4.2 Assembly language

Writing instructions as numbers is hard, so programmers write them as short words, and a program called an **assembler** turns the words into numbers. The TRIADA assembler, **TASM**, is on the Developer triskette (sold separately). The listing in Chapter 7 shows how a program looks in assembly language:

```
        LDI R0, 65         ; put the number 65 into register R0
loop:   ADD R0, R1         ; add R1 to R0
        JMP loop           ; continue at the label "loop"
```

Everything after `;` is a comment for people; the computer ignores it. A word ending in `:` is a **label**, a name for an address.

## 4.3 Your first program, typed in with POKE

You do not need TASM to program. This little program prints `HI!`. In assembly language it reads:

```
        .org 243           ; start at house 243
        LDI R0, msg        ; R0 = the address of the message
        JSR PRINT          ; call the ROM's PRINT routine
        RET                ; back to the monitor
msg:    .str "HI!\n"       ; the letters H, I, ! and a new line, then 0
```

TASM turns it into ten words. Type them in with POKE, then RUN it:

```monitor
> POKE 243 50301
> POKE 244 248
> POKE 245 -177147
> POKE 246 -19679
> POKE 247 -196830
> POKE 248 72
> POKE 249 73
> POKE 250 33
> POKE 251 10
> POKE 252 0
> RUN
HI!
READY.
```

Look at the numbers: 72, 73 and 33 are the letters H, I and !, and 248 is the house where the message starts. −19679 is the address of PRINT in the ROM. Change house 249 to 79 (an O) and RUN again!

## 4.4 The instructions

Here are all 27 instructions. "rd" and "rs" are registers, "n" is a small number from −121 to 121, and "addr" is an address.

| Instruction | What it does |
|---|---|
| `HLT` | Stop the processor. |
| `NOP` | Do nothing. |
| `MOV rd, rs` | Copy rs into rd. |
| `LDI rd, value` | Put a value (any word) into rd. |
| `LD rd, [rs+n]` | Fetch the word from house rs + n into rd. |
| `ST rd, [rs+n]` | Store rd into house rs + n. |
| `PUSH rd`, `POP rd` | Put rd on the stack, take it off again (§4.6). |
| `ADD rd, rs`, `SUB rd, rs` | Add or subtract rs. |
| `ADDI rd, n` | Add a small number. `INC rd` and `DEC rd` add 1 and −1. |
| `MUL rd, rs` | Multiply. |
| `DIV rd, rs`, `MOD rd, rs` | Divide, rounding to the nearest number; the remainder. |
| `NEG rd` | Swap every `+` and `-`: make the number negative. |
| `MIN rd, rs`, `MAX rd, rs` | The smaller or larger trit, place by place: the ternary AND and OR. |
| `TMUL rd, rs` | Multiply trit by trit, place by place. |
| `ROT rd, n` | Turn every trit "up" (− → 0 → + → −) n times. |
| `SHT rd, n` | Shift the trits n places: multiply by 3, 9, 27 … or divide, rounding. |
| `CMP rd, rs` | Compare rd with rs (remembers only the sign of rd − rs). |
| `JMP addr` | Continue at addr. |
| `JSR addr`, `RET` | Call a subroutine at addr; return from it. |
| `JIF cond, addr` | Jump if the sign flag allows it (§4.5). |
| `BR3 a, b, c` | The ternary jump: to a if negative, b if zero, c if positive. |
| `WAIT` | Wait for the next picture on the screen (60 per second). |

`MIN`, `MAX`, `TMUL` and `ROT` work on all twelve trits of a word at once. Binary computers have nothing like them; Chapter 6 shows a game built on them.

## 4.5 Making decisions

After most calculations the processor remembers the **sign** of the result in its **sign flag**: −1, 0 or +1, one trit, of course. The jump instructions look at it:

| Instruction | Jumps when the last result was |
|---|---|
| `JN addr` | negative |
| `JZ addr` | zero |
| `JP addr` | positive |
| `JNZ addr` | not zero |
| `JLE addr` | negative or zero |
| `JGE addr` | zero or positive |
| `BR3 a, b, c` | all three at once! |

A second flag, the **overflow flag**, remembers the trit that "fell off" the end when an addition went past the end of a word.

## 4.6 The stack

The **stack** is a pile of words at the top of the memory street, kept by register R8 (also called SP, the stack pointer). `PUSH` puts a word on top, and `POP` takes the top word off. `JSR` puts the return address on the stack before it jumps, and `RET` takes it off again, so subroutines can call other subroutines as deep as you like.

## 4.7 The ROM routines

The ROM offers ready-made subroutines. Call them with `JSR`. They take their input in R0 to R2, give their answer in R0, and leave R3 to R8 untouched.

| Name | Address | What it does |
|---|---|---|
| `PUTCHAR` | −19,681 | Print the letter in R0. |
| `PRINT` | −19,679 | Print the text that starts at the address in R0 (ends with a 0 word). |
| `PRINTNUM` | −19,677 | Print the number in R0. |
| `CLS` | −19,675 | Clear the screen. |
| `GETKEY` | −19,673 | Wait for a key; R0 = its letter. |
| `LOCATE` | −19,671 | Move the cursor to column R0, line R1. |

## 4.8 The service panel

Service engineers and advanced programmers have a **service panel** (press F1) that stops the processor, shows all registers in ordinary numbers and in trits, and lets the program go one instruction at a time. It is a very good way to understand what your program does.

---
*TRIADA-85 Owner's Manual · Chapter 4*
