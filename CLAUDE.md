# CLAUDE.md — TRIADA-85 Project Instructions

You are building **TRIADA-85**, a fantasy home computer from an alternate 1985 where **ternary** (trits: −1, 0, +1) won instead of binary. You'll build the whole machine in software: ternary math, CPU emulator, assembler, video, sound, a ROM operating system written in the machine's own assembly, a debugger and games. You'll also document every step so that anyone can follow it.

**Read `docs/SPEC.md` fully before writing any code.** It defines the machine exactly. This file defines *how you work*.

---

## 1. Golden rules

1. **The spec is the law.** `docs/SPEC.md` decides behaviour. If you find a gap, a contradiction or a real problem in it, don't silently improvise:
   - choose the simplest reasonable solution,
   - update `docs/SPEC.md`,
   - add an entry to `docs/DECISIONS.md` (see §5.4),
   - mention it in the milestone document.
2. **One milestone at a time, in order.** Don't start milestone N+1 until milestone N is done, meaning code, tests passing and documentation written and checked.
3. **Tests are not optional.** Every behaviour in the spec gets a test. A milestone with failing or missing tests isn't finished.
4. **Documentation is part of the deliverable, not an afterthought.** A milestone without its document is not finished (§5).
5. **Small, readable code beats clever code.** This project is also a teaching project. Name things clearly and add comments that explain *why*, not *what*.
6. **Commit at the end of every milestone** with a message like `M3: CPU core — all 27 opcodes`. Commit smaller steps inside a milestone too, if useful.
7. **Never fake progress.** If something doesn't work, say so in the milestone doc under "Problems" or "Limitations".

---

## 2. Technology

- **Language:** Java, on the newest LTS JDK (21 or later).
- **Build:** Gradle (Kotlin DSL), multi-module, with the Gradle wrapper committed. `./gradlew build` must build and test everything.
- **Frontend:** **libGDX** with the LWJGL3 desktop backend, for the window, pixel display, sound and input. Build the debugger UI with libGDX's scene2d.ui.
- **Tests:** JUnit 5.
- **Image output** (screen dumps, golden images): the JDK's built-in `javax.imageio`. No extra library.
- **No other dependencies** unless clearly justified in `DECISIONS.md`.
- **Java package root:** `org.triada` (e.g. `org.triada.core.cpu.Cpu`).

### Repository layout
```
/CLAUDE.md                 ← this file
/README.md                 ← project front page (you write it; update every milestone)
/settings.gradle.kts, /build.gradle.kts, /gradlew …
/docs/
  SPEC.md                  ← machine specification (source of truth)
  INDEX.md                 ← table of contents for all docs, read-in-order guide
  ARCHITECTURE.md          ← how the parts fit together (kept up to date)
  DECISIONS.md             ← decision log
  GLOSSARY.md              ← every technical word, explained simply
  PROGRESS.md              ← milestone checklist with status
  milestones/
    M00-project-setup.md
    M01-ternary-math.md
    ...
  manual/                  ← in-universe TRIADA-85 user manual (M11)
  NET-SPEC.md              ← TRIADA-NET server and wire protocol specification (Phase 3)
  net/                     ← TRIADA-NET deployment guide and story outline (M18–M19)
/triada-core/              ← ternary math, memory, CPU, video, sound, I/O. Plain Java, NO libGDX.
/triada-assembler/         ← assembler + disassembler library (depends on core)
/triada-rom/               ← builds the ROM from /rom into the emulator
/triada-cli/               ← command-line tool: `tasm` (assemble), `run` (headless run)
/triada-app/               ← libGDX frontend parts shared by desktop and web (M12)
/triada-desktop/           ← libGDX desktop frontend (display, sound, input, debugger UI)
/triada-web/               ← libGDX web frontend, published on GitHub Pages (M12)
/triada-net-core/          ← TRIADA-NET boards, users, menus, messages, files. Pure Java, no sockets (M18)
/triada-net-server/        ← TRIADA-NET server: TCP and WebSocket transports, persistence, admin (M18)
/rom/                      ← ROM source in TRIADA assembly (*.tasm), incl. the extension ROM (TRIBASIC)
/programs/                 ← example programs and games (*.tasm); TRIBASIC examples in programs/basic/
/tests/basic/              ← TRIBASIC test programs: NAME.bas + NAME.expected (M14)
```
Each module uses the standard Gradle layout: `src/main/java`, `src/test/java`, `src/main/resources`. End-to-end tests (assemble and run whole programs, compare output and screens) live in `triada-cli/src/test/java`.

