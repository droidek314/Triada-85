# Glossary

Every technical word used in the documents, explained simply, in alphabetical order. "First seen" names the milestone where the word first appears.

---

**Address**: The number of a memory cell, like a house number on a street. *In TRIADA:* a word, −265,720 … +265,720, with 0 in the middle (SPEC §2.1). First seen: M2.

**Balanced ternary**: Base 3 with the digits −1, 0 and +1 instead of 0, 1 and 2. Negative numbers need no minus sign, and flipping every digit negates a number. *In TRIADA:* how every number is stored (SPEC §1.1). First seen: M1.

**Binary**: Counting with two digits, 0 and 1. Almost every real computer works this way, because a switch is easily either off or on. *In TRIADA:* the road not taken; TRIADA uses ternary instead. First seen: M0.

**BOM (bill of materials)**: A list that pins every part of a library family to versions that work together. *In TRIADA:* the JUnit BOM. First seen: M0.

**Boundary test**: A test at the exact edges of a range (first, last, one beyond), where off-by-one mistakes hide. First seen: M2.

**Build**: Turning source code into something that runs, usually running the tests on the way. *In TRIADA:* `./gradlew build` builds and tests everything. First seen: M0.

**Build tool**: A program that knows how to build a project: what to compile, in which order, and which libraries to download. *In TRIADA:* Gradle. First seen: M0.

**Bus**: The path that carries every read and write between the processor and memory or devices. *In TRIADA:* the `Bus` class. First seen: M2.

**Carry**: The digit that falls off the top when a result is too big for its space. *In TRIADA:* a trit (−1, 0 or +1), stored in flag O by ADD, SUB and ADDI. First seen: M1.

**Cartridge**: A plug-in module that adds memory or software to a computer. *In TRIADA:* lore only; addresses 59,049 and up are reserved for them. First seen: M2.

**CLI (command-line interface)**: A program you control by typing commands in a terminal instead of clicking. *In TRIADA:* the `triada` tool in `triada-cli`. First seen: M0.

**Commit**: A saved snapshot of the whole project in version control (git), with a message saying what changed. *In TRIADA:* at least one commit per milestone. First seen: M0.

**Compile**: Translate source code that people write (here, Java) into a form the computer can run. First seen: M0.

**Dependency**: A ready-made library your project uses instead of writing that code itself. *In TRIADA:* libGDX (window, sound, input) and JUnit (tests), and nothing else without a DECISIONS.md entry. First seen: M0.

**Device**: A piece of hardware the processor talks to through I/O registers. *In TRIADA:* anything implementing `IoDevice`, such as `InputDevice`. First seen: M2.

**Enum**: A Java type with a fixed list of named values, such as `Region` with UNMAPPED, IO, ROM and RAM. First seen: M2.

**Exhaustive test**: A test that tries every possible input instead of a sample. *In TRIADA:* all 531,441 words in `WordTest`. First seen: M1.

**Flag**: A one-digit note the processor keeps about the last calculation, which later instructions can check. *In TRIADA:* S (sign) and O (overflow/carry), both trits. First seen: M1.

**Frame**: One complete picture on the screen. Showing many frames per second makes movement. *In TRIADA:* 60 frames per second (SPEC §6). First seen: M0.

**Frontend**: The part of a program that faces the person: window, sound, keyboard. *In TRIADA:* `triada-desktop`, built with libGDX. The machine itself (`triada-core`) knows nothing about it. First seen: M0.

**Garbage collector**: The part of Java that finds objects no longer used and frees their memory. It costs time, so fast code creates few objects. First seen: M1.

**Gradle**: The build tool this project uses. Its settings are written in Kotlin script files (`*.gradle.kts`). First seen: M0.

**Gradle wrapper**: A tiny script (`gradlew`, `gradlew.bat`) plus a small jar, committed to the project, that downloads exactly the right Gradle version on first use. Nobody has to install Gradle by hand. First seen: M0.

