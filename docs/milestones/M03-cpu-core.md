# Milestone 03 — CPU core

## In one paragraph
The *CPU* (central processing unit, the processor) is the part of a computer that actually does things. It is a tireless clerk who reads one instruction from memory, carries it out, moves on to the next, and repeats this millions of times per second. This milestone builds TRIADA's clerk. It understands all 27 instructions of the machine, keeps two little notes about its last result (the *flags*), can jump around in the program depending on those notes, can call subroutines and come back, and stops cleanly when told to or when an instruction makes no sense. It also follows the machine's rhythm: 60 times a second it gets a fixed allowance of 59,049 steps, and a program can say "I'm done for this frame" to wait for the next one. On a normal PC our clerk works about 13 times faster than the real machine needs.

## What was built
- **`Instruction`**: the shape of every instruction: the 27 opcode numbers, the 6 jump conditions, packing and unpacking of the four fields of an instruction word, each instruction's name and length. The assembler and debugger will reuse it.
- **`Cpu`**: nine registers R0–R8, the program counter PC and flags S and O, with:
  - `Cpu.step()`, one instruction;
  - `Cpu.runFrame()`, one frame's budget;
  - `Cpu.reset()`, the power-on state.
- **`Fault`**: the list of errors that stop the CPU. So far there is one: an invalid JIF condition.
- **`Machine`**: the whole computer in one object (bus, CPU and devices) with `Machine.runFrame()` and `Machine.powerOn()`.
- **`SystemDevice`**: the FRAME counter and the RAND random-number register.
- **64 new tests**: one or more for every opcode, every JIF condition against every flag value, faults, BR3, WAIT and the cycle budget, wrap-around of PC, SP and addresses, an exhaustive encode/decode check, and a check that running frames allocates no memory.
- **A benchmark**: `./gradlew :triada-core:benchmark`.

## Why it was built this way

**An interpreter with one big `switch`.** The CPU reads an instruction word, splits it into its fields, and a `switch` on the opcode picks one of 27 small blocks of code. This is the simplest way to build an *emulator* (a program that imitates a different computer) and the easiest to read. Faster techniques exist, such as translating TRIADA programs into Java code on the fly, but the benchmark shows we don't need them.

**Decoding with the offset trick from M1.** Adding `Word.MAX` to a word adds 1 to every trit. That turns the instruction into an ordinary base-3 number, whose fields are read off with plain division and remainder. A nice bonus: the register field plus 4 is exactly the register number, so `R0`'s field −4 becomes index 0 with no extra work.

**All arithmetic goes through `WordMath`.** The CPU doesn't re-implement wrapping, rounding division or trit-wise operations. It calls the methods that M1 tested exhaustively. That keeps `Cpu.java` short, and every opcode's behaviour has exactly one implementation.

