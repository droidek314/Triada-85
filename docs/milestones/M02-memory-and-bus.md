# Milestone 02 — Memory and bus

## In one paragraph
A calculator forgets everything the moment it has answered. A computer needs a place to keep numbers: its program, its pictures, its score. This milestone gives TRIADA its memory. Picture a very long street of numbered mailboxes, with house number 0 in the middle and negative numbers to the left. Some stretches of the street are ordinary mailboxes you can read and fill (RAM). One stretch is locked display cases you can only look into (ROM). A short stretch isn't mailboxes at all but doorbells and windows to the outside world: the joystick, the keyboard, later the screen and the speaker (I/O registers). The rest of the street is empty lots. We also built the postman, the *bus*, who takes every "read box 500" or "put 7 in box −20,403" and delivers it to the right place.

## What was built
- **`MemoryMap`**: every address from the spec in one place: where RAM, ROM, I/O and the empty stretches begin and end, and where programs, tiles, the tile map, sprites and the stack live inside RAM. `MemoryMap.regionOf()` tells you which region any address belongs to.
- **`Bus`**: the postman. `Bus.read()` and `Bus.write()` route every address to RAM (59,049 words), ROM (19,683 words, read-only for the CPU), an I/O device, or nowhere. There are also host-only tools: `Bus.peek()` (look without side effects), `Bus.loadRom()`, `Bus.loadRam()`, `Bus.clearRam()` and `Bus.attach()`.
- **`IoDevice`**: the plug every device fits into: `read`, `peek` and `write` of a register number.
- **`IoRegisters`**: the numbers and names of all 23 I/O registers from SPEC §5.
- **`InputDevice`**: the first real device. It covers the joystick (JOY_X, JOY_Y), the three buttons and the **KEY queue**, where reading a key removes it.
- **65 new tests**: the first and last address of every region and the address beyond it, every address classified, every RAM cell independent, I/O routing at both ends, and the KEY queue's behaviour.

The new code is in two packages of `triada-core`: `org.triada.core.memory` and `org.triada.core.io`.

## Why it was built this way

**One bus, one path.** Every memory access the CPU makes will go through `Bus.read` or `Bus.write`. The CPU (M3) never needs to know where RAM ends or which device owns which register, and adding a device never touches the CPU. The alternative, letting the CPU look into the RAM array directly, would be a little faster, but every special case (ROM is read-only, KEY removes on read) would then leak into the CPU.

