# Milestone 09 — Debugger

## In one paragraph
When a program misbehaves, you want to look inside the machine: stop it, see what is in every register, follow it one instruction at a time, and let it run until it reaches a particular spot. That is what a *debugger* does. Press F1 in the TRIADA-85 window and a panel opens beside the (now smaller) screen. It shows the processor's registers in decimal and balanced ternary, the program around the current instruction translated back into assembly, a window into memory, and buttons to pause, continue, step, and "step over" a whole subroutine. Click a line of the program to make the machine stop there next time. This milestone also lets you put a program into the machine by dropping its file onto the window, the TRIADA's version of inserting a disk.

## What was built
- **In the core** ([D-030](../DECISIONS.md#d-030--debugging-support-in-the-core-stop-before-the-instruction-finish-the-frame-later---milestone-m09-2026-11-07)):
  - `Cpu.run()` stops *before* an instruction at a breakpoint and says why it stopped.
  - `Machine` can leave a frame unfinished at a breakpoint and continue it later; `Machine.step()` runs one instruction.
  - Breakpoints cost nothing while there are none.
- **The debugger's logic** (`Debugger`, plain Java): pause, continue, step, step over (recursion-safe), breakpoints, automatic pause on breakpoints and faults, disassembly around PC, memory and register views, address input in decimal or ternary, and "insert triskette".
- **The debugger's panel** (`DebuggerView`, scene2d.ui), drawn with the TRIADA's own ROM font (`TriadaFont`) and a skin built in code ([D-031](../DECISIONS.md#d-031--the-debuggers-interface-scene2d-with-a-code-built-skin-and-the-rom-font---milestone-m09-2026-11-07)). F1, F5, F10 and F11 drive it, and clicking a program line sets a breakpoint.
- **Loading programs:** `--load file.tri` on the command line, and dropping a `.tri` or `.tasm` file on the window ([D-032](../DECISIONS.md#d-032--loading-programs---load-and-dropping-a-triskette-on-the-window---milestone-m09-2026-11-07)).
- **29 new tests:** 11 for the core's stop/continue behaviour, 16 for the debugger's logic, 2 for the font.

## Why it was built this way

**The hard part lives in the core, where it can be exact.** A debugger that stops "somewhere around" an instruction is useless. The CPU now checks the breakpoint table *before* each instruction. The machine remembers how many cycles are left in the current frame, so a frame stopped 100 times still gets exactly its 59,049 cycles, and the program can't tell it was ever stopped. The frontend could have done this bookkeeping itself, but then the rules for "when does a frame end" would exist twice.

**Logic and display are separate.** Every decision (should we stop here? where does step over end? which lines surround PC?) is in `Debugger`, which needs no window and is tested. `DebuggerView` only copies state into labels and turns clicks into calls.

**The machine's own font.** libGDX's built-in font is proportional, so a column of trits would wobble. The TRIADA's 8×8 font from the ROM is monospaced, so it lines everything up, and the debugger looks like it was built in 1985 too. libGDX expects a font as a picture plus a description file; we make both in memory.

**Step over must survive recursion.** "Step over" puts a temporary stop at the instruction after the JSR. In a recursive subroutine that same address is passed several times on the way back up, at deeper levels. The stop therefore counts only when the stack pointer is back where it was, which means "we are back at our own level".

## How it works — the plain-language version

### Stopping a running machine
A *breakpoint* is a note on an instruction's address: "stop before you do this". The processor checks for such a note before every instruction. When it finds one, it stops right there, in the middle of a frame. The picture isn't redrawn and FRAME doesn't count up, because the frame isn't over. When you continue, the processor carries out that instruction and goes on, and the frame finishes exactly as it would have.

Think of reading a book with bookmarks: you stop at a bookmark, look around, then carry on reading from the same word. The book doesn't change because you stopped.

### What you see
```
 +---------------------------+---------------------------------+
 | the TRIADA screen, 2x     | TRIADA-85 DEBUGGER    F1        |
 |                           | PAUSED: BREAKPOINT AT 357       |
 |                           | [RUN F5] [STEP F11] [OVER F10]  |
 |                           | R0       1 00000000000+          |
 |                           | ...                             |
 |                           | PC     357 000000++++-0          |
 |                           | S +  O 0  CYCLES 97990          |
 +---------------------------+ DISASSEMBLY (CLICK = BREAK)     |
 | MEMORY [ -20412 ] GO - +  |      355 LDI R5, 208            |
 | -20412    482 00000+-000--| >*   357 CMP R3, R5             |
 | -20411      0 0000...     |      358 JLE 365                |
 +---------------------------+---------------------------------+
```

- **Registers:** R0–R7, SP (= R8) and PC, each as a decimal number and as all 12 trits, plus the S and O flags and the cycle count.
- **Disassembly:** the program around PC, turned back into assembly by M4's disassembler. `>` marks the instruction about to run; `*` marks breakpoints. Click any line to set or clear one.
- **Memory:** eight words from any address you type, in decimal or `0t…`. The viewer only *peeks*, so looking at the KEY register doesn't take the key away (M2's `peek`).

### The four keys
| Key | Does |
|---|---|
| F1 | show / hide the debugger |
| F5 | pause / continue |
| F11 | step: exactly one instruction |
| F10 | step over: like F11, but a `JSR` runs the whole subroutine and stops after it |

When a breakpoint or a fault stops the machine, the panel opens by itself, and the status line says why. For example: `PAUSED: FAULT 1 (INVALID JIF CONDITION) AT 244`.

### Inserting a triskette
Drag a `.tri` file (or a `.tasm` source) onto the window. The machine is switched off and on again, boots, and starts that program, just like a real computer started with a disk in the drive. Breakpoints you set before stay set, so you can catch a program at its very first steps.

## How it works — the programmer's version

### 1. `Cpu.run()` and `Machine`'s frame bookkeeping
[`Cpu.java`](../../triada-core/src/main/java/org/triada/core/cpu/Cpu.java): `Cpu.run(budget, breakAt, ignoreFirst)` is the old `runFrame` loop with one more check:

```java
if (breakAt != null && breakAt[pc + Word.MAX] && !(first && ignoreFirst)) {
    lastStop = Stop.BREAKPOINT;
    return used;
}
```
- `breakAt` is indexed by address + 265,720 (the offset trick once more), so one array covers every address.
- `ignoreFirst` lets execution *leave* a breakpoint: the instruction we stopped at runs, and the next breakpoint stops us again.

`Cpu.runFrame()` is now `run(budget, null, false)`, and `Cpu.lastStop()` tells callers why `run` returned (`Cpu.Stop`).

[`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java):
- `Machine.runFrame()` runs the rest of the frame with `cyclesLeft`. On a breakpoint it returns *without* `finishFrame()` (render, sound, FRAME + 1, fresh budget) and remembers `atBreakpoint`, so the next call resumes and passes `ignoreFirst = true`.
- `Machine.step()` is `cpu.run(1, null, true)`, finishing the frame on WAIT, on the budget's last cycle, or when halted.
- `Machine.setBreakpoint()`, `Machine.isBreakpoint()` and `Machine.clearBreakpoints()` manage the lazily created `boolean[531441]`, which becomes `null` again when the last breakpoint goes.
- `Machine.stoppedAtBreakpoint()`, `Machine.cyclesLeftInFrame()` and `Machine.ignoreBreakpointAtPc()` complete the API.

### 2. `Debugger`
[`Debugger.java`](../../triada-desktop/src/main/java/org/triada/desktop/debug/Debugger.java):
- `Debugger.runFrames(due)` runs frames unless paused. After each, it checks for a breakpoint stop (and calls `shouldStopAt`) or a fault. Finished frames call the frame listener, which hands the sound to the speakers.
- `shouldStopAt(pc)`:
  - If a step over is active and PC is its target, it stops only when `SP == stepOverSp`; otherwise (a deeper level) it returns false and the same frame simply continues.
  - Any other breakpoint stops and cancels the step over, so a user breakpoint inside the subroutine wins.
- `Debugger.stepOver()`:
  - if the instruction at PC isn't a JSR (`Instruction.opcode(...) != JSR`), it calls `Debugger.step()`;
  - otherwise it remembers PC + 2 and SP, executes the JSR itself with `Machine.step()` (so a breakpoint on the JSR can't block it), sets a temporary breakpoint on the return address if there isn't a user one already, and continues.
- `Debugger.resume()` calls `Machine.ignoreBreakpointAtPc()`, so continuing from a breakpoint never stops at the same spot again.
- `Debugger.disassembly(before, after)` can't simply walk backwards, because instructions are 1–4 words long. It tries starting 12, 11, … 1 words before PC, decodes forward with `Disassembler.disassemble(address, bus::peek)`, and uses the first start that lands exactly on PC.
- `Debugger.memory()`, `Debugger.registers()`, `Debugger.status()` and `Debugger.parseAddress()` (decimal or `0t`) feed the panels.
- `Debugger.insertTriskette()` = `powerOn()` + `Rom.autorun()`.

### 3. The panel
[`DebuggerView.java`](../../triada-desktop/src/main/java/org/triada/desktop/debug/DebuggerView.java):
- builds a `Stage` with a root `Table` of two columns, the left one starting with a spacer cell the size of the screen;
- `buildSkin()` makes the Label, TextButton and TextField styles from one tinted white pixel;
- `DebuggerView.refresh()` copies the debugger's state into the labels every frame while visible; `DebuggerView.resize()` sizes the spacer;
- disassembly labels carry a `ClickListener` that toggles a breakpoint at the line's address;
- text fields run their action on Enter and hand the keyboard back.

[`TriadaFont.java`](../../triada-desktop/src/main/java/org/triada/desktop/debug/TriadaFont.java):
- `TriadaFont.fontRows()` reads the 760 font rows from the ROM image at the `font` label;
- `atlas()` draws them at 2× into a Pixmap;
- `TriadaFont.description()` writes the BMFont text (one `char` line per glyph);
- `TriadaFont.create()` builds the `BitmapFont`, passing the text through `TriadaFont.MemoryFile`, a `FileHandle` whose `read()` returns it.

### 4. The app and the launcher
[`TriadaApp.java`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java):
- `render()` asks the `FrameClock` how many frames are due and lets the debugger run them. If the debugger has just paused, it opens the panel. Then it draws the screen (full size, or 2× at the top left), and then the panel.
- Input goes through an `InputMultiplexer`: the F-keys first, then the panel's stage (only while shown), then `InputMapper` for the TRIADA.
- `TriadaApp.insertTriskette()` loads a dropped file.

[`DesktopLauncher.java`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java):
- `DesktopLauncher.programArgument()` understands `--load FILE`;
- `config.setHdpiMode(HdpiMode.Pixels)`;
- a window listener forwards `filesDropped` to the app.

## Build it yourself from scratch
1. **First make the core stoppable.** A run loop that stops *before* an instruction, and a frame whose remaining budget survives the stop. Test it: a frame interrupted many times must still use exactly its budget, and the instruction at the breakpoint must not have run yet.
2. **Decide what "continue" means at a breakpoint:** execute that instruction first. Otherwise continue does nothing at all.
3. **Single step,** including the cases where the step ends a frame (WAIT, last cycle) and where the CPU is halted.
4. **Write the debugger's logic as a plain class with tests,** before any user interface.
5. **Step over:** a temporary breakpoint plus a stack-pointer check. Test it with a recursive subroutine. **Pitfall:** a user breakpoint on the JSR itself would stop step over immediately, so execute the JSR as a single step first.
6. **Disassembling backwards:** try several start points and keep the one that lands on PC.
7. **The panel last.** Keep it dumb. Use a monospace font, or columns of trits won't line up.
8. **Try it in the real window,** with the mouse and keys. Our first layout put the screen at 1× because of two 8-pixel margins.

## How we know it works

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. Tests: 250 in `triada-core`, 98 in `triada-assembler`, 56 in `triada-rom`, 48 in `triada-cli` and 32 in `triada-desktop`.

| Test class | Tests | What it proves |
|---|---|---|
| [`MachineDebugTest`](../../triada-core/src/test/java/org/triada/core/MachineDebugTest.java) | 11 | A breakpoint stops *before* the instruction and leaves the frame open. Resuming runs it and stops again next time round. **`aFrameInterruptedByBreakpointsStillGetsExactlyItsBudget`** (100 stops, still 59,049 cycles). Step runs one instruction and ignores a breakpoint under PC. Stepping WAIT or the last cycle finishes the frame. Stepping a halted CPU moves time. Adding and removing breakpoints. `Cpu.run`'s stop reasons. Power-on forgets a half-finished frame. |
| [`DebuggerTest`](../../triada-desktop/src/test/java/org/triada/desktop/debug/DebuggerTest.java) | 16 | Run / pause / resume; nothing runs while paused. A breakpoint pause and its status line. Continuing from a pause on a breakpoint (exactly 4 instructions, then a stop in the next frame). Step. **Step over PUTCHAR** (a whole ROM routine). Step over a non-JSR. **Step over a recursive call** (stops only at the same depth, with 4! computed). A user breakpoint inside the subroutine wins. A fault pauses with code and address. Disassembly markers, and alignment after a two-word LDI. Memory lines (and peeking KEY doesn't eat the key). Register lines. Address parsing. Inserting a triskette. |
| [`TriadaFontTest`](../../triada-desktop/src/test/java/org/triada/desktop/debug/TriadaFontTest.java) | 2 | libGDX's own parser reads the generated description as 95 monospaced 16-pixel glyphs; the glyph rows come from the ROM. |

**In the real window.** We started `--load programs/colors.tasm`, pressed keys and clicked with an external tool, and checked each step on a screenshot:
1. F1 opened the panel, with the screen at 2× on the left. In the first try it was at 1×: the left column was 464 pixels instead of 480, and we fixed it by narrowing the panel.
2. F5 paused (`PAUSED`).
3. Three F11 steps moved PC 353 → 354 → 355 → 357 (the `LDI` at 355 is two words long).
4. A click on the line `357 CMP R3, R5` set a breakpoint (`*`, `AT 357`).
5. F5 continued, and the machine stopped there: `PAUSED: BREAKPOINT AT 357`, with `>*` on the line.
6. Typing `-20412` + Enter in the memory field showed the I/O registers, with FRAME = 482.

```bash
./gradlew :triada-desktop:run --args="--load programs/colors.tasm"
```

**Not tested in the real window:** dropping a file. Windows delivers a drop as a message that our external tool couldn't fake. The drop handler calls the same `insertTriskette()` as `DebuggerTest.insertingATriskettePowersOnAndRunsIt`. Step over (F10) was tested in `DebuggerTest`, not in the window.

**Mutation check.** We broke three things at once:
- resuming forgot that the CPU stands on a breakpoint;
- step over ignored the stack pointer;
- breakpoints stopped *after* the instruction instead of before.

7 tests failed across two modules: `aBreakpointStopsBeforeTheInstructionAndLeavesTheFrameOpen`, `resumingRunsTheBreakpointInstruction…`, `stepOverARecursiveCallStopsOnlyWhenBackAtTheSameLevel`, `stepOverRunsAWholeSubroutine` and three more. With the code restored, all pass.

## Problems we hit and how we solved them
- **A hanging test.** One of our first core tests ran breakpoint stops until "1 cycle left in the frame". But the loop was 3 instructions long and 59,049 is divisible by 3, so that moment never came, and the test ran forever (we stopped it after 10 minutes). The test now walks to the frame's last cycle with single steps.
- **A wrong expectation about continuing.** We expected PC to have moved after continuing from a pause on a breakpoint. In fact the frame ran to its WAIT and came back round to the same instruction, which is correct behaviour. The test now checks exactly what happened: 4 instructions, then a stop in the next frame.
- **"Continue" could have been stuck forever:** paused by the user with PC on a breakpoint, the next frame would stop again immediately. We found this while designing `Debugger` and added `Machine.ignoreBreakpointAtPc()`. A test covers it.
- **The screen at 1× instead of 2×** in the first window try: the panel was 16 pixels too wide.

## Limitations and what comes next
- **No register or memory editing** in the panel. Use the monitor's POKE, or a breakpoint and a program.
- **No symbols in the disassembly:** it shows `JSR -19681`, not `JSR PUTCHAR`. The ROM's symbols exist (`Rom.symbols()`), so this would be a small addition.
- **Disassembling backwards is a heuristic.** Right after data words it may show a misaligned line or two before PC; PC itself and everything after it are always right.
- **Sound stops while paused**, holding its last value (no click), and the screen updates only when a frame ends (D-030).
- **Drag-and-drop wasn't tested** in a real window (see above).
- **Next, M10:** games written in TRIADA assembly: a snake game, a puzzle built on ternary logic, and a scrolling action game. That is the real test of the whole machine, and the debugger will come in handy.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Debugger:** a tool for stopping a program and looking inside the machine.
- **Breakpoint:** a mark on an instruction: "stop before executing this".
- **Single step:** executing exactly one instruction.
- **Step over:** executing a whole subroutine call as if it were one step.
- **Call depth:** how many subroutine calls deep the program currently is; on TRIADA, visible in SP.
- **Pause / Resume:** stopping and continuing execution.
- **Widget / Skin:** a piece of a user interface (label, button, text field) / the set of looks (fonts, colours) widgets use.
- **Monospace font:** a font whose characters are all equally wide.
- **Drag and drop:** moving a file onto a window with the mouse to open it there.
- **Heuristic:** a rule of thumb that usually gives the right answer.
