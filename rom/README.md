# rom/

Source code of the TRIADA-85 ROM, written in TRIADA assembly. The build assembles it into the emulator (module `triada-rom`, task `generateRom`).

- `rom.tasm`: start here; the jump table, and the files it includes
- `sysvars.tasm`: system variables and constants
- `kernel.tasm`: boot, font loading, chime, PUTCHAR, PRINT, PRINTNUM, CLS, GETKEY, LOCATE
- `monitor.tasm`: the command line (HELP, PEEK, POKE, RUN, CLS, TERN)
- `font.tasm`: GENERATED from `third_party/font8x8/font8x8_basic.h` (public domain) by `tools/FontToTasm.java`

See `docs/milestones/M08-rom.md`.