**RAM and ROM are plain arrays of `int`.** This follows from [D-004](../DECISIONS.md#d-004--words-are-plain-ints-operations-are-static-methods---milestone-m01-2026-11-07): a word is an `int`, so a memory is an `int[]`. RAM is 59,049 ints, about 230 KB, which is nothing for a modern PC.

**Devices see register numbers, and looking is separate from reading** ([D-008](../DECISIONS.md#d-008--io-devices-register-numbers-k-and-separate-read--peek---milestone-m02-2026-11-07)). A device is told "register 6 was read", not "address −20,406 was read", so device code reads like the spec's table. The debugger needs to *look* at KEY without stealing the key, which is why `peek` exists.

**The KEY queue holds 27 keys** ([D-007](../DECISIONS.md#d-007--the-key-queue-holds-27-keys-and-drops-new-ones-when-full---milestone-m02-2026-11-07)). The spec didn't say. Like the small keyboard buffers of real 1980s machines, a full queue drops new keys.

**Input arrives now, wiring comes later.** CLAUDE.md puts keyboard and joystick handling in M7, but asks M2 to support KEY's read-removes behaviour. The cleanest way to support it is to build the device that owns it. M7 only has to connect libGDX's key events to `InputDevice.pushKey()` and the joystick setters.

**Mistakes in the host's code fail loudly, mistakes in TRIADA programs never do.** A TRIADA program can only produce valid addresses and values: the CPU wraps them. So if `Bus` ever sees address 300,000 or value 999,999, our Java code has a bug, and the bus throws an exception at once instead of quietly corrupting memory. A TRIADA program writing to ROM or an empty address, on the other hand, is legal and simply has no effect, exactly as SPEC §2.2 says.

## How it works — the plain-language version

### The street of mailboxes
Every *address* (a mailbox number) is a word, so the street runs from −265,720 to +265,720. Here is what is where (SPEC §2.2):

```
 address:  −265,720 … −20,413 | −20,412 … −19,684 | −19,683 … −1 | 0 … 59,048 | 59,049 … 265,720
           -------------------+-------------------+--------------+------------+------------------
 region:   empty lots         | I/O registers     | ROM          | RAM        | empty lots
 size:     245,308            | 729 = 3^6         | 19,683 = 3^9 | 59,049=3^10| 206,672
 reading:  always 0           | asks a device     | the kernel   | the value  | always 0
 writing:  nothing happens    | tells a device    | nothing      | stores it  | nothing happens
```

- **RAM** (*random-access memory*) is the ordinary memory, where programs and their data live. At power-on every box holds 0.
- **ROM** (*read-only memory*) will hold the machine's built-in software, the kernel and monitor (M8). The CPU can read it but never change it, so a crashing program can't damage the machine's own software. Address −19,683, the very first ROM word, is where the CPU starts after power-on.
- **I/O registers** (input/output) aren't memory at all. Each one is a doorbell or a window to a piece of hardware. Reading address −20,411 (JOY_X) asks the joystick "left, centre or right?"; the answer is −1, 0 or +1. Writing to the BORDER register (M6) will change the border's color.
- **Empty lots** (*unmapped* addresses) are deliberately left free. The lore says the top part is reserved for plug-in *cartridges*. Reading gives 0, and writing does nothing.

Why is the I/O just below the ROM? It makes every register reachable from one starting point: put −20,412 in a register, and each I/O register is a small offset (0 to 22) away from it.

### Inside RAM: a plan everyone follows (SPEC §2.3)
RAM itself is divided by agreement:

```
 0 … 242          system variables    (the ROM's own notes)
 243 … 39,999     your program        (programs load at 243 = 3^5)
 40,000 … 43,887  tile patterns       (the little 8×8 pictures the screen is made of)
 44,000 … 44,485  tile map            (which picture goes in which screen square)
 44,600 … 44,707  sprite table        (the moving figures)
 45,000 … 59,048  free / stack        (the stack starts at 59,049 and grows down)
```

The *stack* is a pile of notes the processor keeps, for example "where to come back to after this subroutine". It grows downward from the top of RAM, so it and your program grow toward each other and only collide if memory is truly full. The video chip (M6) will read the tile and sprite areas directly; that's why their addresses are fixed.

### The postman (the bus)
Every request carries an address. The bus checks the address against the four regions, in order of how common they are (RAM first), and hands the request on. For example, `read −20,406`:

1. Is it between 0 and 59,048 (RAM)? No.
2. Between −19,683 and −1 (ROM)? No.
3. Between −20,412 and −19,684 (I/O)? Yes. It is register −20,406 − (−20,412) = **6**, which is KEY.
4. Which device is plugged into register 6? The input device. Ask it.

### The keyboard queue
Keys you press wait in line, first in, first out, like people at a ticket counter. Each time a program reads KEY, the person at the front is served and leaves the line. When the line is empty, KEY reads 0, which means "no key".

```
  you type:  H  I            queue: [H, I]
  read KEY → 72 ('H')        queue: [I]
  read KEY → 73 ('I')        queue: []
  read KEY → 0               queue: []   (nothing waiting)
```

The line has room for 27 keys. If a program ignores the keyboard and you keep typing, key number 28 is lost, just as on real home computers of the time.

The debugger must be able to *look* at KEY without serving anyone. That's the difference between *reading* (serve the front of the line) and *peeking* (glance at the front of the line).

## How it works — the programmer's version

Study the classes in this order.

### 1. `MemoryMap`: constants and `regionOf`
[`MemoryMap.java`](../../triada-core/src/main/java/org/triada/core/memory/MemoryMap.java). Each region is defined by its size and computed from its neighbour, so the numbers in the spec table appear as results, not as hand-typed copies:

```java
public static final int ROM_START = -ROM_SIZE;                // −19,683
public static final int IO_BASE   = ROM_START - IO_SIZE;      // −20,412
public static final int IO_END    = ROM_START - 1;            // −19,684
```
- ROM ends at −1 and is 3^9 long, so it starts at −19,683.
- I/O sits directly below ROM, 729 registers long.

`MemoryMap.Region` is an *enum* (a type with a fixed list of values) with `UNMAPPED`, `IO`, `ROM` and `RAM`. `MemoryMap.regionOf()` checks the ranges and throws for an address outside the word range. The RAM layout constants (`PROGRAM_START`, `TILES`, `MAP`, `SPRITES`, `FREE_START`, `STACK_TOP`) will also become built-in assembler symbols in M4.

### 2. `IoDevice` and `IoRegisters`
[`IoDevice.java`](../../triada-core/src/main/java/org/triada/core/io/IoDevice.java) is a Java *interface*: a list of methods a class promises to provide, here `read(int k)`, `write(int k, int value)` and `peek(int k)`. `peek` is a `default` method that calls `read`, so only devices with side-effecting reads need to write their own.

[`IoRegisters.java`](../../triada-core/src/main/java/org/triada/core/io/IoRegisters.java) holds `FRAME = 0` … `HOST_EXIT = 22`, and `IoRegisters.nameOf()` for the debugger and the error messages.

### 3. `Bus`: the router
[`Bus.java`](../../triada-core/src/main/java/org/triada/core/memory/Bus.java). The state is three arrays: `ram` (`int[59049]`), `rom` (`int[19683]`) and `devices` (`IoDevice[729]`, `null` where nothing is attached).

`Bus.read()`:
```java
if (address >= RAM_START && address <= RAM_END) return ram[address];
if (address >= ROM_START && address <= ROM_END) return rom[address - ROM_START];
if (address >= IO_BASE && address <= IO_END) {
    int k = address - IO_BASE;
    IoDevice device = devices[k];
    return device == null ? 0 : device.read(k);
}
MemoryMap.checkAddress(address);   // throws if not a word at all
return 0;                          // unmapped
```
- Line 1: RAM addresses equal their array index, because RAM starts at 0.
- Line 2: ROM addresses are negative, so subtract `ROM_START` to get the index 0 … 19,682.
- Lines 3–6: I/O addresses become the register number `k`, then the attached device answers. An empty socket answers 0.
- The last two lines: anything else is unmapped. The validity check sits at the end, so the common cases pay nothing for it.

`Bus.write()` first rejects non-word values (a host bug), then stores to RAM, forwards to a device, or ignores the write. `Bus.peek()` is `read` with `device.peek(k)` in the I/O case.

The host-side methods:
- `Bus.attach(firstK, count, device)` checks the whole range *before* changing anything, so a failed attach leaves the bus untouched. Its error message names the register: "I/O register 18 (V3_WAVE) already has a device attached".
- `Bus.loadRom(image)` replaces the whole ROM; words beyond the image become 0.
- `Bus.loadRam(address, words)` copies a block such as a loaded program, after checking that it fits.
- `Bus.clearRam()` restores the power-on state of RAM.
- `Bus.deviceAt(k)` reports which device sits on a register.

### 4. `InputDevice`: joystick, buttons, KEY queue
[`InputDevice.java`](../../triada-core/src/main/java/org/triada/core/io/InputDevice.java). It occupies registers `FIRST_REGISTER` = 1 (JOY_X) through 6 (KEY), so it is plugged in with `bus.attach(InputDevice.FIRST_REGISTER, InputDevice.REGISTER_COUNT, input)`.

The KEY queue is a *ring buffer*: a fixed array of 27 slots plus two numbers, `head` (where the oldest key is) and `size` (how many keys wait). Adding a key writes slot `(head + size) % 27`. Removing one reads slot `head` and moves `head` one step forward, wrapping from 26 back to 0. Nothing is ever allocated, and nothing is ever shifted.

```java
int code = keys[head];
head = (head + 1) % KEY_QUEUE_CAPACITY;
size--;
return code;
```
- These lines are the "serve the front of the line" step in `InputDevice.read()` for k = KEY.
- `InputDevice.peek()` returns `keys[head]` without the last three lines.

The frontend side is `InputDevice.setJoyX()`, `setJoyY()` (each must be −1, 0 or +1), `setButtonA()`, `setButtonB()`, `setButtonC()`, `InputDevice.pushKey()` (returns `false` if the key was dropped), `InputDevice.queuedKeys()` and `InputDevice.clearKeys()`. CPU writes to these registers are ignored, because the spec marks them read-only.

## Build it yourself from scratch
1. Write down the memory map as constants. Derive each boundary from its neighbour and its size, and then test the results against the spec's numbers typed in literally. If you mistype one, the two sources disagree and the test tells you.
2. Write `regionOf(address)` and test it at every boundary (first, last, and one beyond each). Then test it for all 531,441 addresses against a plain `if` chain using the spec's numbers.
3. Write the bus with two arrays and an array of device slots. **Pitfall:** ROM addresses are negative, so remember `address − ROM_START` when indexing. Forgetting it gives an `ArrayIndexOutOfBoundsException`, or worse, the wrong word.
4. Decide what happens with addresses that aren't words at all. We throw, because the CPU can never produce them.
5. Define the device interface with a separate side-effect-free `peek`. You'll need it the day you build a debugger.
6. Build the KEY queue as a ring buffer. Test "first in, first out", "empty reads 0", "peek doesn't remove", "the 28th key is dropped", and, important for ring buffers, many keys passing through so that `head` wraps around several times.
7. Use a fake "recording" device in the tests to prove that register 0 sits at −20,412 and register 728 at −19,684, and that the neighbouring addresses don't reach it.

## How we know it works

Run all tests:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. `triada-core` now has 115 tests (65 of them new) and `triada-cli` 1.

| Test class | Tests | What it proves |
|---|---|---|
| [`MemoryMapTest`](../../triada-core/src/test/java/org/triada/core/memory/MemoryMapTest.java) | 24 | All constants equal the numbers in SPEC §2.2 and §2.3. `MemoryMapTest.regionBoundaries` covers 20 addresses: the first and last of every region and their neighbours. All 531,441 addresses are classified exactly as the spec's table says. The RAM layout areas don't overlap. |
| [`BusTest`](../../triada-core/src/test/java/org/triada/core/memory/BusTest.java) | 29 | RAM read/write at 0, 1, 243, 59,047, 59,048. Every RAM cell is independent (all 59,049 written and read back). RAM is all zero at power-on. ROM is readable and ignores writes at both ends, and writes just outside it land in I/O and RAM. I/O register 0 and 728 sit at the right addresses, and −20,413 and −19,683 don't reach the device. Unmapped addresses at all four edges read 0. `attach` rejects overlaps and bad ranges without side effects. Invalid addresses and values throw. |
| [`InputDeviceTest`](../../triada-core/src/test/java/org/triada/core/io/InputDeviceTest.java) | 12 | Joystick and button values at the spec's addresses. KEY is first in, first out, reads 0 when empty, and peeking doesn't remove. The queue is full at 27, and 1,000 keys pass through the ring buffer correctly. Writes are ignored. The register numbers and names match the spec table. |

(Parameterized tests count each case separately, which is why `MemoryMapTest` has 24.)

**Mutation check.** We broke the code twice on purpose: the ROM range started one address too late (`>` instead of `>=`), and reading KEY stopped removing the key. 8 tests failed, including `BusTest.romBoundariesAreReadOnly` and `InputDeviceTest.readingKeyRemovesItOldestFirst`. With the code restored, all tests pass.

## Problems we hit and how we solved them
- **Two gaps in the spec**: the KEY queue's size ([D-007](../DECISIONS.md#d-007--the-key-queue-holds-27-keys-and-drops-new-ones-when-full---milestone-m02-2026-11-07)), and how a debugger can look at KEY without changing it ([D-008](../DECISIONS.md#d-008--io-devices-register-numbers-k-and-separate-read--peek---milestone-m02-2026-11-07)). Both were settled with the simplest workable rule.
- **Scope question: does input belong in M2 or M7?** We built the input device now, because KEY's read-removes behaviour is an M2 acceptance criterion, and testing it needs a real queue. M7 still owns the connection to the real keyboard.
- No bugs showed up in the code this time. Everything passed on the first run, which is why the mutation check matters: it shows the tests would have caught real boundary and queue bugs.

## Limitations and what comes next
- Only the input device exists. FRAME, RAND, the scroll and border registers, sound, and the debug registers (DEBUG_CHAR, DEBUG_NUM, HOST_EXIT) read 0 until their devices arrive: the machine and debug port in M3/M5, video in M6, sound in M7.
- There is no direct RAM access for the video chip yet. When M6 needs it, we will measure whether reading through `Bus.peek` is fast enough before exposing the array.
- Nothing is thread-safe. libGDX delivers key events on the same thread that will run the CPU, so this is fine for the desktop frontend.
- **Next, M3:** the CPU, which fetches instructions from this memory through the bus and executes all 27 opcodes.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Address:** the number of a memory cell, like a house number. TRIADA's run from −265,720 to +265,720.
- **Memory map:** the plan of which addresses are RAM, ROM, I/O or empty.
- **RAM / ROM:** memory you can read and write / memory you can only read.
- **I/O register:** an address that is a doorbell to hardware instead of storage.
- **Unmapped:** an address with nothing behind it.
- **Bus:** the path that carries every read and write to the right place.
- **Device:** a piece of hardware attached to I/O registers.
- **Kernel:** the machine's built-in core software, in ROM.
- **Cartridge:** a plug-in module adding memory or software (lore; reserved addresses).
- **Stack / Stack pointer:** a pile of notes that grows downward from the top of RAM / the register remembering where the top of the pile is.
- **System variables:** RAM cells where the ROM keeps its own notes.
- **Tile / Tile map / Sprite:** an 8×8 picture / the grid saying which tile goes where on screen / a picture that moves freely (all M6).
- **Queue (FIFO):** a waiting line, first in, first out.
- **Ring buffer:** a fixed array used as a queue, whose ends chase each other round and round.
- **Side effect:** a change caused by an operation beyond its answer, like reading KEY removing the key.
- **Peek:** looking at an address without any side effect.
- **Interface (Java):** a list of methods a class promises to provide.
- **Enum:** a type with a fixed list of named values.
- **Boundary test:** a test at the exact edges of a range, where off-by-one mistakes hide.
- **Parameterized test:** one test method run once for each row of a table of inputs.
- **Power-on state:** how the machine looks the instant it is switched on.