**Headless**: Running without any window, sound or keyboard, which is useful for automatic tests. *In TRIADA:* the `triada run` command (M5). First seen: M0.

**Horner's rule**: Building a number digit by digit: multiply what you have by the base, then add the next digit. `+-0` → 1, 1·3−1 = 2, 2·3+0 = 6. First seen: M1.

**I/O register**: An address that isn't storage but a doorbell or window to hardware: reading or writing it talks to a device. *In TRIADA:* 729 slots from −20,412, 23 of them defined (SPEC §5). First seen: M2.

**Interface (Java)**: A list of methods a class promises to provide, so code can work with any class that keeps the promise. First seen: M2.

**JDK (Java Development Kit)**: The tools needed to compile and run Java programs. *In TRIADA:* JDK 25. First seen: M0.

**JUnit**: The standard Java library for writing automatic tests. *In TRIADA:* JUnit 5. First seen: M0.

**JVM (Java Virtual Machine)**: The program that runs compiled Java code. It accepts start-up settings called JVM flags. First seen: M0.

**Kernel**: The core built-in software of a computer. *In TRIADA:* lives in ROM, written in TRIADA assembly (M8). First seen: M2.

**Least significant trit**: The rightmost trit, worth 1. *In TRIADA:* t0. First seen: M1.

**libGDX**: A free Java library for games. It opens windows, draws pictures with the graphics card, plays sound and reads the keyboard. *In TRIADA:* used only by `triada-desktop`. First seen: M0.

**Lookup table**: A list of precomputed answers, so a calculation becomes a single array read. First seen: M1.

**LWJGL**: "Lightweight Java Game Library", the layer libGDX uses on desktop computers to talk to the graphics card (OpenGL), the window system (GLFW) and sound (OpenAL). First seen: M0.

**Maven Central**: The big public warehouse of Java libraries that Gradle downloads dependencies from. First seen: M0.

**Memory map**: The plan of which addresses are RAM, ROM, I/O or empty. *In TRIADA:* SPEC §2.2 and `MemoryMap`. First seen: M2.

**Module**: One self-contained part of a larger project, built separately, with its own code and tests. *In TRIADA:* `triada-core`, `triada-assembler`, `triada-cli` and `triada-desktop`. First seen: M0.

**Most significant trit**: The leftmost trit, with the biggest place value. *In TRIADA:* t11, worth 3^11 = 177,147. First seen: M1.

**Mutation check**: Breaking the code on purpose to confirm that the tests notice. If they don't, the tests are too weak. First seen: M1.

**Native library**: Code compiled directly for one kind of computer and operating system, not Java. LWJGL needs some to reach the graphics card. First seen: M0.

**OpenGL**: A standard way for programs to ask the graphics card to draw. libGDX uses it behind the scenes. First seen: M0.

**Parameterized test**: One test method run once for every row of a table of inputs. First seen: M2.

**Peek**: Looking at a memory address without any side effect. *In TRIADA:* `Bus.peek()`, used by the debugger; also a monitor command (M8). First seen: M2.

**Plugin (Gradle)**: A ready-made bundle of build steps you switch on in a build file, for example `application`, which knows how to run a program. First seen: M0.

**Power-on state**: How a machine looks the instant it is switched on. *In TRIADA:* RAM all 0, registers 0, PC = −19,683 (SPEC §2.4). First seen: M2.

**Queue (FIFO)**: A waiting line: first in, first out. *In TRIADA:* the KEY queue. First seen: M2.

**RAM (random-access memory)**: Memory that can be read and written. *In TRIADA:* addresses 0 … 59,048. First seen: M2.

**Reference implementation**: A slow but obviously correct version of an algorithm, used only in tests to check the fast version. First seen: M1.

**Register**: A small, very fast storage slot inside the processor, like a notepad on the CPU's desk. *In TRIADA:* R0–R8, one word each (SPEC §3.1). First seen: M1.

