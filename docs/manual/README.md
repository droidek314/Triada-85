# TRIADA-85 — Owner's Manual

```
        +------------------------------------------------+
        |                                                |
        |     T R I A D A - 8 5                          |
        |                                                |
        |     HOME COMPUTER  ·  БЫТОВАЯ ЭВМ              |
        |                                                |
        |     OWNER'S MANUAL                             |
        |     First English edition                      |
        |                                                |
        |     NPO "TRIADA"  ·  1985                      |
        |                                                |
        +------------------------------------------------+
```

## Dear owner,

Thank you for choosing the TRIADA-85, the home computer that counts the way nature intended: in threes.

Your computer belongs to the great family of ternary machines that began with the SETUN in 1958. Like its ancestors, it does not know only "yes" and "no". It also knows "neither". Every digit inside it is a **trit**, which can be −1, 0 or +1. You will find that this makes many things simpler. Negative numbers need no special treatment, rounding comes for free, and a joystick has exactly the three positions a joystick should have.

This manual will help you set up your computer, program it in TRIBASIC and in its own machine language, use its built-in monitor, make pictures and music, and play the games supplied on the Games triskette. No previous knowledge is required. Read the chapters in order, try every example at the keyboard, and do not be afraid: **nothing you type can damage the computer**. If something goes wrong, switch it off and on again.

We wish you many happy hours.

*The engineers of NPO "Triada"*

---

## Contents

| Chapter | |
|---|---|
| [1. Getting started](01-getting-started.md) | Unpacking, connecting, switching on. The keyboard and the joystick. |
| [2. Counting in threes](02-counting-in-threes.md) | Trits, trytes and words. Balanced ternary without tears. |
| [3. The monitor](03-the-monitor.md) | Talking to your computer: HELP, TERN, PEEK, POKE, RUN, CLS. |
| [4. Programming in assembly language](04-programming.md) | Registers, instructions, the stack. Your first program. |
| [5. Pictures and sound](05-pictures-and-sound.md) | 27 colours, tiles, sprites, scrolling, three voices. |
| [6. The Games triskette](06-games-triskette.md) | TRIZMEYKA, TROIKA and OBLAKA. |
| [7. From the magazine: a type-in listing](07-type-in-listing.md) | "Triangle of Threes", from *Trit & Tryte* No. 3/1985. |
| [8. TRIBASIC: Programming with Three Answers](tribasic.md) | The built-in language: IF … THEN … MAYBE … ELSE, strings, arrays, pictures, sound and the triskette. With "Trit Tapestry" to type in. |
| [9. Calling the World: Your TRIADA-85 Modem](triada-net.md) | DIAL, the terminal program TERM, TRIADA-NET's boards, taking programs home with TRIMODEM. |
| [Appendix: Quick reference](appendix-reference.md) | Instructions, memory map, I/O registers, colours, ROM routines. |

---

## About this edition (a note from the present day)

The TRIADA-85 never existed. It is a fantasy computer from an alternate 1985 in which ternary computers won. This manual is written *as if* it had been printed then, but everything in it is true of the emulator in this repository. Every number, command and example works, and the monitor examples and the type-in listing are checked by automatic tests (`ManualTest` in `triada-cli`).

Where the manual speaks of hardware, the emulator stands in for it:

| In the manual | In the emulator |
|---|---|
| Switching on | `./gradlew :triada-desktop:run` |
| The joystick | The arrow keys |
| Fire buttons A, B, C | The keys Z, X, C |
| Inserting a triskette | `--load file.tasm`, or dropping the file on the window |
| The Developer triskette with TASM | The `triada tasm` command (see the [project README](../../README.md)) |
| The service panel (Chapter 4) | The debugger, F1 |
| The modem and the telephone line (Chapter 9) | `--modem HOST:PORT` (a TRIADA-NET server), or `--local-node` (the boards inside the emulator) |

For how the machine was built, read the project documentation, starting at [docs/INDEX.md](../INDEX.md). The exact technical rules are in [docs/SPEC.md](../SPEC.md).
