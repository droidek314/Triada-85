# Milestone 13 — The triskette storage device

## In one paragraph
Until now, everything you typed into the TRIADA-85 was lost when you switched it off. This milestone gives the machine a place to keep things: the **triskette drive**. A triskette is the TRIADA's floppy disk, a flat list of named files. A program can save a block of memory as a file, load it back, list the files and delete one. On the desktop the "triskette" is a folder on your PC; on the web page it lives in your browser. The released games can be loaded from it, and files can be moved between the triskette and your computer in both directions. This is the ground for TRIBASIC's `SAVE` and `LOAD`, which come in M15.

## What already existed
CLAUDE.md asks for this list before any code (Phase 2, step 3):
- **Import, one way only.** The desktop's `--load FILE` and drag-and-drop, the web page's drag-and-drop and `?program=NAME`, and `triada run FILE` all accept `.tri` (and `.tasm`) files. Each puts the program straight into memory and runs it. None of them stored anything.
- **Export:** none. The nearest thing was `triada tasm -o`, which writes `.tri` files on the PC.
- **Paste-typing:** none, apart from the typing helpers inside the tests (`GameHarness`, `ManualTest`).
- **The `.tri` file format** (`TriImage.toBytes()` / `TriImage.fromBytes()`) already existed. It is now the triskette's file format too.

