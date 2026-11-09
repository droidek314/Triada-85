# Prior art and related work

TRIADA-85 did not come from nowhere. Real people built real ternary computers decades ago. Others have emulated those machines, or invented ternary machines of their own. Many people make "fantasy consoles": computers that exist only as software, with limits chosen on purpose. And a few games have told stories set inside fictional computers and networks.

This page lists the projects we know of that came before TRIADA or inspired it. It says what each one is and how TRIADA differs. "Differs" never means "is better": each of these projects does something TRIADA doesn't.

Every link was opened and every statement checked against the project's own pages (or, for the historical machines and the games, against their Wikipedia articles) in November 2026. The descriptions are in our own words.

## The real ternary computers

### Setun (1958) and Setun-70 (1970)

**What it is.** Setun was a computer built at Moscow State University in 1958. Its designers were Sergei Sobolev and Nikolay Brusentsov, and Brusentsov led the engineering. It counted in **balanced ternary**, the same −1 / 0 / +1 digits that TRIADA uses. About 50 machines were made at a factory in Kazan between 1959 and 1965. Its successor, Setun-70, was designed by Brusentsov together with Zhogolev. It is usually dated 1970, and it was a stack machine: it worked on values kept in a pile instead of in named registers.

**How TRIADA differs.** Setun was real, and TRIADA is fiction. TRIADA's alternate history starts where Setun stopped: *what if ternary had won, and by 1985 there were ternary home computers with colour graphics and games?* Nothing in TRIADA copies Setun's design. Its word size, instructions and memory map are all invented ([SPEC.md](SPEC.md)).

Source: [Setun on Wikipedia](https://en.wikipedia.org/wiki/Setun).

## Emulators of the historical machines

An **emulator** is a program that behaves like a particular computer, so software written for that computer runs on another one.

### `setun70` (Python, on PyPI)

**What it is.** A Python package that emulates the Setun-70. It was rebuilt from the 1970 Moscow State University paper describing the machine, and it covers that machine's balanced-ternary arithmetic, its two-stack execution model and its short "syllable" instructions. The source code is at [github.com/Zaneham/setun70-emulator](https://github.com/Zaneham/setun70-emulator), MIT-licensed, and it also has an emulator that runs in the browser. Package page: [pypi.org/project/setun70](https://pypi.org/project/setun70/).

### `smaslovski/Setun70` (Fortran, on GitHub)

**What it is.** A separate Setun-70 emulator, written in Fortran and released under the GPL in 2023. It follows the machine's logical description in the same 1970 report, and keeps a branch with the instruction set exactly as described there. Its README says it is in early development. [github.com/smaslovski/Setun70](https://github.com/smaslovski/Setun70).

**How TRIADA differs (both).** These projects reconstruct a machine that existed, as faithfully as the surviving papers allow. TRIADA invents its own machine. Because it isn't tied to history, it has graphics, sound, games, a ROM operating system and a programming language.

## Invented ternary machines

### SBTCVM

**What it is.** SBTCVM ("Simple Balanced Ternary Computer Virtual Machine") is a balanced-ternary virtual machine written in Python, by Thomas Leathers and contributors (GPL v3). The current generation, Gen2-9, has a 9-trit CPU, a 4-channel sound chip, demos and games, and its own languages: **SSTNPL** and an SBTCVM assembly language. Its developer's posts describe a 3-trit RGB colour mode, which gives 27 colours (TRIADA's palette uses the same idea). [github.com/SBTCVM/SBTCVM-Gen2-9](https://github.com/SBTCVM/SBTCVM-Gen2-9); the colour modes are described in [Rainbow effect: ternary color spaces and SBTCVM](https://dev.to/thomasthespacefox/rainbow-effect-ternary-color-spaces-and-sbtcvm-2bp4).

**How TRIADA differs.** SBTCVM is the project closest to TRIADA, and it got there first. The differences are in purpose:
- SBTCVM is a VM to explore. TRIADA is a home computer from an invented 1985, with an owner's manual and a magazine.
- TRIADA's language, TRIBASIC, is built around **three-valued logic**: `IF … THEN … MAYBE … ELSE` and the `ASK` statement, which takes yes, no or "don't know".
- TRIADA is written up as a from-scratch book, so a reader can rebuild every part.

### VINE

**What it is.** VINE is a ternary **fantasy console** that runs in the web browser, by bates64 (0BSD licence). It has a 9-trit word, a 243 × 243 display and its own assembly language. It is aimed at people learning assembly or exploring balanced ternary. [github.com/bates64/vine](https://github.com/bates64/vine).

**How TRIADA differs.** VINE's pages describe a console programmed in assembly. They mention no operating system, high-level language, storage device or fictional world. TRIADA has all four: the ROM kernel and monitor, TRIBASIC, the triskette drive, and the alternate 1985.

## Fantasy consoles

### PICO-8 and TIC-80

**What they are.** A fantasy console is a computer that exists only as software, with tight limits chosen on purpose, so that small games are fun to make.
- **PICO-8**, by Lexaloffle, is the best-known one: a 128 × 128 screen, 16 colours, programs in a version of Lua. It is commercial. [lexaloffle.com/pico-8.php](https://www.lexaloffle.com/pico-8.php)
- **TIC-80** is a free and open-source fantasy computer with a 240 × 136 screen and 16 colours. [tic80.com](https://tic80.com/)

Both count in binary, like every computer you own.

**How TRIADA differs.** TRIADA is part of the same idea: a made-up machine with deliberate limits. Its twist is ternary, and a history to go with it.

## Stories inside fictional computers

### Digital: A Love Story (Christine Love, 2010)

**What it is.** A free story game written by Christine Love. You play it on the screen of a fictional late-1980s computer, set "five minutes into the future of 1988", by dialling into bulletin board systems (BBSes) and reading messages. [Wikipedia](https://en.wikipedia.org/wiki/Digital:_A_Love_Story).

### Hypnospace Outlaw (Tendershoot, 2019)

**What it is.** A game by Tendershoot (published by No More Robots), set inside an alternate-history 1999 internet that people visit while they sleep. [Wikipedia](https://en.wikipedia.org/wiki/Hypnospace_Outlaw).

**How TRIADA differs (both).** These games inspire the coming **TRIADA-NET**: a fictional network of the TRIADA world. Theirs are closed worlds written for one story. TRIADA's network is meant to be a real server that a working emulated machine dials into, where people can leave things for each other.

## What TRIADA brings that is new

Each piece above existed before. What TRIADA adds is the combination:
- a ternary **home computer** from an alternate 1985, with a manual, a magazine and games;
- **TRIBASIC**, a BASIC whose IF has three branches and whose logic has three answers;
- the whole machine written up as a book, *How to build a computer that never existed*, so anyone can rebuild it;
- (coming next) a real fictional network for that machine to dial into.