**Important architecture rule:** `triada-core` and `triada-assembler` must be pure Java with **no** libGDX or other UI dependency, so tests, the headless runner and any future web or alternative frontend can all use them. Keep them free of reflection, threads and file I/O wherever reasonable, so a later web build (TeaVM/GWT) can compile them. File loading belongs in the CLI and frontend.

### Implementation hint (performance)
Store a word as a Java `int` in the range ±265,720. Use normal integer arithmetic plus the wrap formula from SPEC §1.3. Java's `%` can return negative values, so use `Math.floorMod`. Convert to trits only for trit-wise operations (MIN, MAX, TMUL, ROT) and for display. Lookup tables are allowed. RAM is a plain `int[]`. **Don't allocate objects in the CPU's per-instruction loop** (no boxing, no `new` per step), to keep the garbage collector quiet. The CPU must run 59,049 instructions per frame at 60 fps comfortably. Measure this (a simple timed benchmark is enough; JMH is optional) and report it in the M3 doc.

---

## 3. Milestones

Each milestone lists **acceptance criteria**. All of them must be true before you move on.

### M0 — Project setup
- Gradle multi-module project created; `./gradlew build` succeeds, including an (empty) test run.
- `./gradlew :triada-desktop:run` opens a blank libGDX window titled "TRIADA-85".
- All `docs/` files created with their initial structure. `PROGRESS.md` lists M0–M11 (Phase 2 later added M12–M15).
- `README.md` explains what the project is in a few paragraphs, for a non-programmer.

### M1 — Ternary math (`triada-core`)
- Types for trit and word, including conversion between integers, trit arrays and the `-0+` string notation (both directions).
- Wrapping, carry and all arithmetic and trit-wise operations needed by the CPU (add, sub, mul, the rounding division of SPEC §3.5, neg, min, max, tmul, rot, shift).
- **An exhaustive test** that converts every one of the 531,441 word values to trits and back.
- Tests for every edge case: maximum and minimum values, wrap in both directions, the division-by-zero rule, the rounding examples from the spec.

### M2 — Memory and bus
- The memory map from SPEC §2.2: RAM, ROM (read-only), I/O region, unmapped.
- I/O registers implemented as a pluggable device interface (video, input, sound and debug will plug in later). KEY's read-removes behaviour is supported.
- Tests for every region boundary (first and last address of each region, ±1).

### M3 — CPU core
- All 27 opcodes, both flags, all JIF conditions, BR3, the fault rule, the cycle budget and `WAIT` (SPEC §3, §6).
- At least one test per opcode, plus flag tests. Tests may build instruction words by hand at this stage.
- A performance measurement (instructions per second), recorded in the doc.

### M4 — Assembler and disassembler
- The full syntax, directives, built-in symbols and pseudo-instructions from SPEC §9.
- Helpful error messages (file, line, column, expected or found), with tests for the error cases.
- `.tri` output and `.lst` listing.
- Disassembler: word(s) → readable text (used later by the debugger).
- Round-trip test: assemble → disassemble → assemble gives identical words.
- From now on, CPU tests should also be written in assembly where that's clearer.