What changed is listed in [D-047](../DECISIONS.md#d-047--import-and-export-the-existing-drop-becomes-import-the-web-page-gains-an-export---milestone-m13-2026-11-08): the existing drop now also stores the file, and the web page gains the missing export.

## What was built
- **The triskette drive** on I/O registers 23–27 (SPEC §5.1): `TrisketteDevice`, with five commands (SAVE, LOAD, DIR, DELETE, LOAD-HOME) and five status codes.
- **A storage interface**, `TriskettePort`, with three implementations:
  - `MemoryTriskette` (in `triada-core`): the default, and the tests' triskette;
  - `FolderTriskette` (in `triada-cli`): a folder of `.tri` files, for `triada run --disk DIR` and the desktop's `triskettes/` folder (`--disk DIR` to change it);
  - `WebTriskette` (in `triada-web`): the browser's local storage.
- **Four ROM routines** in the next jump-table entries: `DISKSAVE`, `DISKLOAD`, `DISKDIR`, `DISKDEL` ([`rom/disk.tasm`](../../rom/disk.tasm), SPEC §8.5).
- **Import and export:** a dropped file is now also stored on the triskette. The web page has a "Triskette" panel with the file list (click a name to export it) and an "Import a file…" button.
- **The register and routine names** `TS_CMD` … `TS_STATUS` and `DISKSAVE` … `DISKDEL` are built into the assembler.
- **SPEC 1.1 merged in** from the Phase 2 task file, including TRIBASIC's specification for M14–M15 (D-044).
- **23 new tests** (563 in all).

## Why it was built this way

**The machine doesn't know where files really live.** The device talks to a small interface with four methods: list, read, write, delete. The desktop plugs in a folder, the web page plugs in browser storage, and the tests plug in a map in memory. The core of the emulator never touches a file, so it still compiles for the browser ([D-045](../DECISIONS.md#d-045--the-triskette-device-an-interface-in-the-core-and-the-cases-spec-51-left-open---milestone-m13-2026-11-08)).

**One folder implementation for two programs.** The command-line tool and the desktop both need "a folder of `.tri` files". The code lives once, in `triada-cli`, next to the other code that reads files on the PC, and the desktop uses it from there ([D-046](../DECISIONS.md#d-046--triskettes-on-the-pc-a-folder-of-tri-files-in-triada-cli-shared-with-the-desktop---milestone-m13-2026-11-08)).

**Extend the import that exists; add only the missing export.** People already drop files onto the window. Now a dropped file is also kept on the triskette. Export was missing entirely, so the web page got it. On the desktop the folder is the export: it is a normal folder you can open ([D-047](../DECISIONS.md#d-047--import-and-export-the-existing-drop-becomes-import-the-web-page-gains-an-export---milestone-m13-2026-11-08)).

**The spec's open cases, decided simply.** What if a program asks for a file list but gives too little room? Nothing is written, and the drive says how much room it needs. What if the program asks for an unknown command? Status −4, "I/O error". All such cases are in SPEC §5.1 and D-045.

## How it works — the plain-language version

### A drive with a few "post boxes"
The CPU talks to the drive through five numbered memory cells, the I/O registers 23–27. Think of them as the slots on the front of a strange 1985 disk drive:

```
   TS_NAME   "where is the file's name written?"     (an address)
   TS_ADDR   "where in memory is the data?"           (an address)
   TS_LEN    "how many words?"                        (a number)
   TS_CMD    "do it!"  1 save, 2 load, 3 list, 4 delete, 5 load-home
   TS_STATUS "how did it go?"  0 OK, -1 not found, -2 no space,
                                    -3 bad name, -4 I/O error
```

A program fills in the first three, writes a command number into TS_CMD, and reads the answer from TS_STATUS. There is no waiting: the command is finished the moment it is given.

### A worked example: saving three words
Say memory cells 2000, 2001 and 2002 hold 10, −20 and 30, and the name `NOTES.TB` is written at address 1000, one letter per cell, ending with a 0.

1. TS_NAME ← 1000, TS_ADDR ← 2000, TS_LEN ← 3.
2. TS_CMD ← 1 (save).
3. The drive reads the name `NOTES.TB` and the three words, and stores a file "NOTES.TB, from address 2000: 10, −20, 30".
4. TS_STATUS reads 0: OK.

Loading it back with TS_ADDR ← 5000 and TS_CMD ← 2 puts 10, −20, 30 into cells 5000–5002 and sets TS_LEN to 3. Command 5, "load-home", ignores TS_ADDR and loads to 2000, where the file came from. That is what a game needs: it must sit where it was built to run.

### File names
Names are 1 to 12 characters: letters, digits and at most one dot, like `GAME.TRI`. Small letters become capitals. By custom, `.TB` is a TRIBASIC program and `.TRI` is machine code. A file you bring in from your PC gets a name made from its own: `oblaka.tasm` becomes `OBLAKA.TRI`, and `trizmeyka.tasm`, one letter too long, becomes `TRIZMEYK.TRI`.

### Where the files really are

| Where you run it | The triskette is | How files get in and out |
|---|---|---|
| Desktop | the folder `triskettes/` (or `--disk DIR`) | copy `.tri` files in and out with your file manager, or drop a file on the window |
| Web page | your browser's storage on this computer | drop a file or click "Import a file…"; click a file's name in the "Triskette" panel to save it on your computer |
| `triada run` | `--disk DIR`, or an empty one in memory | the folder, as on the desktop |

Every file is stored in the same `.tri` format the assembler writes, so a file exported from the web page can be copied into the desktop's folder, and the other way round.

### Step by step: getting a program in and out

**On the web page:**
1. To put a program on the triskette, drag its `.tri` or `.tasm` file onto the page, or open "Triskette" under the screen and click "Import a file…". Machine code also starts at once, as before. A `.TB` file is only stored.
2. To see what is on it, open "Triskette": every file is listed by name.
3. To take a file out, click its name. The browser saves it in your downloads folder.
4. The files stay in this browser on this computer, even after you close the page. Clearing the browser's site data clears the triskette too, so export what you want to keep.

**On the desktop:**
1. Start the desktop. It uses the folder `triskettes/` in the folder you started it from; `--disk DIR` picks another one.
2. To put a program on the triskette, copy its `.tri` file into that folder with a valid name (for example `GAME.TRI`), or drop it on the window, which stores it there as well.
3. To take a file out, copy it out of the folder. Everything a program saves appears there as a normal file.

## How it works — the programmer's version

Read in this order.

### 1. The interface and the names
[`TriskettePort.java`](../../triada-core/src/main/java/org/triada/core/triskette/TriskettePort.java) has `names()`, `read()`, `write()` and `delete()`. Every file is a `TriImage`, and every name a port sees is already valid and upper case. A port that fails throws [`TrisketteException`](../../triada-core/src/main/java/org/triada/core/triskette/TrisketteException.java) with the status the program should see (`TrisketteException.status()`: −2 or −4).

[`TrisketteNames.java`](../../triada-core/src/main/java/org/triada/core/triskette/TrisketteNames.java):
- `TrisketteNames.normalize()` checks a name and returns it in upper case, or null.
- `TrisketteNames.forHostFile()` turns a PC file name into a triskette name for imports.

[`MemoryTriskette.java`](../../triada-core/src/main/java/org/triada/core/triskette/MemoryTriskette.java) keeps files in a sorted map. Its optional size limit (in words) is how the tests produce "no space".

### 2. The device
[`TrisketteDevice.java`](../../triada-core/src/main/java/org/triada/core/triskette/TrisketteDevice.java) is an `IoDevice` like the others. `Machine` attaches it to registers 23–27 and clears its registers in `powerOn()`; `Machine.triskette()` gives the host access, and `TrisketteDevice.setPort()` puts in a different triskette.

Writing TS_CMD runs one command:

```java
case CMD_SAVE:
    return save();
case CMD_LOAD:
    return load(false);
case CMD_LOAD_HOME:
    return load(true);
```

- `save()` reads the name (`fileName()`, at most 12 characters plus the closing 0), checks that the whole block is inside RAM, copies the words with `Bus.peek` (no side effects, even for I/O addresses), and calls `TriskettePort.write`.
- `load()` reads the file, sets TS_ADDR to the file's own address for load-home, sets TS_LEN, and copies the words with `copyIntoRam()`, which skips words that fall outside RAM and reports −4 if any did.
- `dir()` counts the words needed first (each name plus its 0, plus one closing 0). If that is more than TS_LEN, it writes nothing and reports −2 with the count in TS_LEN.
- Every `TrisketteException` becomes its status, and any other exception (for example a broken `.tri` file on the host) becomes −4. A program never sees a Java crash.

[`IoRegisters.java`](../../triada-core/src/main/java/org/triada/core/io/IoRegisters.java) has the five new names, so the assembler knows `TS_CMD` … `TS_STATUS` as built-in symbols (`BuiltinSymbols`), and the debugger shows them by name.

### 3. The ROM routines
[`rom/disk.tasm`](../../rom/disk.tasm) has four short routines behind new jump-table entries in [`rom/rom.tasm`](../../rom/rom.tasm). `DISKLOAD` shows the trick for "load home":

```
diskload: PUSH R3
        LDI R3, IO_BASE
        ST R0, [R3+TS_NAME]
        ST R1, [R3+TS_ADDR]
        LDI R0, 2                       ; LOAD to R1
        MOV R1, R1
        JGE dl_go
        LDI R0, 5                       ; LOAD-HOME
dl_go:  ST R0, [R3+TS_CMD]
```

- R3 is needed to point at the registers, so it is saved first (the convention says R3–R8 survive a call).
- `MOV R1, R1` sets the sign flag from R1. `JGE` jumps if it isn't negative: then it is a normal LOAD to that address.
- A negative R1 can never be a RAM address, so it means "load to the file's own address" (command 5).
- All four routines end at `disk_status`, which loads TS_STATUS into R0 and sets the flag from it again after `POP R3`. A caller can test for an error with `JNZ` straight after `JSR`.

`BuiltinSymbols` knows the four new names, so programs simply write `JSR DISKLOAD`.

### 4. The hosts
[`FolderTriskette.java`](../../triada-cli/src/main/java/org/triada/cli/FolderTriskette.java) maps a folder: `files()` lists it and keeps only files with valid names, matched without regard to case. `write()` creates the folder when needed, checks the free space (`FileStore.getUsableSpace`) and writes `TriImage.toBytes()`.
- [`RunCommand.java`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java) plugs it in for `--disk DIR`.
- [`DesktopLauncher.java`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java) always plugs it in, for `LaunchOptions.disk()` (default `triskettes`).
- [`TriadaApp.java`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java) → `TriadaApp.insertTriskette()` stores a dropped file under `TrisketteNames.forHostFile()` before running it, and only stores a `.TB`.

[`WebTriskette.java`](../../triada-web/src/main/java/org/triada/web/WebTriskette.java) uses four one-line JavaScript methods. Each one wraps local storage in `try { … } catch (e) { … }` and turns a refusal into a code:

```js
try { localStorage.setItem(k, v); return 0; }
catch (e) { return e && (e.name === 'QuotaExceededError' || e.code === 22) ? -2 : -4; }
```

The key is `triada85.triskette.` + name, so the TRIADA's entries never mix with anything else on the same web address. The value is the `.tri` file in Base64 (`java.util.Base64`), because local storage only holds text. `WebApp.insert()` stores dropped and imported files. The page ([`index.html`](../../triada-web/src/main/webapp/index.html)) reads the same keys for its file list (`triadaFilesChanged`) and builds a download from them (`exportFile`).

## Build it yourself from scratch
1. **Write the interface first:** list, read, write, delete, with files as "load address + words". Add an in-memory implementation with an optional size limit.
2. **Write the name rules** and test them on the edge cases: 12 and 13 characters, two dots, lower case, characters like `-`, `Ä` and space.
3. **Write the device as an I/O device** on registers 23–27. Keep the registers as plain fields; only a write to TS_CMD does something. Decide every open case before coding: what a too-small DIR buffer does, what a SAVE outside RAM does, what an unknown command does. Write those decisions into the spec.
4. **Never let a host problem crash the machine:** catch everything around a command and turn it into a status code.
5. **Test through the registers**, the way a program would: write a name into RAM, set the registers, write TS_CMD, read TS_STATUS. One test per command and per status.
6. **Add the ROM routines** at the next jump-table addresses and the names to the assembler's built-in symbols. Test them from a small TRIADA program that also checks that R3–R6 survive every call.
7. **Write the folder backend** and test the host round trip: a program saves through the CLI with `--disk`, the test opens the file on disk, and a second machine reads it back unchanged.
8. **Write the browser backend** around `localStorage`, every access in try/catch, and give the page an import (file picker) and an export (download a `Blob`).

What tends to go wrong:
- Test values outside the word range (±265,720): the bus rejects them, and the error points at the test, not the device.
- A name with no 0 at the end: limit how far you read.
- Local storage throws in private windows or when site data is blocked; without try/catch, the whole page stops.

## How we know it works

```bash
./gradlew build
```

You should see `BUILD SUCCESSFUL`: 563 tests, no failures (core 264, assembler 98, ROM 59, CLI 103, app 10, desktop 29). The new ones:
- **`TrisketteDeviceTest`** (14 tests, `triada-core`): every command (SAVE, LOAD, LOAD-HOME, DIR, DELETE) and every status:
  - −1: a missing file, for LOAD, LOAD-HOME and DELETE;
  - −2: a full triskette, and a DIR buffer that is too small;
  - −3: six bad names, plus a name without its closing 0;
  - −4: SAVE outside RAM, a negative length, an unknown command, LOAD clipped at the end of RAM, and a failing host.

  It also checks the registers and power-on, and the name rules, including `trizmeyka.tasm` → `TRIZMEYK.TRI`.
- **`DiskRoutinesTest`** (3 tests, `triada-rom`): the four ROM routines called from a TRIADA program. The tests check their results, that R3–R6 survive every call, and that the sign flag carries the status.
- **`TrisketteTest`** (5 tests, `triada-cli`):
  - the host round trip through `triada run --disk`;
  - the in-memory default;
  - the folder's rules (case, invalid names ignored, a missing folder);
  - a broken file on the host giving −4;
  - **TROIKA, a released game, stored on a triskette folder, loaded by a small loader program with `DISKLOAD` and run**: no fault, and its title screen appears.
- **`LaunchOptionsTest`** (+1, `triada-desktop`): `--disk`, its default and its error message.
- `InputDeviceTest` and `KernelTest` now also check the five new register names and the four new jump-table entries.

Checked by hand in the web version (the browser pane, `./gradlew :triada-web:gdx_teavm_web_js_run`):
- `oblaka.tasm` handed to the page as if dropped was stored as `OBLAKA.TRI` in browser storage and started.
- After a page reload, a small dropped loader program found `OBLAKA.TRI` with `DISKLOAD` and started the game from it: browser storage works for writing, listing and reading, and keeps files across reloads.
- The "Triskette" panel listed both files. The bytes the export link would download were a valid `.tri` file (header `TRIADA85`, load address 30000, 38 words). The download itself wasn't clicked, so as not to put a file on the user's computer.

## Problems we hit and how we solved them
- **A test with an impossible word.** The first save test used −300,000, outside the word range, and the bus refused it. The test data was wrong, not the device; −240,000 fits.
- **No "home" argument left.** "Load to the file's own address" needed a fourth argument, but the calling convention has only R0–R2 for arguments. A negative address in R1 does the job, because no RAM address is negative.
- **Where to put the folder code.** The core must not touch files, and `triada-app` is compiled for the browser. The folder code went into `triada-cli`, and the desktop depends on it (D-046).
- **Milestone numbers.** The task file numbered these milestones from M12, which was already the web version. They became M13–M15 (D-044).

## Limitations and what comes next
- There is no user command for the triskette yet. The monitor has none, and TRIBASIC's `SAVE`, `LOAD`, `FILES`, `ERASE`, `BSAVE`, `BLOAD` and `BRUN` come in M15. Until then only programs (through the routines or the registers) and the host side (folder, import, export) use it.
- The web triskette belongs to one browser on one computer. Its size is the browser's limit for one site, usually around 5 MB, which is enough for several hundred programs the size of OBLAKA.
- Long PC file names are shortened on import (`TRIZMEYK.TRI`). The user can't choose the name yet.
- The web's import and export weren't clicked by hand in this milestone (the file picker and the download); the code paths behind them were exercised as described above.
- **Next, M14:** the TRIBASIC interpreter core, in the extension ROM, and the machine booting into it.

## New words
- **Base64:** a way to write any bytes as plain letters, digits, `+` and `/`, so they can be kept where only text is allowed.
- **Host round trip:** saving from the emulated machine, finding the file on the real computer, and loading it back unchanged.
- **Local storage:** a small store of text that a browser keeps for each website, even after the page is closed.
- **Status code:** a number a device or routine returns to say how an action went; here 0 for OK and −1 … −4 for problems.
- **Triskette:** the TRIADA-85's floppy disk: a flat list of named files, with no tracks or folders.
- **TriskettePort:** the interface between the triskette drive and wherever its files really live.
