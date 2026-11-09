# Milestone 16 — Housekeeping: prior art, and the machine's birth certificate

## In one paragraph

This milestone changes nothing you can see on the machine. It does two tidy-up jobs before the next big phase, TRIADA-NET. First, it writes down honestly who came before us: the real Soviet ternary computers, the people who emulate them, other invented ternary machines, and the games that inspired the coming network. Second, it gathers every number that makes the TRIADA-85 what it is (how much memory, where the screen lives, how fast it runs, which parts are inside) into one place, a **machine profile**. A profile is the machine's birth certificate. It says how much memory the computer has and which parts are inside, and the emulator builds the computer from it. With that in place, a later model such as a TRIADA-88 is a new certificate, not a new emulator.

(The task file calls this "milestone M15". M15 was already TRIBASIC II, so this is M16, and TRIADA-NET starts at M17: [D-056](../DECISIONS.md#d-056--the-housekeeping-milestone-is-m16-triada-net-starts-at-m17---milestone-m16-2026-11-09).)

## What was built

- A **"Prior art and related work"** section in the [README](../../README.md#prior-art-and-related-work) and a fuller page, [PRIOR-ART.md](../PRIOR-ART.md), linked from [INDEX.md](../INDEX.md).
- [`MachineProfile`](../../triada-core/src/main/java/org/triada/core/MachineProfile.java): an immutable record holding the memory map, the RAM addresses the hardware reads, the timing, the screen, the voices, the plugged-in devices and the ROM images of one model. It checks itself when it is created.
- [`MachineProfiles`](../../triada-core/src/main/java/org/triada/core/MachineProfiles.java): the models there are. Today there is one, `MachineProfiles.TRIADA_85`, with exactly the numbers of [SPEC.md](../SPEC.md). `MachineProfiles.byId` finds a model by name.
- `Machine`, `Bus`, `Cpu`, `VideoChip`, `SoundChip` and `TrisketteDevice` now build themselves from a profile: `new Machine(profile)`. `new Machine()` is still a TRIADA-85.
- The assembler takes a profile: `new Assembler(loader, profile)`, with its built-in symbols from `BuiltinSymbols.of(profile)`. There are seven new built-ins, and the ROM source uses them instead of literal numbers.
- `Rom.newMachine(profile)` loads the ROM images the profile names.
- `triada tasm … --model M` and `triada run … --model M` (default `triada-85`).
- Tests:
  - `MachineProfilesTest` checks that the TRIADA-85 profile holds the SPEC numbers;
  - `ModifiedProfileTest` builds a made-up smaller model and checks that the machine follows it;
  - `ProfileSymbolsTest` checks the assembler's built-ins and default origin;
  - `ModelOptionTest` checks the CLI option.

## Why it was built this way

**Prior art, checked at the source.** Every project was opened and every claim checked against its own page, or, for the historical machines and the games, against Wikipedia. Two things in the task's table turned out wrong or unverifiable:
- `setun70` on PyPI is not `smaslovski/Setun70`. They are two separate Setun-70 emulators: the PyPI package comes from [github.com/Zaneham/setun70-emulator](https://github.com/Zaneham/setun70-emulator) and is written in Python; smaslovski's is written in Fortran. Both are listed, each with its own description.
- We could not confirm that SBTCVM's language is "maths-oriented", so we name its languages (SSTNPL and an assembler) without describing them.
- Its 27-colour 3-trit RGB mode is confirmed by its developer's own posts; the page links one.

**A record, not a file** ([D-057](../DECISIONS.md#d-057--a-machine-profile-one-immutable-record-per-model---milestone-m16-2026-11-09)). A configuration file would need a parser and file reading inside `triada-core`, which must stay free of file I/O for the web build. A Java record is checked by the compiler, cannot change after it is made, and validates itself once in its constructor.

**What is *not* in the profile.** The 12-trit word, the 27 instructions, the way four pixels pack into a word, and the numbers of the I/O registers. Those define the TRIADA *family*. A TRIADA-88 that changed them would not run TRIADA-85 programs at all.

**Old constants kept.** `MemoryMap.RAM_END`, `VideoChip.FRAME_WIDTH`, `Machine.CYCLES_PER_FRAME` and the rest still exist, but they are now *computed* from `MachineProfiles.TRIADA_85`. The rule was "every existing test passes unchanged", and hundreds of tests use them. They now mean "the TRIADA-85's value", and only code about that model uses them.

**New built-in symbols** ([D-058](../DECISIONS.md#d-058--the-assemblers-built-ins-come-from-the-profile-seven-new-ones---milestone-m16-2026-11-09)). The ROM repeated machine numbers as literals (`.org -19683`, `STACK_TOP = 59049`, `MAP + 459`). Now the assembler supplies `ROM_START`, `EXT_ROM_START`, `PROGRAM_START`, `STACK_TOP`, `MAP_COLS`, `MAP_ROWS` and `MAP_CELLS` from the profile. The assembler can only add and subtract, so `MAP_CELLS` (27 × 18) had to be a built-in too.

**`--model` on the CLI only** ([D-059](../DECISIONS.md#d-059----model-on-the-command-line-the-frontends-start-a-triada-85---milestone-m16-2026-11-09)). With one model, a menu in the window or web page would be clutter, and the task says the UI stays the same. The CLI option proves the whole path, from the command line to a machine and an assembler, with a few lines.

## How it works — the plain-language version

Think of a car factory. The factory doesn't keep "four doors, 1.6-litre engine, red" in the heads of the workers on the line. Each car comes with an order sheet, and every station reads its part from the sheet: the engine station reads "1.6 litres", the paint shop reads "red". To build a different car, you hand in a different sheet; the factory stays the same.

Before this milestone, the TRIADA emulator was like a factory where every station had "TRIADA-85" painted on its wall. The memory station knew "59,049 words". The screen station knew "216 by 144 pixels, the picture starts at address 44,000". The clock knew "59,049 steps per frame". To build a TRIADA-88 you would have had to repaint every wall and hope you didn't miss one.

Now there is an order sheet, the **profile**, our machine's birth certificate. For the TRIADA-85 it reads, in part:

```
  name ............ TRIADA-85
  RAM ............. addresses 0 ... 59,048       (59,049 words)
  main ROM ........ addresses -19,683 ... -1
  TRIBASIC ROM .... addresses -59,048 ... -20,413
  screen .......... 216 x 144 pixels, border 12
  tile map ........ starts at address 44,000
  speed ........... 59,049 steps per frame, 60 frames a second
  sound ........... 3 voices
  parts inside .... system clock, keyboard and joystick, video, sound,
                    debug port, triskette drive
```

When the emulator starts, it reads the sheet and builds each part to match: a memory of 59,049 words, a screen chip that looks for its picture at 44,000, a clock that counts 59,049 steps.

How do we know no station secretly ignores the sheet? We hand the factory a sheet for a machine that doesn't exist, a "TEST-SMALL", and check what comes out:
- only 19,683 words of memory, so writing to address 19,683 (fine on a TRIADA-85) must now do nothing;
- the picture lives at 14,000 instead of 44,000;
- 1,000 steps per frame instead of 59,049;
- one voice instead of three;
- no triskette drive.

If any part still had "TRIADA-85" painted on its wall, one of those checks would fail. They all pass.

**Why this makes future models possible.** A TRIADA-88 might have more memory, a wider screen or a fourth device. With profiles, that model is one new entry in `MachineProfiles` and perhaps a ROM image, plus tests. The CPU, the bus, the video chip and the assembler already build themselves from whatever the sheet says. The program `triada run game.tri --model triada-88` would be the only thing a user needs to know.

## How it works — the programmer's version

Read in this order.

1. **The record.** [`MachineProfile.java`](../../triada-core/src/main/java/org/triada/core/MachineProfile.java) → `MachineProfile`:
   - 25 components plus the `Device` enum (`SYSTEM`, `INPUT`, `VIDEO`, `SOUND`, `DEBUG`, `TRISKETTE`).
   - The compact constructor copies the collections (`Set.copyOf`, `List.copyOf`), so the record is truly immutable. It then rejects regions outside the word range or overlapping each other, tiles that aren't 8 × 8, a screen that isn't whole tiles, and voice counts outside 1 … 3.
   - Derived values are methods: `ramEnd()`, `romEnd()`, `ioEnd()`, `extRomEnd()`, `stackTop()`, `frameWidth()`, `frameHeight()`, `mapColumns()`, `mapRows()`, `has(Device)` and `isRam(int)`.
2. **The models.** [`MachineProfiles.java`](../../triada-core/src/main/java/org/triada/core/MachineProfiles.java) → `MachineProfiles.TRIADA_85`, `MachineProfiles.all()`, `MachineProfiles.byId(String)`. `MachineInfo.NAME` and `MachineInfo.SPEC_VERSION` now read from the profile.
3. **The bus.** [`Bus.java`](../../triada-core/src/main/java/org/triada/core/memory/Bus.java) → `Bus(MachineProfile)`. The constructor copies the region bounds into `final int` fields and sizes the RAM, ROM, extension ROM and device arrays from the profile. `Bus.read` and `Bus.write` run for every instruction, so they compare against those fields, not against method calls. RAM is now indexed `ram[address - ramStart]`, so a model whose RAM doesn't start at 0 works too. `Bus.profile()` lets every device find the profile. `MemoryMap.regionOf(MachineProfile, int)` is the profile-aware version of the old `MemoryMap.regionOf`.
4. **The CPU.** [`Cpu.java`](../../triada-core/src/main/java/org/triada/core/cpu/Cpu.java) → `Cpu.reset()` sets `pc = bus.profile().romStart()`.
5. **The video chip.** [`VideoChip.java`](../../triada-core/src/main/java/org/triada/core/video/VideoChip.java) → `VideoChip(Bus)` copies the screen size, border, map size, tile and sprite counts and the three RAM addresses into fields, and sizes its buffers from them. The render loops use the fields. `VideoChip.frameWidth()` and `VideoChip.frameHeight()` give the picture size of *this* machine.
6. **The sound chip.** [`SoundChip.java`](../../triada-core/src/main/java/org/triada/core/sound/SoundChip.java) → `SoundChip(int voices, int framesPerSecond)` sizes its voice arrays and its frame buffer (44,100 / fps samples). `SoundChip.registerCount()` is 3 per voice.
7. **The triskette.** [`TrisketteDevice.java`](../../triada-core/src/main/java/org/triada/core/triskette/TrisketteDevice.java) checks names and loads against `bus.profile()`'s RAM bounds.
8. **The machine.** [`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java) → `Machine(MachineProfile)` builds the bus, CPU and devices, and plugs in only the devices for which `profile.has(...)` is true. A missing device's registers are empty slots and read 0. The frame budget is the profile's `cyclesPerFrame`. `Machine.loadProgram` checks against the profile's RAM and sets SP to `stackTop()`. `Machine.profile()` returns the profile.

   ```java
   if (profile.has(Device.SOUND)) {
       bus.attach(SoundChip.FIRST_REGISTER, sound.registerCount(), sound);
   }
   ```
   *Only a model with a sound chip gets one plugged in. It occupies three registers per voice, starting at V1_FREQ (k = 10). The same pattern repeats for each device.*
9. **The ROM.** [`Rom.java`](../../triada-rom/src/main/java/org/triada/rom/Rom.java) → `Rom.newMachine(MachineProfile)` loads each image in `profile.romImages()` ("main" into the ROM, "tribasic" into the extension ROM). `Rom.autorun` checks the program against the machine's own RAM. [`RomGenerator.java`](../../triada-rom/src/generator/java/org/triada/rom/tools/RomGenerator.java) assembles the ROM explicitly for `MachineProfiles.TRIADA_85`.
10. **The assembler.** [`BuiltinSymbols.java`](../../triada-assembler/src/main/java/org/triada/assembler/BuiltinSymbols.java) → `BuiltinSymbols.of(MachineProfile)` builds the table (the TRIADA-85's is built once and reused, `BuiltinSymbols.all()`). [`Assembler.java`](../../triada-assembler/src/main/java/org/triada/assembler/Assembler.java) → `Assembler(SourceLoader, MachineProfile)` keeps that table and the profile's `programStart` as the default origin. `Assembler.DEFAULT_ORIGIN` stays as the TRIADA-85's.
11. **The ROM source.** [`rom/sysvars.tasm`](../../rom/sysvars.tasm) now says `COLS = MAP_COLS`, `ROWS = MAP_ROWS`, `LINE_MAX = MAP_COLS - 3`, `FREE_WORDS = TILES - PROGRAM_START`, `BASIC_COLD = EXT_ROM_START`. [`rom/rom.tasm`](../../rom/rom.tasm) starts with `.org ROM_START` and [`rom/basic.tasm`](../../rom/basic.tasm) with `.org EXT_ROM_START`, `B_MEMTOP = TILES`. [`rom/kernel.tasm`](../../rom/kernel.tasm) scrolls up to `MAP + MAP_CELLS - COLS`.
12. **The CLI.** [`TasmCommand.java`](../../triada-cli/src/main/java/org/triada/cli/TasmCommand.java) → `TasmCommand.model` turns `--model NAME` into a profile, or a usage error listing the known names. [`RunCommand.java`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java) passes it to the assembler and to `Rom.newMachine`. [`ScreenDump.java`](../../triada-cli/src/main/java/org/triada/cli/ScreenDump.java) → `ScreenDump.writePng` sizes the PNG from the machine's video chip.
13. **The desktop debugger.** [`DebuggerView.java`](../../triada-desktop/src/main/java/org/triada/desktop/debug/DebuggerView.java) starts its memory view at the machine's `programStart` and shows the profile's name in its title. [`TriadaFont.java`](../../triada-desktop/src/main/java/org/triada/desktop/debug/TriadaFont.java) → `TriadaFont.fontRows` finds the font relative to the ROM image's own load address.

### What was replaced

| Where | Was | Now |
|---|---|---|
| `MemoryMap` (all 17 constants) | literals 0, 59,049, −19,683, −20,412, 729, −59,048, 38,636, 243, 40,000, 44,000, 44,600, 45,000 … | `MachineProfiles.TRIADA_85.xxx()` |
| `Bus` | `RAM_SIZE`, `ROM_SIZE`, `EXT_ROM_SIZE`, `IoRegisters.COUNT`, region constants | fields from the bus's profile |
| `Cpu.reset()` | `MemoryMap.ROM_START` | `bus.profile().romStart()` |
| `VideoChip` | 216, 144, 12, 27, 18, 243, 27 and `MemoryMap.TILES` / `MAP` / `SPRITES` | instance fields from the profile; statics derived from TRIADA_85 |
| `SoundChip` | 3 voices, `SAMPLE_RATE / 60` | constructor arguments from the profile |
| `TrisketteDevice` | `MemoryMap.RAM_START` / `RAM_END` | `bus.profile()` |
| `Machine` | 60, 59,049, `MemoryMap.RAM_START` / `RAM_END`, `STACK_TOP`, every device always plugged in | the profile; devices by `profile.has` |
| `MachineInfo` | "TRIADA-85", "1.1" | the profile's name and spec version |
| `Rom`, `RomGenerator` | `MemoryMap.*` | `MachineProfiles.TRIADA_85` / the machine's profile |
| `BuiltinSymbols`, `Assembler` | `MemoryMap.*`, `DEFAULT_ORIGIN` | the assembler's profile |
| `rom/sysvars.tasm` | `COLS = 27`, `ROWS = 18`, `MAP_CELLS = 486`, `STACK_TOP = 59049`, `PROGRAM_START = 243`, `FREE_WORDS = 39757`, `BASIC_COLD = -59048`, `LINE_MAX = 24` | built-ins and expressions of them |
| `rom/rom.tasm`, `rom/basic.tasm`, `rom/kernel.tasm` | `.org -19683`, `.org -59048`, `B_MEMTOP = 40000`, `MAP + 459` | `ROM_START`, `EXT_ROM_START`, `TILES`, `MAP + MAP_CELLS - COLS` |
| `ScreenDump.writePng`, `DebuggerView`, `TriadaFont` | 240 × 168, 243, `MemoryMap.ROM_START` | the machine's video chip and profile, the ROM image |

Left as they are, on purpose:
- `19_683`, `729` and `27` in instruction decoding and pixel unpacking (`Cpu.step`, `Instruction.opcode`, `VideoChip.decodeTile`). These are powers of 3 in the word format, the same for every model.
- `SoundChip.SAMPLE_RATE` (44,100) is the host's audio rate, not the machine's.
- The frontends' picture size: `MachineScreen`, `CrtEffect`, `DesktopLauncher` and the web page's JavaScript use the TRIADA-85's 240 × 168 (see Limitations).

## Build it yourself from scratch

1. **Collect the numbers.** Go through the spec and list every number that a different model could change: region starts and sizes, the RAM addresses the hardware reads, timing, screen, voices, devices, ROM images. Leave out what defines the family: word size, instructions, pixel packing, register numbers.
2. **Make the record.** Write an immutable type with those fields. Validate in the constructor: regions inside the word range, no overlaps, whatever limits your hardware code relies on (here: 8 × 8 tiles, 1 to 3 voices). Copy any collections so nobody can change them later.
3. **Make the first model** and write a test that compares it, field by field, with the spec's numbers. Write the numbers in the test as literals, not by reading them back from the constants, or the test proves nothing.
4. **Turn the old constants into derived ones**, `RAM_END = TRIADA_85.ramEnd()`. Run all tests: they must pass unchanged. Nothing has moved yet; you have only changed where the numbers come from.
5. **Give each part a profile, from the bottom up**: the bus first (copy the bounds into `final` fields; don't call methods in the hot path), then the CPU's reset, then the devices, then the machine. Keep the old no-argument constructors, which build the first model. Run the tests after each part.
6. **Plug in only the listed devices.** An unplugged slot must read 0 and ignore writes, which the bus already does.
7. **Test a made-up model.** Change as many numbers as you can at once: smaller RAM, moved video memory, different timing, fewer voices, a missing device. Check that each part follows. This test is what proves nothing is still hard-coded; if one assertion fails, you have found a forgotten constant.
8. **The assembler.** Build its built-in symbols from the profile. Then go through your ROM source for literal machine numbers and replace them with built-ins or expressions of them. Before that, **save the assembled ROM and every program image**, and afterwards compare them word for word. They must be identical.
9. **Choose the model at startup**: a `--model` option in the CLI, the first model as the default, and a helpful error for unknown names.
10. **What tends to go wrong:**
    - an array still sized by the old constant (the video chip's buffers);
    - an index that assumes RAM starts at 0;
    - a test that reads its expected value from the very constant it checks;
    - new built-in names that clash with labels already used in someone's source. Search for them first.

## How we know it works

Run everything:

```bash
./gradlew build
```

All 710 tests pass: the 691 from before M16, unchanged, and 19 new ones.

The new tests:
- [`MachineProfilesTest`](../../triada-core/src/test/java/org/triada/core/MachineProfilesTest.java) checks every TRIADA-85 number against SPEC §2.2, §2.3, §2.5, §4, §6 and §7, finding models by name, and the rejection of overlapping regions.
- [`ModifiedProfileTest`](../../triada-core/src/test/java/org/triada/core/ModifiedProfileTest.java) builds "TEST-SMALL": RAM of 19,683 words, video memory at 10,000 / 14,000 / 14,600, a 120 × 96 screen with border 4, 1,000 cycles per frame at 50 fps, one voice, no triskette. It checks:
  - the RAM ends at 19,682;
  - a program's SP is 19,683;
  - a frame of NOPs uses exactly 1,000 cycles;
  - the picture is 128 × 104 and drawn from the moved tile map;
  - the sound chip makes 882 samples per frame and has no second voice;
  - the triskette registers are empty slots.
- [`ProfileSymbolsTest`](../../triada-assembler/src/test/java/org/triada/assembler/ProfileSymbolsTest.java) checks that the built-ins and the default origin follow the profile, and that the TRIADA-85 is the default.
- [`ModelOptionTest`](../../triada-cli/src/test/java/org/triada/cli/ModelOptionTest.java) checks that `--model triada-85` works for `tasm` and `run`, and that `--model triada-88` is a usage error.

**Nothing changed.** Before touching any code we saved the generated `RomData.java` and the assembled `.tri` image of all nine programs in `programs/`. After the change:
- the ROM and extension ROM words are identical (only the three symbol names that became built-ins left the ROM's symbol table);
- all nine program images are byte-identical (`cmp`).

Trying the option yourself:

```bash
triada run programs/hello.tasm --model triada-85
```

This prints `HELLO, WORLD` and exits with 0, as before. With `--model triada-88` it prints `triada: --model: no such model 'triada-88' (known: triada-85)`, then the usage text, and exits with 2.

**The web build** compiles (`./gradlew build :triada-web:gdx_teavm_web_js_build`), and the page loads locally with no errors in the console. We could not look at the picture: the browser pane was hidden, and a hidden page draws no frames. The deployed page is checked after the push (see Problems).

## Problems we hit and how we solved them

- **A shell heredoc broke** on the Python scripts we used for the larger edits (a quote inside the text). We wrote the scripts as files instead.
- **The comparison first said "DIFFERENT" for every program.** The shell's `java` was an old JDK that couldn't even start the CLI, so nothing had been assembled. With `JAVA_HOME` set to the project's JDK 25, every image compared identical.
- **Built-ins can't be redefined**, so the ROM's own `STACK_TOP`, `PROGRAM_START` and `MAP_CELLS` had to go once they became built-ins. That changed the ROM's symbol table, not its words. We checked that no code looks those names up through `Rom.address`.
- **A record's `equals`** is generated through a JVM feature (`invokedynamic`) that a JavaScript compiler may handle differently. The built-in table's cache compares the profile by identity (`==`) instead, which is all it needs.
- **A hidden browser pane draws nothing**, so the local web check could only confirm that the page loads cleanly.

## Limitations and what comes next

- **One model.** Only the TRIADA-85 exists. The profile is ready for more, but nobody has designed a TRIADA-88 yet.
- **The frontends assume the TRIADA-85's screen.** `MachineScreen`, `CrtEffect`, the desktop window size and the web page's JavaScript use 240 × 168. A model with another screen size needs `MachineScreen` to take its size from `machine.video().frameWidth()` and `frameHeight()`, and the page to read it from the app. That is a small change, but it is left until a second model exists.
- **The ROM is the TRIADA-85's.** `Rom.newMachine(profile)` loads the same ROM into any model. A model with a different memory map would need its own ROM assembled for its profile (`RomGenerator` already assembles for an explicit profile).
- **Some ROM numbers are layout choices, not hardware.** TRIBASIC's control stack at 45,000, its input buffer at 50,000 and the system variables stay as they are. They belong to the ROM, not to the machine.
- **New built-in names.** A user's own source that defines `STACK_TOP` (or one of the other six) now gets "is a built-in symbol and can't be redefined". No source in this repository does.

**Next:** TRIADA-NET, starting at M17. The prior-art page already says where its ideas come from.

## New words

- **Machine profile**: the list of numbers and parts that makes one computer model what it is. *In TRIADA:* `MachineProfile`; the TRIADA-85's is `MachineProfiles.TRIADA_85` (M16).
- **Record (Java)**: a class that only holds values, cannot be changed after it is made, and gets `equals` and a constructor written for it by the compiler.
- **Immutable**: cannot be changed after it is made.
- **Prior art**: work that came before yours and did something similar.
- **Fantasy console**: a computer that exists only as software, with limits chosen on purpose.
- **Hard-coded**: a value written straight into the code in many places, instead of read from one place.