**Source registers are read first** ([D-009](../DECISIONS.md#d-009--source-registers-are-read-first-push-r8-and-pop-r8---milestone-m03-2026-11-07)). Read literally, the spec made `PUSH R8` / `POP R8` change the stack pointer by one. We chose the behaviour a programmer expects and updated SPEC §3.4.

**After a fault, PC points at the guilty instruction** ([D-010](../DECISIONS.md#d-010--after-a-fault-pc-points-at-the-faulting-instruction-fault-codes-are-numbered---milestone-m03-2026-11-07)), and faults have stable numbers.

**FRAME and RAND now, in a `SystemDevice`** ([D-011](../DECISIONS.md#d-011--frame-and-rand-live-in-a-systemdevice-rand-is-a-seeded-xorshift-generator---milestone-m03-2026-11-07)). The cycle budget belongs to SPEC §6 "Timing", whose last step is "FRAME increases by 1", so the frame loop and FRAME arrive together. RAND starts from a fixed seed, so tests are repeatable.

**A `Machine` class.** Frontends shouldn't have to know how to wire a bus, a CPU and devices together. `Machine` does it once, and it is where the video chip (M6) and sound chip (M7) will plug in.

## How it works — the plain-language version

### The clerk's desk
- **Nine registers**, R0 to R8, are the clerk's notepads, each holding one word. R8 has a second job: it is the **stack pointer** (SP), marking the top of the stack.
- **The program counter (PC)** is the clerk's bookmark: the address of the next instruction to read.
- **Two flags**, each a single trit:
  - **S** (sign) remembers whether the last result was negative (−1), zero (0) or positive (+1). Binary CPUs need two flags for this ("zero" and "negative"). One trit says it all.
  - **O** (overflow) remembers the carry of the last ADD, SUB or ADDI: +1 if the result rolled over the top, −1 if it rolled under the bottom, 0 otherwise. A division by zero also sets it to +1.

### Reading an instruction
Every instruction starts with one word, divided into four fields:

```
  ADD R1, R2   =   +0-  -0  -+  00000
                   ---  --  --  -----
                   op   rd  rs  imm5
                   8    −3  −2  0
                   ADD  R1  R2  (unused)
```

- **op** (3 trits) says *what* to do: 27 possible values, 27 instructions, none wasted.
- **rd** (2 trits) is the destination register, where the result goes.
- **rs** (2 trits) is the source register, the second input.
- **imm5** (5 trits) is a small number built into the instruction (an *immediate*), from −121 to +121, used for example by `ADDI R0, 5` ("add 5 to R0").

A register field holds the register number minus 4: R0 is −4, R4 is 0, R8 is +4. Nine values fit exactly in two trits.

Some instructions need a full word of extra information, such as a jump address or a big number. That word simply follows the instruction in memory. `LDI R0, 100000` is two words; `BR3` with its three target addresses is four.

### The work cycle
The clerk repeats three steps forever:

1. **Fetch**: read the word at PC, decode its fields, read any extra words, and move PC past all of it.
2. **Execute**: do what the opcode says. Every instruction costs exactly **one cycle**, however long it is.
3. Repeat. (A fault stops the loop: see below.)

### What the 27 instructions do, in groups
| Group | Instructions | In plain words |
|---|---|---|
| Control | `HLT`, `NOP`, `WAIT` | stop for good; do nothing; rest until the next frame |
| Moving data | `MOV`, `LDI`, `LD`, `ST` | copy register to register; load a constant; load from memory; store to memory |
| Stack | `PUSH`, `POP` | put a register on the pile; take the top note off the pile |
| Arithmetic | `ADD`, `SUB`, `ADDI`, `MUL`, `DIV`, `MOD`, `NEG` | the M1 calculations on registers |
| Trit-wise | `MIN`, `MAX`, `TMUL`, `ROT`, `SHT` | the M1 trit-by-trit operations |
| Comparing | `CMP` | set S to "smaller / equal / bigger", without changing anything else |
| Jumping | `JMP`, `JSR`, `RET`, `JIF`, `BR3` | go elsewhere; call a subroutine; come back; jump if a condition holds; three-way jump |

### Decisions: JIF and BR3
`JIF` ("jump if") looks at S and jumps only if a condition holds. There are six conditions:

| condition | name | jumps when S is … |
|---|---|---|
| −1 | `JN` | −1 (negative) |
| 0 | `JZ` | 0 (zero) |
| +1 | `JP` | +1 (positive) |
| +2 | `JNZ` | −1 or +1 (not zero) |
| −2 | `JLE` | −1 or 0 (not positive) |
| +3 | `JGE` | 0 or +1 (not negative) |

The condition is stored in the rd field, which can hold nine values. The other three (−4, −3, +4) mean nothing, so the CPU stops with a **fault**: it halts and records "fault 1, invalid JIF condition, at address …" for the debugger.

`BR3` is the most ternary instruction of all: one instruction, three destinations. After `CMP R0, R1` it jumps to the first address if R0 < R1, the second if they are equal, the third if R0 > R1. A binary CPU needs two jumps for that.

### Subroutines: JSR and RET
A *subroutine* is a piece of program you can call from anywhere, like a recipe you consult and then return to your place in the cookbook. `JSR 300` ("jump to subroutine") remembers where to come back to by **pushing** the return address onto the stack, then jumps. `RET` pops that address back into PC.

```
 address  instruction            stack (top at the bottom of the picture)
   243    JSR 300   ───┐         … | 245 |   ← SP = 59,048 (pushed: return to 245)
   245    HLT    ◄──┐  │
                    │  │
   300    LDI R0, 9 ◄──┘
   302    RET  ─────┘            … |     |   ← SP = 59,049 (popped)
```

Because the return addresses are on a stack, a subroutine can call another subroutine, which can call another, and each `RET` finds the right way back.

### The rhythm: frames, budget and WAIT
The machine runs in *frames*, 60 per second. In each frame the CPU gets **59,049 cycles** (3^10), which is about 3.5 million instructions per second. After the CPU's turn, the video chip draws the picture (from M6) and the FRAME counter goes up by one.

A game usually doesn't need its whole allowance. It moves the player, then executes `WAIT`: "I'm done; wake me at the start of the next frame." The rest of the frame's cycles are simply skipped. This keeps the game's speed steady: one update per frame, whatever the work.

```
frame 1:  ADDI  WAIT · · · · · · · · · · · ·   (2 cycles used, 59,047 skipped)
frame 2:  ADDI  WAIT · · · · · · · · · · · ·   (continues right after the WAIT)
```

A **halted** CPU does nothing at all, but the frames keep coming: FRAME keeps counting, and from M6 the screen keeps showing.

### Speed
On the development PC (8 cores, Java 25) the CPU runs:

| program | instructions per second | compared with the real machine |
|---|---|---|
| typical mix (loads, stores, arithmetic, calls, jumps) | about 47 million | **13 ×** faster than needed |
| worst case: nothing but MIN, MAX, TMUL, ROT | about 20 million | **5.6 ×** faster than needed |

So one frame takes about 1.3 ms of the 16.7 ms available, leaving plenty of time for drawing and sound.

## How it works — the programmer's version

Study the classes in this order.

### 1. `Instruction`: encoding and tables
[`Instruction.java`](../../triada-core/src/main/java/org/triada/core/cpu/Instruction.java).
- Constants `HLT = 0` … `WAIT = -13`, and `COND_N` … `COND_GE` for JIF.
- `Instruction.encode(opcode, rdField, rsField, imm5)` computes `opcode·3^9 + rd·3^7 + rs·3^5 + imm5`, after range-checking every field.
- `Instruction.reg(n)` converts a register number to its field value (`n − 4`).
- Decoding: `Instruction.opcode()`, `Instruction.rdField()`, `Instruction.rsField()` and `Instruction.imm5()` use the offset trick:

```java
public static int imm5(int word)    { return (word + Word.MAX) % 243 - 121; }
public static int rsField(int word) { return (word + Word.MAX) / 243 % 9 - 4; }
```
- `word + Word.MAX` is the word with every trit raised by one: an ordinary base-3 number 0 … 531,440.
- `% 243` keeps its low 5 digits (3^5 = 243). Those are imm5 + 121, so subtract 121.
- `/ 243 % 9` drops those 5 digits and keeps the next 2 (3^2 = 9). That is rs + 4.

`Instruction.mnemonic()`, `Instruction.length()` and `Instruction.isValidCondition()` are lookup helpers for the assembler, the disassembler and the debugger.

### 2. `Cpu`: state and `step()`
[`Cpu.java`](../../triada-core/src/main/java/org/triada/core/cpu/Cpu.java). The state is `int[] r` (9 registers), `int pc`, `int s`, `int o`, plus `halted`, `fault`, `faultPc`, `waitRequested` and a `cycles` counter.

The top of `Cpu.step()`:
```java
final int start = pc;
final int word = bus.read(pc);
pc = next(pc);
final int u = word + Word.MAX;
final int imm = u % 243 - 121;
final int rs = u / 243 % 9;          // register NUMBER 0 … 8 (field + 4)
final int rd = u / 2_187 % 9;        // register number, or condition + 4 for JIF
final int op = u / 19_683 - 13;
```
- Lines 1–3: remember where the instruction started (for faults), fetch it, and advance PC. `next()` wraps from `Word.MAX` to `Word.MIN`.
- Lines 4–8: decode all four fields at once. Note that `rs` and `rd` here are already register *indexes*, ready for `r[rs]`.

Then comes a `switch (op)` with one `case` per opcode. Most are one line that calls `WordMath` and the helper `setWithSign(reg, value)`, which stores a result and sets S. Some details worth noticing:

- **Two-word instructions** (`LDI`, `JMP`, `JSR`, `JIF`) read their extra word with `bus.read(pc)` and advance PC again. BR3 reads three words.
- **Flags.** `ADD`, `SUB` and `ADDI` set O from `WordMath.addCarry()` or `subCarry()` *before* storing, so `ADD R0, R0` reads both inputs first. `DIV`/`MOD` set O to 1 exactly when the divisor is zero. `MUL` and the rest leave O alone. `CMP` sets S from `WordMath.compare()` and stores nothing. `RET`, the jumps, `ST` and `PUSH` don't touch S at all.
- **Stack.** `push(value)` decrements R8 and writes; `pop()` reads and increments R8. Both wrap. `PUSH` passes `r[rd]` before `push()` changes R8, and `POP` writes `rd` after, which gives D-009's behaviour for R8.
- **Effective addresses** `rs + imm5` go through `WordMath.add()`, so they wrap like any word.
- **JIF** computes `cond = rd − 4`. An invalid condition calls `raise(Fault.INVALID_CONDITION, start)`, which halts and sets `pc` and `faultPc` to the instruction's address (D-010).
- **WAIT** only sets `waitRequested`. Ending the frame is `runFrame()`'s job.

### 3. `Cpu.runFrame()`: the budget
```java
while (used < budget && !halted) {
    step();
    used++;
    if (waitRequested) { waitRequested = false; break; }
}
```
- Run instructions until the budget is used or the CPU halts.
- A WAIT counts its own cycle, then ends the loop. The next call starts at the instruction after the WAIT.

The method returns the cycles used. That is useful for the debugger, and the tests check it exactly.

The state accessors for tests and the debugger are `Cpu.register()`, `Cpu.setRegister()`, `Cpu.pc()`, `Cpu.setPc()`, `Cpu.flagS()`, `Cpu.flagO()`, `Cpu.setFlags()`, `Cpu.isHalted()`, `Cpu.fault()`, `Cpu.faultPc()` and `Cpu.cycles()`. `Cpu.reset()` restores the power-on state from SPEC §2.4: everything 0, PC = −19,683.

### 4. `Fault`
[`Fault.java`](../../triada-core/src/main/java/org/triada/core/cpu/Fault.java) is an enum with a stable `Fault.code()` and a `Fault.description()`.

### 5. `SystemDevice` and `Machine`
[`SystemDevice.java`](../../triada-core/src/main/java/org/triada/core/io/SystemDevice.java) answers register 0 (FRAME) and register 19 (RAND).
- `SystemDevice.advanceFrame()` counts frames, wrapping like a word.
- RAND keeps the *next* value ready in `nextRandom`, so `peek` can show it and `read` hands it out and computes a new one with xorshift32. Xorshift is a classic tiny random-number generator (George Marsaglia, 2003): three steps on a 32-bit number, each shifting the bits sideways and combining them with the original using XOR (a binary operation that flips each bit where the other number has a 1).
- `SystemDevice.seed()` restarts the sequence.

[`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java) creates the `Bus`, the `Cpu`, an `InputDevice` and a `SystemDevice`, and attaches the devices to registers 0, 1–6 and 19.
- `Machine.runFrame()` = `cpu.runFrame(CYCLES_PER_FRAME)`, then (from M6) render, then `system.advanceFrame()`.
- `Machine.powerOn()` clears RAM, resets the CPU, FRAME and the key queue, and keeps the ROM.

### 6. The benchmark
[`CpuBenchmark.java`](../../triada-core/src/test/java/org/triada/core/cpu/CpuBenchmark.java) is in the test sources and runs as its own Gradle task, `benchmark`, defined in [`triada-core/build.gradle.kts`](../../triada-core/build.gradle.kts). Timing depends on the PC, so it is not a pass/fail test.

It loads one of two endless loops (`CpuBenchmark.loadMixed()` and `CpuBenchmark.loadTritHeavy()`), runs 600 frames to let the *JIT compiler* translate `step()` into fast machine code, then runs 60-frame batches for 3 seconds. Besides speed, it reports the bytes allocated, using `CpuBenchmark.allocatedBytes()`, a HotSpot per-thread counter.

## Build it yourself from scratch
1. Write the encoder and the four field decoders. Test them **exhaustively**: for every one of the 531,441 words, `encode(decode(w)) == w`. It runs in milliseconds and catches every off-by-one in the field positions.
2. Write the CPU state and `step()` with a `switch`. Start with HLT, NOP, MOV and LDI, then load tiny hand-assembled programs into RAM and step through them.
3. Add the instructions group by group, with a test for each that also checks the flags. **Pitfall:** decide for every instruction whether it sets S, sets O, or leaves them alone (SPEC §3.4's last column), and test the "leaves alone" cases too, by setting the flags to unusual values first.
4. Stack: decrement-then-write for PUSH, read-then-increment for POP. Test last-in-first-out order, and decide what `PUSH SP` / `POP SP` mean (D-009).
5. JIF: test all 6 conditions × 3 values of S = 18 cases in a table, plus the 3 invalid conditions. **Pitfall:** read the jump address word even when you don't jump, or PC ends up pointing at the address word and executes it as an instruction.
6. `runFrame(budget)`: the loop above. Test that a busy loop uses exactly the budget, that WAIT ends the frame and the next frame continues after it, and that a halted CPU uses 0 cycles.
7. Measure. Run a loop for a few seconds after a warm-up and divide. If you are below 3.5 million instructions per second, profile before optimising. Check allocations with `com.sun.management.ThreadMXBean.getThreadAllocatedBytes()`.

## How we know it works

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. `triada-core` has 179 tests, 64 of them new in M3, and `triada-cli` has 1.

| Test class | Tests | What it proves |
|---|---|---|
| [`InstructionTest`](../../triada-core/src/test/java/org/triada/core/cpu/InstructionTest.java) | 8 | The field layout matches SPEC §3.3 trit for trit. **`InstructionTest.everyWordDecodesIntoFieldsThatEncodeBackToIt`** covers all 531,441 words. Field ranges, register numbering, 27 distinct mnemonics, and the length of every opcode. |
| [`CpuTest`](../../triada-core/src/test/java/org/triada/core/cpu/CpuTest.java) | 45 | Every opcode, including S and O (set or unchanged). The spec's idioms (`MOV R0, R0`, `SUB R0, R0`, `PUSH`+`RET`). **`CpuTest.everyConditionAgainstEverySign`** (18 cases). Invalid conditions fault with code 1 at the right PC. BR3 three ways, and CMP + BR3. WAIT and frame continuation. A busy loop uses exactly 59,049 cycles. Power-on state, and execution starting in ROM. PC, SP and effective-address wrap-around. `PUSH R8`/`POP R8` (D-009). |
| [`MachineTest`](../../triada-core/src/test/java/org/triada/core/MachineTest.java) | 10 | FRAME counts up even when halted, is read-only and wraps from MAX to MIN. RAND is repeatable, `peek` doesn't consume it, and it covers the range evenly. Power-on keeps ROM and clears the rest. A program can read FRAME across a WAIT. |
| [`CpuAllocationTest`](../../triada-core/src/test/java/org/triada/core/cpu/CpuAllocationTest.java) | 1 | 100 frames (5.9 million instructions) allocate less than 10 KB in total, i.e. nothing per instruction. |

Measure the speed:

```bash
./gradlew :triada-core:benchmark
```

Output on the development PC (numbers vary from PC to PC and run to run):

```
TRIADA-85 CPU benchmark (Java HotSpot(TM) 64-Bit Server VM 25.0.4, 8 cores)
mixed       2,400 frames in 3.03 s: 46,700,370 instructions/s = 13.2 x real time, 1.264 ms per frame, 792 bytes allocated
trit-heavy  1,020 frames in 3.03 s: 19,907,380 instructions/s = 5.6 x real time, 2.966 ms per frame, 792 bytes allocated
```

The 792 bytes are the same for both programs, however many instructions run, so they come from the measuring code, not the CPU.

**Mutation check.** We broke the CPU twice on purpose: JLE tested `S ≠ −1` instead of `S ≠ +1`, and PUSH wrote before decrementing SP. 8 tests failed, including `everyConditionAgainstEverySign`, `stackIsLastInFirstOut` and `jsrPushesTheReturnAddressAndRetComesBack`. With the code restored, all pass.

## Problems we hit and how we solved them
- **Three gaps in the spec**: PUSH/POP of R8 ([D-009](../DECISIONS.md#d-009--source-registers-are-read-first-push-r8-and-pop-r8---milestone-m03-2026-11-07)), which PC a fault records ([D-010](../DECISIONS.md#d-010--after-a-fault-pc-points-at-the-faulting-instruction-fault-codes-are-numbered---milestone-m03-2026-11-07)), and where FRAME and RAND come from ([D-011](../DECISIONS.md#d-011--frame-and-rand-live-in-a-systemdevice-rand-is-a-seeded-xorshift-generator---milestone-m03-2026-11-07)). SPEC §3.4 and §3.9 were updated.
- **Mistakes in our own first test draft**, caught by re-reading before running:
  - One test ran a program to its HLT and then tried to step the halted CPU again. Halted is final until reset, so it was rewritten to use WAIT and two frames.
  - One BR3 test jumped to a *data* word, which happened to decode as HLT, so the test would have passed for the wrong reason. It now jumps to a real HLT.
- **Hand-assembling is error-prone.** Writing instruction words with `Instruction.encode()` works but is tiring and easy to get wrong, for example by counting addresses for jump targets by hand. That is the motivation for M4. From then on, CPU tests can be written in assembly where that is clearer.

## Limitations and what comes next
- **The trit-wise instructions are the slowest** (5.6× real time in the worst case). That is comfortably enough. If the video chip (M6) needs the time, `WordMath` could switch to lookup tables, with no change to the CPU.
- **No video or sound yet.** `Machine.runFrame()` has a marked spot where the video chip will render (M6).
- **The debugger hooks are only accessors.** Breakpoints and "step over JSR" come in M9, built on `step()`.
- **Next, M4:** the assembler and disassembler, so programs can be written as `ADD R1, R2` instead of `Instruction.encode(8, -3, -2, 0)`.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **CPU (central processing unit):** the part of a computer that reads and carries out instructions.
- **Instruction / Opcode / Mnemonic:** one command for the CPU / its number / its short name, like `ADD`.
- **Operand / Immediate:** an input to an instruction / a small number built into the instruction itself.
- **Program counter (PC):** the register holding the address of the next instruction.
- **Fetch / Decode / Execute:** read the instruction / split it into its fields / carry it out.
- **Cycle:** one tick of the CPU's clock. On TRIADA, one instruction.
- **Cycle budget:** the cycles the CPU may use per frame (59,049).
- **Subroutine:** a piece of program that can be called from anywhere and returns to its caller.
- **Return address:** where to continue after a subroutine; kept on the stack.
- **Conditional jump / Branch:** a jump that only happens if a condition on the flags holds.
- **Fault:** an error that stops the CPU, recorded with a code and an address.
- **Halt:** stop the CPU for good, until a reset.
- **Emulator / Interpreter:** a program that imitates another computer / one that does so instruction by instruction.
- **JIT compiler:** the part of Java that translates frequently used code into fast machine code while the program runs.
- **Benchmark:** a program that measures speed.
- **Pseudo-random number generator / Seed:** a formula producing numbers that look random / the starting value that decides the whole sequence.