### M5 — Headless runner and first program
- `triada run program.tri [--frames N] [--dump-screen out.png]` (plus `triada tasm source.tasm -o out.tri [--listing out.lst]`), with DEBUG_CHAR, DEBUG_NUM and HOST_EXIT supported (SPEC §5).
- `programs/hello.tasm` prints "HELLO, WORLD" through DEBUG_CHAR and exits with HOST_EXIT 0.
- Integration tests: run programs and check their debug output and exit code.
- At this point the ROM may be a minimal stub. The runner can load a `.tri` and start at its load address.

### M6 — Video chip and frontend display
- The video chip renders exactly as SPEC §4 says: 27 colors, pixel packing, tiles, mirroring, tile map, scroll, sprites in order, transparency, border.
- The renderer lives in `triada-core` and outputs an RGB pixel buffer (`int[]`). The frontend only uploads it to a libGDX `Pixmap`/`Texture` and draws it.
- The libGDX window shows the screen at integer scale with nearest-neighbour filtering (no blur). Golden-image tests: render known memory contents and compare against stored expected images or hashes.
- Demo program `programs/colors.tasm` shows all 27 colors plus a moving sprite.
- **Early web check:** do a short spike (not a full build) to confirm that a current libGDX web backend can compile `triada-core`. Record the result in `DECISIONS.md` so problems are found now, not at the end.

### M7 — Input and sound
- Keyboard → KEY queue. Arrow keys → JOY_X/JOY_Y. Z/X/C → BTN_A/B/C. Remapping is optional.
- Three-voice sound with all three waveforms (SPEC §7). Sample generation lives in `triada-core`. The frontend streams the samples through libGDX's `AudioDevice` (raw PCM) on its own audio thread, without stutter.
- Demo `programs/piano.tasm`: keys play notes, and the joystick changes the waveform.

### M8 — ROM: kernel and monitor (written in TRIADA assembly)
- Font: use a public-domain 8×8 font (for example the public-domain *font8x8* by Daniel Hepper) or design your own. Record the source and licence in `DECISIONS.md`.
- Jump table, all kernel routines, boot sequence, boot chime, boot screen and every monitor command from SPEC §8.
- The ROM is assembled during the build and embedded in the emulator.
- Integration tests: boot the machine headless, type commands into the KEY queue, check the screen contents (tile map) and results.

### M9 — Debugger
- In the desktop frontend (scene2d.ui), toggled with F1: pause/continue, single step, step over JSR, breakpoints, registers and flags (shown in decimal *and* ternary), disassembly around PC, memory viewer, fault display.
- Loading a program: command-line `--load file.tri` and drag-and-drop of a `.tri` file onto the window ("insert triskette").

