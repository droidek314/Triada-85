# Changelog

What changed in each released version of the TRIADA-85. The milestone documents in [`docs/milestones/`](docs/milestones/) tell the whole story.

## Unreleased

Housekeeping (M16). The machine behaves exactly as in v1.1.

- Every model-specific number now lives in one machine profile, `MachineProfiles.TRIADA_85`, and the machine is built from it.
- `triada tasm` and `triada run` accept `--model triada-85` (the default and, for now, the only model).
- New built-in assembler symbols: `ROM_START`, `EXT_ROM_START`, `PROGRAM_START`, `STACK_TOP`, `MAP_COLS`, `MAP_ROWS`, `MAP_CELLS`. A source that defines one of these names itself must rename it.
- A "Prior art and related work" section in the README and `docs/PRIOR-ART.md`.

TRIADA-NET, part 1 (M17):
- **A modem** on I/O registers 28–35 (SPEC §5.2): it dials, connects and moves 2 characters per frame each way, like a 1200 bit/s line. The debugger shows it.
- **TRIBASIC:** `MDIAL`, `MHANGUP`, `MSEND`, `MSTATUS`, `MRECV$` (and the keyword `DIAL`, for the terminal of M19). Example: `programs/basic/AUTOCALL.bas`.
- `triada run --modem HOST:PORT` and the desktop's `--modem HOST:PORT` connect the modem to a TRIADA-NET server; the web version dials the address set in its page (none yet, so it is offline as before).
- **TRIADA-NET:** the wire protocol and TRIMODEM in `docs/NET-SPEC.md`, the modules `triada-net-core` and `triada-net-server`, and an echo server: `./gradlew :triada-net-server:run`.

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
