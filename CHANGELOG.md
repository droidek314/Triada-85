# Changelog

What changed in each released version of the TRIADA-85. The milestone documents in [`docs/milestones/`](docs/milestones/) tell the whole story.

## v1.1 — TRIBASIC and the triskette (2026-11-08)

The machine now greets you with a programming language of its own, and can keep your programs.

- **TRIBASIC** (M14, M15), written in TRIADA assembly in the new extension ROM (−59,048 … −20,413):
  - three-valued logic: `TRUE`, `UNKNOWN`, `FALSE`; `AND`, `OR`, `NOT` as MIN, MAX and NEG; the compare operator `<=>`; `IF … THEN … MAYBE … ELSE`; `ASK`;
  - numbers, strings (up to 80 characters) and arrays (`DIM A(-n TO n)`), with the usual functions;
  - statements for the picture and sound chips and for machine code (`TILE`, `DEFTILE`, `SPRITE`, `SOUND`, `COLOR`, `BORDER`, `SCROLL`, `WAIT`, `POKE`, `PEEK`, `SYS`);
  - `SAVE`, `LOAD`, `FILES`, `ERASE`, `BSAVE`, `BLOAD`, `BRUN`.
- **The machine boots into TRIBASIC.** `MON` reaches the machine-code monitor; the monitor's new `BASIC` command comes back.
- **The triskette drive** (M13): I/O registers 23–27 and the ROM routines `DISKSAVE`, `DISKLOAD`, `DISKDIR`, `DISKDEL`. The triskette is a folder on the desktop (`triskettes/`, or `--disk DIR`), browser storage on the web, and `--disk DIR` for `triada run`.
- **Getting files in and out:** dropping a file stores it on the triskette; the web page has Import and Export.
- **Paste-typing:** Ctrl+V on the desktop and the web, `triada run --type FILE` on the command line.
- **The Esc key** now reaches the machine (code 27) on the desktop and the web.
- **Examples** in `programs/basic/` and a new manual chapter, *TRIBASIC: Programming with Three Answers*.
- The specification is version 1.1 (`docs/SPEC.md` §2.5, §5.1, §8.5, §10).

## v1.0 — The TRIADA-85 (2026-11-08)

The whole machine (M0 to M12): balanced-ternary arithmetic, the CPU with its 27 instructions, memory, video and sound chips, keyboard and joystick, the assembler and disassembler, the ROM with its monitor, the debugger, the games TRIZMEYKA, TROIKA and OBLAKA, the owner's manual, the CRT look, and the web version on GitHub Pages.