### M10 — Games (written in TRIADA assembly)
1. **TRIZMEYKA**, a snake game: tiles, joystick, score, sound.
2. **A three-state puzzle game** whose mechanic is built on ternary itself. For example, every tile is −1/0/+1 and moves apply ROT or MIN/MAX rules. Design it yourself and explain the design in the doc.
3. **One ambitious game**: a platformer or a shooter using scrolling and many sprites.
Each game has an integration smoke test (it boots, runs N frames and doesn't fault).

### M11 — Lore and presentation
- `docs/manual/`: an in-universe user manual (styled as a 1985 manual), including a "type-in listing" page from the fictional magazine *Trit & Tryte*.
- Optional CRT shader in the frontend (a libGDX `ShaderProgram`).
- README finished, with screenshots or GIF, a "how to run" section and a link to `docs/INDEX.md`.
- A final retrospective document: what went well, what was hard, what we'd do differently.

### Stretch (only after M11)
- ~~A small high-level language (Pascal- or BASIC-like) compiling to TRIADA assembly.~~ Replaced by TRIBASIC, built into the machine (Phase 2, M13–M15).
- A cassette-loading effect: screeching audio and stripes across the border while a program loads.
- A web build using a libGDX web backend (TeaVM-based or GWT). First check which one is current and maintained and whether `triada-core` compiles with it, then write the choice up in `DECISIONS.md` before building. **Done as M12** (the web version on GitHub Pages).

### Phase 2 — TRIBASIC and the triskette (M13–M15)
Phase 2 adds **TRIBASIC**, the TRIADA-85's built-in programming language, plus the **triskette storage device** it needs for SAVE/LOAD. The machine is specified in SPEC §2.5, §5.1, §8.5 and §10. (These milestones were numbered M12–M14 in the task file; M12 was already the web version, so they are M13–M15, see DECISIONS.md D-044.)

**Before writing any code:**
1. TRIADA-85 v1.0 is **released and deployed on GitHub Pages**. Don't break it. Every existing program, game and test must still work at the end of each milestone.
2. Read the current `docs/SPEC.md`, `docs/DECISIONS.md`, `docs/ARCHITECTURE.md` and the ROM source. The released implementation may differ from the original spec (addresses, routine names, I/O usage). **Where they differ, the released behaviour wins**, unless it's a bug. Adapt the numbers to reality and log every adaptation in `DECISIONS.md`.
3. Look for features that already exist (for example import/export, file loading, paste-typing). **Reuse and extend them; never duplicate them.** List what you found at the top of the M13 doc.
4. Check for conflicts: I/O registers 23–27 and the address range −59,048 … −20,413 must be free (SPEC §2.5, §5.1). If they aren't, pick free ones and log it.
5. Write `docs/milestones/M13-…` etc. with a "Plan" section first, as usual.

All rules in this file still apply in full, especially §5 (documentation): every milestone gets its full milestone document and passes the documentation checks.

### M13 — Triskette storage device
- Implement the device from SPEC §5.1 in `triada-core`, behind a storage interface (`TriskettePort` or similar). It needs an in-memory implementation for tests, and **no file I/O in core**.
- Backends:
  - desktop: a `triskettes/` folder (configurable path);
  - headless CLI: `--disk <dir>`;
  - **web build: browser storage**, with all access wrapped in try/catch.
- **Import/export (conditional).** Users must be able to move files between their computer and the triskette, on both desktop and web. **First check whether the released version already has import/export** (for example for `.tri` files).
  - **If it does:** don't build a second one. Connect the existing feature to the triskette so that imported files appear in `FILES`, and files on the triskette can be exported. Extend it only where it's missing something, for example `.TB` files or a platform it doesn't cover. Record what already existed and what you changed in `DECISIONS.md`.
  - **If it doesn't:** add "Import file" and "Export file" buttons in the web build. On desktop, the `triskettes/` folder is enough.
  - Either way, the M13 milestone doc explains how a user gets a program in and out, step by step.
- Existing `.tri` games become loadable from the triskette.
- New kernel routines in the ROM jump table (next free entries): `DISKSAVE`, `DISKLOAD`, `DISKDIR`, `DISKDEL`. Document the calling convention.
- Tests: every command and every error code, plus a host round trip (save from the machine, file appears on the host, load back gives identical words).

### M14 — TRIBASIC I: the interpreter core
Written **in TRIADA assembly**, in the extension ROM (SPEC §2.5).
- Line editor: entering, replacing and deleting lines, and direct mode. Commands `LIST`, `RUN`, `NEW`; `CONT` is optional.
- Numbers, variables, the full expression evaluator with the operator precedence of SPEC §10.2, and **three-valued logic** (SPEC §10.3).
- `PRINT`, `INPUT`, `ASK`, `LET`, `GOTO`, `GOSUB`/`RETURN`, `FOR`/`TO`/`STEP`/`NEXT`, `IF … THEN … MAYBE … ELSE`, `REM`, `END`, `STOP`.
- All errors in SPEC §10.7. Pressing Esc stops a running program with `BREAK IN <line>`.
- **The machine now boots into TRIBASIC** (SPEC §10.8). `MON` enters the old monitor and the monitor's `BASIC` command returns, with the program kept.
- Test infrastructure, all in Java test code:
  - a **screen-text reader** that turns the tile map into a string;
  - a **typing helper** that feeds text into the KEY queue;
  - a **TRIBASIC test suite**: `tests/basic/NAME.bas` plus `NAME.expected`. The runner types the program, types `RUN`, runs until `READY.` appears again (with a frame limit), and compares the screen text.
- At least 40 test programs, covering every statement, every operator, every error and the three-valued logic truth tables.

### M15 — TRIBASIC II: strings, arrays, machine access, storage and release
- String variables and string functions (SPEC §10.4), arrays (SPEC §10.5).
- Machine statements and functions: graphics, sound, input, `PEEK`/`POKE`/`SYS` (SPEC §10.6).
- `SAVE`, `LOAD`, `BSAVE`, `BLOAD`, `BRUN`, `FILES`, `ERASE` (SPEC §10.6.4).
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

### M16 — Housekeeping (done)
Between the phases: a "Prior art and related work" section (README and `docs/PRIOR-ART.md`), and every model-specific number gathered into one **machine profile** (`MachineProfile`, `MachineProfiles.TRIADA_85`), from which the machine and the assembler are built. CLI `--model`. No change in behaviour. (The task file `SMALL-TASKS.md` called it M15; see DECISIONS.md D-056.)

### Phase 3 — TRIADA-NET (M17–M19)
Phase 3 adds **TRIADA-NET**: a fictional 1985 network of bulletin board systems (BBSes) that a TRIADA-85 can **really dial into**. The machine gets a modem. A real Java server hosts the boards, and the machine runs a terminal program to use them. The machine side is specified in SPEC §5.2, §10.6.5 and §11; the server and the protocol in `docs/NET-SPEC.md`. (These milestones were numbered M16–M18 in the task file `TRIADA-NET.md`; M16 was already the housekeeping, so they are M17–M19, see DECISIONS.md D-060.)

**Why this matters:** it's the feature no other ternary or fantasy-computer project has. Games like *Digital: A Love Story* simulate BBSes inside a closed game. TRIADA-NET is a real server and protocol that a working emulated machine connects to, and other people can write their own clients and boards for it.

**Before writing any code:**
1. TRIADA-85 v1.1 is **released and deployed on GitHub Pages**. Don't break it. Every existing program, game and test must still work at the end of each milestone.
2. Read the current `docs/SPEC.md`, `docs/DECISIONS.md`, `docs/ARCHITECTURE.md`, the machine profile code (M16), the ROM and TRIBASIC sources. **Reuse what exists, never duplicate it**, and **the released behaviour wins** where it differs from the plan. Log every adaptation in `DECISIONS.md`.
3. Check that I/O registers 28–35 are free (SPEC §5.2). If they aren't, pick free ones and log it.
4. The modem is a device listed in the **TRIADA-85 machine profile**, and its line rate comes from the profile.
5. **Never create accounts on hosting services and never deploy to a public server yourself.** Prepare everything (Docker image, config, docs) so the human can deploy. Local servers for development and tests are fine.

All rules in this file still apply in full, especially §5 (documentation).

### M17 — Modem device, protocol and TRIMODEM
- The modem device in `triada-core` (SPEC §5.2), behind a `ModemPort` interface. Backends:
  - **TCP** (desktop, CLI);
  - **WebSocket** (web build; it must use `wss://` because GitHub Pages is HTTPS);
  - **in-process loopback** (tests).
- Client side of the wire protocol (NET-SPEC §2).
- **TRIMODEM** encoding and transfer protocol (NET-SPEC §3) as a shared pure-Java library, used by both the server and the tests. The machine-side version is written in TRIADA assembly in M19.
- A minimal **echo test server**, enough to test the modem end to end.
- TRIBASIC modem statements (SPEC §10.6.5).
- Tests: every modem status, the character rate limit, buffer overflow, and hang-up from either side. Include a TRIBASIC test program that dials the echo server and checks what comes back.

### M18 — The TRIADA-NET server
- New modules:
  - **`triada-net-core`:** pure Java, no sockets. Boards, users, sessions, menus, messages, mail, file areas, rendering for a 27×18 screen.
  - **`triada-net-server`:** TCP and WebSocket transports, persistence, config, admin.
- **Recommended stack** (change it only with a DECISIONS entry):
  - Java 21+ **virtual threads** (one per caller);
  - **Javalin** for WebSocket, the health endpoint and the admin page;
  - plain `ServerSocket` for TCP;
  - **SQLite** through JDBC, with versioned SQL migration scripts run by a small in-house runner;
  - passwords hashed with **PBKDF2** from the JDK (no extra library).
- All features in NET-SPEC §4, security and moderation in NET-SPEC §5.
- **Deployment kit:** Dockerfile, `docker-compose.yml` (server plus a Caddy reverse proxy for TLS and `wss://`), all config through environment variables, a `/health` endpoint, structured logs. Write `docs/net/DEPLOY.md` explaining deployment to a small VPS or a container host, step by step, for someone who has never deployed a server.
- Tests:
  - unit tests for every menu action;
  - migration tests;
  - a protocol test suite;
  - a **load test**: 50 simulated callers at once on a local server, every session completes, no errors;
  - a **security test list**: wrong passwords, oversized input, flooding, path tricks in file names.

### M19 — Terminal, content and release v1.2
- **`TERM.TRI`**, the terminal program, written in TRIADA assembly and shipped on the default triskette (SPEC §11). Includes dial sounds, the control codes, TRIMODEM download and upload to the triskette, and a phonebook.
- TRIBASIC `DIAL "number"` starts TERM with that number (SPEC §10.6.5).
- **Content** (NET-SPEC §6): the starter boards, seeded fictional posts, the hidden story **outline**, and the files available for download.
- **End-to-end test:** a headless TRIADA boots, dials an in-process server, logs in as GUEST, reads a message, downloads a program with TRIMODEM, saves it to the triskette, hangs up, then `BRUN`s or `LOAD`s and `RUN`s it successfully.
- **Web build:**
  - the server URL is a build-time setting;
  - if no server is configured or it's unreachable, dialing gives `NO CARRIER` and the terminal prints "TRIADA-NET IS OFFLINE";
  - the deployed site must never break because the server is down.
- **Desktop:** a "Start local node" option (or `triada net-server --local`), so anyone can try TRIADA-NET offline.
- **Optional, only if cheap:** compile `triada-net-core` into the web build as an in-browser offline node. Spike it first and log the result.
- Manual chapter `docs/manual/triada-net.md`, *"Calling the World: Your TRIADA-85 Modem"*, written in-universe.
- **Release v1.2:** README section with screenshots or a GIF of a call, changelog, Pages redeploy. Add TRIADA-NET to `docs/PRIOR-ART.md`'s "what's new" paragraph.

---

## 4. Working loop for every milestone

1. Re-read the relevant spec sections.
2. Write a short plan at the top of the milestone doc ("Plan" section). You'll replace it later.
3. Implement in small steps with tests.
4. Run **all** tests, not only the new ones.
5. Write the milestone document (§5), then do the **documentation checks** (§5.5).
6. Update `PROGRESS.md`, `ARCHITECTURE.md`, `GLOSSARY.md`, `INDEX.md` and `README.md` as needed.
7. Commit.

---

## 5. Documentation rules (as important as the code)

The documentation has **two audiences at the same time**:

- **A person with no technical background.** They should understand *what* was built, *why*, and *how it works in principle*: the ideas, analogies and examples.
- **A programmer with some experience.** Using only the docs, they should be able to rebuild the project from nothing, understanding every design decision and finding the exact code that implements each idea.

Read in order, `docs/milestones/` should work like a book: **"How to build a computer that never existed."**

### 5.1 Writing style
- Plain language first, technical detail second. Every section starts with the simple explanation, then goes deeper.
- **Explain every technical word the first time it appears in each document**, briefly and in place, and add it to `GLOSSARY.md`. Never assume the reader knows terms like "register", "opcode", "stack" or "two's complement".
- Use **analogies** (a register is like a small notepad on the CPU's desk; memory is a long street of numbered mailboxes, with house number 0 in the middle) and **small worked examples** with real numbers.
- Use **ASCII diagrams** or tables for layouts: instruction encoding, memory map, pixel packing.
- Short paragraphs. No marketing tone. Be honest about trade-offs and problems.

### 5.2 Code references
Each "how it works" explanation must point to the real code:
- Format: `` `triada-core/src/main/java/org/triada/core/cpu/Cpu.java` → `Cpu.step()` `` as a relative Markdown link to the file, plus the class or method name. Line numbers are optional. Symbol names are **mandatory**, because they survive edits.
- Every important type or method introduced in a milestone must be referenced at least once in that milestone's doc.
- Short code excerpts (up to about 15 lines) are welcome when they make an idea concrete. Always explain them line by line in plain words.

### 5.3 Milestone document template (`docs/milestones/MNN-name.md`)
```markdown
# Milestone NN — <Title>

## In one paragraph
<What this milestone gives the machine, for someone who has never programmed.>

## What was built
<Bullet list of the concrete things that now exist.>

## Why it was built this way
<Each important decision: what we chose, what the alternatives were, why we
picked this one. Link the matching DECISIONS.md entries.>

## How it works — the plain-language version
<Ideas, analogies, diagrams and worked examples with real numbers. No code needed.>

## How it works — the programmer's version
<A walkthrough of the implementation in the order a reader should study it,
with code references (§5.2) and short explained excerpts.>

## Build it yourself from scratch
<Numbered steps that someone could follow to recreate this milestone WITHOUT
our code: what to create first, what to test, what tends to go wrong.>

## How we know it works
<Which tests exist and what they prove, the exact commands to run them, and
what you should see. Screenshots or output samples where useful.>

## Problems we hit and how we solved them
<Honest log of bugs, surprises and dead ends.>

## Limitations and what comes next
<What is missing or rough, and how the next milestone builds on this one.>

## New words
<Terms introduced here, each with a one-line explanation. They must also be
added to GLOSSARY.md.>
```

### 5.4 `DECISIONS.md` entry format
```markdown
## D-NNN — <Short title>   (Milestone MNN, <date>)
**Situation:** <What needed deciding.>
**Options:** <A, B, C, with short pros/cons.>
**Decision:** <What we chose.>
**Why:** <Reasoning, in plain words.>
**Consequences:** <What this makes easier or harder later.>
```

### 5.5 Documentation checks (do these before finishing each milestone)
1. **Reference check:** every file and symbol referenced in the doc exists (verify with search). Fix broken references.
2. **Command check:** every command in "How we know it works" was actually run and gives the output described.
3. **Beginner pass:** re-read the doc as someone who has never programmed. Find every unexplained term and every jump in logic, and fix them.
4. **Rebuild pass:** re-read "Build it yourself from scratch" as a programmer who can't see our code. Is any step missing? Fix it.
5. **Glossary check:** every new term is in `GLOSSARY.md`.
6. Update `INDEX.md` so the new doc is linked in reading order.

### 5.6 Living documents
- `ARCHITECTURE.md`: a current picture of all components and how data flows between them (CPU ↔ bus ↔ devices ↔ frontend), with a diagram. Update it whenever the architecture changes.
- `PROGRESS.md`: a checklist of milestones with status and date.
- `GLOSSARY.md`: alphabetical. Each entry has a simple explanation and, where relevant, "In TRIADA: …" plus the milestone where it first appears.

---

## 6. When stuck
- If a test fails and you don't understand why, write a smaller test first.
- If the spec seems wrong, follow §1 rule 1. Don't stop the whole project for a small question.
- Stop and ask the human only for decisions that are hard to undo or that change the character of the machine, such as changing word size, the memory map or the instruction set beyond small fixes.
