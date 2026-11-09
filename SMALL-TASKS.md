# Small Tasks — before TRIADA-NET

Two small tasks, done together as **milestone M15 — Housekeeping**. All rules in `CLAUDE.md` apply, including §5 (documentation). Write one milestone doc, `docs/milestones/M15-housekeeping.md`, with a section per task.

**Hard rule for both tasks: no change in behaviour.** Every existing test, program, game and TRIBASIC test must pass unchanged, and the deployed web build must work exactly as before.

---

## Task 1 — "Prior art" section in the README

Add a section called **"Prior art and related work"** near the end of `README.md`. Also add a fuller page at `docs/PRIOR-ART.md` and link it from `docs/INDEX.md`.

**Tone:** respectful and factual. These projects inspired or came before TRIADA. Never present TRIADA as "better"; say what it does *differently*.

**Before writing, open each link and verify every claim.** Correct anything that's wrong, and drop a project if the link is dead and you can't find another source. Write the descriptions in your own words; don't copy text from those pages.

| Project | What it is | How TRIADA differs |
|---|---|---|
| **Setun / Setun-70** (Moscow State University, 1958 / 1970) | The real Soviet balanced-ternary computers. TRIADA's alternate history starts from them | TRIADA is fictional, a home computer from a world where ternary won |
| **Setun emulators** (e.g. `setun70` on PyPI, `smaslovski/Setun70` on GitHub) | Faithful reconstructions of the historical machines | TRIADA invents its own machine: graphics, sound, games, an OS and a language |
| **SBTCVM** (`github.com/SBTCVM/SBTCVM-Gen2-9`) | A balanced-ternary VM in Python with graphics (including 27-color 3-trit RGB), sound, games and its own languages | No alternate-history world. Its language is maths-oriented, while TRIBASIC is built around three-valued logic (THEN/MAYBE/ELSE, ASK). TRIADA is also documented as a from-scratch book |
| **VINE** (`github.com/bates64/vine`) | A ternary fantasy console in the browser, programmed in assembly | No OS, high-level language, storage or world |
| **PICO-8 / TIC-80** | Popular (binary) fantasy consoles | The fantasy-console idea TRIADA is part of |
| **Digital: A Love Story** (Christine Love, 2010) | A story game played on a fictional 1988 computer through fictional BBSes | Inspiration for the upcoming TRIADA-NET. TRIADA's network will be a real server that a working emulated machine dials into |
| **Hypnospace Outlaw** (Tendershoot, 2019) | A game set inside an alternate-history 1999 internet | Same as above: TRIADA's network is open and real, not a closed game |

End the section with one short paragraph on **what TRIADA brings that's new as a whole**: the alternate-history ternary home computer, TRIBASIC's three-valued logic, the "how to build a computer that never existed" book, and (upcoming) a real fictional network.

---

## Task 2 — Machine configuration in one place

**Goal:** prepare for future models (TRIADA-88, TRIADA-91) by moving every machine-specific number and choice into a single **machine profile**. Today there is one profile: `TRIADA_85`.

### What goes into the profile
Everything that would differ between models. For example:
- memory map: RAM range, main ROM range, extension ROM range, I/O base and size;
- RAM layout constants used by the hardware: tile, map and sprite addresses;
- CPU timing: cycles per frame, frames per second;
- video: resolution, tile size, tile count, sprite count;
- sound: number of voices;
- which devices are attached: video, sound, input, triskette, debug/host;
- which ROM images to load: main ROM, extension ROM (TRIBASIC);
- the model name and version shown in the UI ("TRIADA-85").

### Rules
1. Put the profile in `triada-core` (e.g. an immutable `MachineProfile` record plus a `MachineProfiles.TRIADA_85` constant). The machine is built from a profile, never from scattered constants.
2. **Search the whole codebase for hard-coded numbers** (59049, −19683, −20412, 40000, 44000, 44600, 216, 144, 59049 cycles, etc.) and replace them with profile values. List what you replaced in the milestone doc.
3. The **assembler's built-in symbols** (`TILES`, `MAP`, `IO_BASE`, …) come from the profile too. Assembling for a profile is explicit (default `TRIADA_85`).
4. **Frontends and CLI:** the profile is chosen at startup. For now only `TRIADA_85` exists. Add a `--model` option to the CLI (default `triada-85`) and keep the UI unchanged.
5. **ROM source** (in TRIADA assembly) keeps working. If ROM code uses constants, they should come from the assembler's profile symbols rather than literal numbers wherever practical.
6. **Tests:**
   - all existing tests pass unchanged;
   - a new test asserts that `TRIADA_85` has exactly the values in `SPEC.md`;
   - a new test builds a machine from a *modified* test profile (for example a different RAM size) and checks the machine actually follows it. This proves nothing is still hard-coded.
7. **Docs:** add a "Machine profiles" section to `ARCHITECTURE.md`, with a diagram of how a profile turns into a running machine. Add a short note to `SPEC.md` that values in the spec are those of the TRIADA-85 profile. Add `DECISIONS.md` entries for the design.

### Explain it simply in the milestone doc
Include a plain-language analogy, for example *"a profile is the machine's birth certificate: it says how much memory it has and which parts are inside, and the emulator builds the computer from it"*. Also explain why this makes future models possible.

---

## After M15
Update `PROGRESS.md`. The next phase is TRIADA-NET; its milestones will be numbered from M16.