**Render loop**: The cycle a game runs over and over: update, draw a frame, repeat. *In TRIADA:* libGDX calls `TriadaApp.render()` about 60 times a second. First seen: M0.

**Ring buffer**: A fixed-size array used as a queue whose start and end chase each other round and round, so nothing is ever moved or allocated. First seen: M2.

**ROM (read-only memory)**: Memory that can be read but not changed by programs. *In TRIADA:* addresses −19,683 … −1, holding the kernel. First seen: M2.

**Rotate (ROT)**: *In TRIADA:* turn every trit one step around a three-hour clock: − → 0 → + → −. First seen: M1.

**Shift (SHT)**: Slide all digits left (multiply by the base) or right (drop the last digits). *In TRIADA:* a right shift rounds to the nearest number. First seen: M1.

**Side effect**: A change an operation makes beyond giving its answer. *In TRIADA:* reading KEY removes the key. First seen: M2.

**Smoke test**: A very small test that only checks "does it start at all?", like switching on a new appliance to see whether smoke comes out. First seen: M0.

**Source code**: The text of a program as people write it. First seen: M0.

**Spec (specification)**: The exact written description of how something must behave. *In TRIADA:* `docs/SPEC.md`, the law of the machine. First seen: M0.

**Sprite**: A small picture that moves freely over the background. *In TRIADA:* 27 sprites, table at RAM 44,600 (SPEC §4.7). First seen: M2.

**Stack**: A pile of notes where the last one added is the first one taken, used for return addresses and saved values. *In TRIADA:* grows downward from 59,049. First seen: M2.

**Stack pointer**: The register that remembers where the top of the stack is. *In TRIADA:* R8, also called SP. First seen: M2.

**System variables**: RAM cells where the built-in software keeps its own notes. *In TRIADA:* addresses 0 … 242. First seen: M2.

**Ternary**: Counting with three digits. *Balanced* ternary uses −1, 0 and +1 (written `-`, `0`, `+`). *In TRIADA:* everything. First seen: M0.

**Test (automatic test)**: A small program that checks whether another piece of code does what it should, and complains loudly if not. First seen: M0.

**Tile**: A small fixed picture (8×8 pixels) used as a building block for the screen. *In TRIADA:* 243 tiles at RAM 40,000 (SPEC §4.4). First seen: M2.

**Tile map**: The grid saying which tile goes in each square of the screen. *In TRIADA:* 27 × 18 cells at RAM 44,000. First seen: M2.

**Toolchain (Gradle)**: Gradle's setting for "compile and test with exactly this Java version", even if a different one is installed as the default. First seen: M0.

**Trit**: One ternary digit: −1, 0 or +1. The ternary cousin of the bit. First seen: M0.

**Trit-wise operation**: An operation that treats each trit column separately, ignoring the number's value. *In TRIADA:* MIN, MAX, TMUL and ROT. First seen: M1.

**Truth table**: A table listing an operation's result for every combination of inputs. First seen: M1.

**Tryte**: Six trits, the ternary cousin of the byte. Range −364 … +364. First seen: M1.

**Two's complement**: The usual way binary computers store negative numbers, using the top bit as a sign. Balanced ternary doesn't need it. First seen: M1.

**Unmapped**: An address with nothing behind it: reads 0, writes have no effect. First seen: M2.

**Version catalog**: One file (`gradle/libs.versions.toml`) that lists every external library and its version, so they aren't scattered across build files. First seen: M0.

**Version control (git)**: A system that remembers every saved version of a project, so you can see what changed, when and why, and go back. First seen: M0.

**Vsync**: Waiting for the monitor to finish showing one picture before handing it the next, so the image never tears halfway. First seen: M0.

**Word**: The amount of data a computer handles in one piece. *In TRIADA:* 12 trits, −265,720 … +265,720; every register and memory cell holds one word. First seen: M1.

**Wrap (overflow)**: When a result is too big for its space and rolls around to the other end, like a car's odometer. *In TRIADA:* SPEC §1.3. First seen: M1.
