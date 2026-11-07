# Glossary

Every technical word used in the documents, explained simply, in alphabetical order. "First seen" names the milestone where the word first appears.

---

**Address**: The number of a memory cell, like a house number on a street. *In TRIADA:* a word, −265,720 … +265,720, with 0 in the middle (SPEC §2.1). First seen: M2.

**Assembler**: A program that turns assembly language text into machine words. *In TRIADA:* `tasm`, the `Assembler` class (M4). First seen: M4.

**Assembly language**: The thinnest layer above machine code: one line per instruction, written with mnemonics and names instead of numbers. First seen: M4.

**Balanced ternary**: Base 3 with the digits −1, 0 and +1 instead of 0, 1 and 2. Negative numbers need no minus sign, and flipping every digit negates a number. *In TRIADA:* how every number is stored (SPEC §1.1). First seen: M1.

**Benchmark**: A program that measures how fast something runs. *In TRIADA:* `./gradlew :triada-core:benchmark` (M3). First seen: M3.

**Binary**: Counting with two digits, 0 and 1. Almost every real computer works this way, because a switch is easily either off or on. *In TRIADA:* the road not taken; TRIADA uses ternary instead. First seen: M0.

**BOM (bill of materials)**: A list that pins every part of a library family to versions that work together. *In TRIADA:* the JUnit BOM. First seen: M0.

**Boundary test**: A test at the exact edges of a range (first, last, one beyond), where off-by-one mistakes hide. First seen: M2.

**Branch**: Another word for a jump, especially a conditional one. *In TRIADA:* BR3 is a three-way branch. First seen: M3.

**Build**: Turning source code into something that runs, usually running the tests on the way. *In TRIADA:* `./gradlew build` builds and tests everything. First seen: M0.

**Build tool**: A program that knows how to build a project: what to compile, in which order, and which libraries to download. *In TRIADA:* Gradle. First seen: M0.

**Bus**: The path that carries every read and write between the processor and memory or devices. *In TRIADA:* the `Bus` class. First seen: M2.

**Canonical form**: The one standard way of writing something. *In TRIADA:* an instruction word whose unused fields are all 0. First seen: M4.

**Carry**: The digit that falls off the top when a result is too big for its space. *In TRIADA:* a trit (−1, 0 or +1), stored in flag O by ADD, SUB and ADDI. First seen: M1.

**Cartridge**: A plug-in module that adds memory or software to a computer. *In TRIADA:* lore only; addresses 59,049 and up are reserved for them. First seen: M2.

**CLI (command-line interface)**: A program you control by typing commands in a terminal instead of clicking. *In TRIADA:* the `triada` tool in `triada-cli`. First seen: M0.

**Commit**: A saved snapshot of the whole project in version control (git), with a message saying what changed. *In TRIADA:* at least one commit per milestone. First seen: M0.

**Compile**: Translate source code that people write (here, Java) into a form the computer can run. First seen: M0.

**Conditional jump**: A jump that only happens if a condition on the flags holds. *In TRIADA:* JIF and its aliases JN, JZ, JP, JNZ, JLE, JGE. First seen: M3.

**Constant (assembler)**: A name for a number, defined with `NAME = value`. First seen: M4.

**CPU (central processing unit)**: The part of a computer that reads instructions from memory and carries them out, one after another. *In TRIADA:* the `Cpu` class. First seen: M3.

**Cycle**: One tick of a processor's clock. *In TRIADA:* every instruction takes exactly one cycle. First seen: M3.

**Cycle budget**: How many cycles the CPU may use per frame. *In TRIADA:* 59,049 = 3^10 (SPEC §6). First seen: M3.

**Debug port**: I/O registers through which a program sends text and numbers to the host instead of the screen. *In TRIADA:* DEBUG_CHAR, DEBUG_NUM, HOST_EXIT (registers 20–22). First seen: M5.

**Decode**: Splitting an instruction word into its fields (opcode, registers, immediate). First seen: M3.

**Dependency**: A ready-made library your project uses instead of writing that code itself. *In TRIADA:* libGDX (window, sound, input) and JUnit (tests), and nothing else without a DECISIONS.md entry. First seen: M0.

**Device**: A piece of hardware the processor talks to through I/O registers. *In TRIADA:* anything implementing `IoDevice`, such as `InputDevice`. First seen: M2.

**Directive**: A line that tells the assembler what to do (`.org`, `.str`, `.include` …) rather than producing a CPU instruction. First seen: M4.

**Disassembler**: A program that turns machine words back into assembly text. *In TRIADA:* the `Disassembler` class, used by the debugger. First seen: M4.

**Emulator**: A program that imitates a different computer closely enough to run its programs. First seen: M3.

**End-to-end test**: A test that goes through the whole system the way a user would, e.g. assembling and running a real file through the real command line. First seen: M5.

**Enum**: A Java type with a fixed list of named values, such as `Region` with UNMAPPED, IO, ROM and RAM. First seen: M2.

**Environment variable**: A named setting the operating system gives every program, such as `JAVA_HOME`. First seen: M5.

**Escape sequence**: A backslash code for a special character inside quotes, such as `\n` for a new line. First seen: M4.

**Execute**: Carrying out an instruction. First seen: M3.

**Exhaustive test**: A test that tries every possible input instead of a sample. *In TRIADA:* all 531,441 words in `WordTest`. First seen: M1.

**Exit code**: The number a program hands back to the operating system when it ends; 0 means success. *In TRIADA:* `triada run` returns the value a program writes to HOST_EXIT. First seen: M5.

**Expression**: A small calculation in the source, such as `IO_BASE + KEY` or `end - start`. *In TRIADA:* numbers and names combined with + and −. First seen: M4.

**Fault**: An error that stops the CPU, recorded with a code and the address of the guilty instruction. *In TRIADA:* fault 1 = invalid JIF condition (D-010). First seen: M3.

**Fetch**: Reading the next instruction from memory at the address in PC. First seen: M3.

**Flag**: A one-digit note the processor keeps about the last calculation, which later instructions can check. *In TRIADA:* S (sign) and O (overflow/carry), both trits. First seen: M1.

**Forward reference**: Using a label before the line that defines it, e.g. `JMP end` above `end:`. Two passes make it possible. First seen: M4.

**Frame**: One complete picture on the screen. Showing many frames per second makes movement. *In TRIADA:* 60 frames per second (SPEC §6). First seen: M0.

**Frontend**: The part of a program that faces the person: window, sound, keyboard. *In TRIADA:* `triada-desktop`, built with libGDX. The machine itself (`triada-core`) knows nothing about it. First seen: M0.

**Garbage collector**: The part of Java that finds objects no longer used and frees their memory. It costs time, so fast code creates few objects. First seen: M1.

**Gradle**: The build tool this project uses. Its settings are written in Kotlin script files (`*.gradle.kts`). First seen: M0.

**Gradle wrapper**: A tiny script (`gradlew`, `gradlew.bat`) plus a small jar, committed to the project, that downloads exactly the right Gradle version on first use. Nobody has to install Gradle by hand. First seen: M0.

**Grammar**: The rules for what a valid line looks like. First seen: M4.

**Halt**: Stop the CPU for good, until a reset. *In TRIADA:* HLT, or a fault. First seen: M3.

**Headless**: Running without any window, sound or keyboard, which is useful for automatic tests. *In TRIADA:* the `triada run` command (M5). First seen: M0.

**Horner's rule**: Building a number digit by digit: multiply what you have by the base, then add the next digit. `+-0` → 1, 1·3−1 = 2, 2·3+0 = 6. First seen: M1.

**Host**: The real computer the emulator runs on, as opposed to the imaginary TRIADA-85. First seen: M5.

**I/O register**: An address that isn't storage but a doorbell or window to hardware: reading or writing it talks to a device. *In TRIADA:* 729 slots from −20,412, 23 of them defined (SPEC §5). First seen: M2.

**Image (program image)**: The block of words a program occupies, together with its load address. *In TRIADA:* `TriImage`, stored in a `.tri` file. First seen: M4.

**Immediate**: A small number built into the instruction itself instead of coming from a register. *In TRIADA:* the imm5 field, −121 … +121. First seen: M3.

**Instruction**: One command for the CPU, such as "add R2 to R1". *In TRIADA:* one to four words. First seen: M3.

**Integration test**: A test that checks several parts working together, rather than one part alone. First seen: M5.

**Interface (Java)**: A list of methods a class promises to provide, so code can work with any class that keeps the promise. First seen: M2.

**Interpreter**: An emulator that handles one instruction at a time: fetch, decode, execute, repeat. First seen: M3.

**JAVA_HOME**: An environment variable telling scripts which Java installation to use. The `triada` launcher needs it to point to Java 25. First seen: M5.

**JDK (Java Development Kit)**: The tools needed to compile and run Java programs. *In TRIADA:* JDK 25. First seen: M0.

**JIT compiler (just-in-time)**: The part of Java that translates frequently used code into fast machine code while the program runs. That is why benchmarks warm up first. First seen: M3.

**JUnit**: The standard Java library for writing automatic tests. *In TRIADA:* JUnit 5. First seen: M0.

**JVM (Java Virtual Machine)**: The program that runs compiled Java code. It accepts start-up settings called JVM flags. First seen: M0.

**Kernel**: The core built-in software of a computer. *In TRIADA:* lives in ROM, written in TRIADA assembly (M8). First seen: M2.

**Label**: A name for an address in a program, written `name:` at the start of a line. First seen: M4.

**Launcher script**: A small script that starts a Java program with the right settings. *In TRIADA:* `triada` / `triada.bat`, made by `./gradlew :triada-cli:installDist`. First seen: M5.

**Least significant trit**: The rightmost trit, worth 1. *In TRIADA:* t0. First seen: M1.

**Lexer**: The part of a translator that cuts text into tokens. First seen: M4.

**libGDX**: A free Java library for games. It opens windows, draws pictures with the graphics card, plays sound and reads the keyboard. *In TRIADA:* used only by `triada-desktop`. First seen: M0.

**Listing**: A printout of the source next to the address and words each line produced. *In TRIADA:* the `.lst` file. First seen: M4.

**Little-endian**: Storing a multi-byte number with its least significant byte first. *In TRIADA:* the numbers in a `.tri` file. First seen: M4.

**Load address**: The address where a program image is placed in memory. *In TRIADA:* usually 243. First seen: M4.

**Lookup table**: A list of precomputed answers, so a calculation becomes a single array read. First seen: M1.

**LWJGL**: "Lightweight Java Game Library", the layer libGDX uses on desktop computers to talk to the graphics card (OpenGL), the window system (GLFW) and sound (OpenAL). First seen: M0.

**Magic number**: Fixed bytes at the start of a file that identify its format. *In TRIADA:* `TRIADA85`. First seen: M4.

**Maven Central**: The big public warehouse of Java libraries that Gradle downloads dependencies from. First seen: M0.

**Memory map**: The plan of which addresses are RAM, ROM, I/O or empty. *In TRIADA:* SPEC §2.2 and `MemoryMap`. First seen: M2.

**Mnemonic**: The short name of an instruction, such as `ADD` or `JSR`. First seen: M3.

**Module**: One self-contained part of a larger project, built separately, with its own code and tests. *In TRIADA:* `triada-core`, `triada-assembler`, `triada-cli` and `triada-desktop`. First seen: M0.

**Most significant trit**: The leftmost trit, with the biggest place value. *In TRIADA:* t11, worth 3^11 = 177,147. First seen: M1.

**Mutation check**: Breaking the code on purpose to confirm that the tests notice. If they don't, the tests are too weak. First seen: M1.

**Native library**: Code compiled directly for one kind of computer and operating system, not Java. LWJGL needs some to reach the graphics card. First seen: M0.

**Opcode**: The number that says which instruction it is. *In TRIADA:* 3 trits, −13 … +13, all 27 used. First seen: M3.

**OpenGL**: A standard way for programs to ask the graphics card to draw. libGDX uses it behind the scenes. First seen: M0.

**Operand**: An input to an instruction: a register, an immediate or an address. First seen: M3.

**Option (command line)**: A setting given on a command line, such as `--frames 10` or `-o out.tri`. First seen: M5.

**Parameterized test**: One test method run once for every row of a table of inputs. First seen: M2.

**Parser**: The part of a translator that recognises the structure of the tokens (label, instruction, operands …). First seen: M4.

**Peek**: Looking at a memory address without any side effect. *In TRIADA:* `Bus.peek()`, used by the debugger; also a monitor command (M8). First seen: M2.

**Plugin (Gradle)**: A ready-made bundle of build steps you switch on in a build file, for example `application`, which knows how to run a program. First seen: M0.

**Power-on state**: How a machine looks the instant it is switched on. *In TRIADA:* RAM all 0, registers 0, PC = −19,683 (SPEC §2.4). First seen: M2.

**Program counter (PC)**: The register holding the address of the next instruction, like a bookmark. *In TRIADA:* starts at −19,683 (SPEC §2.4). First seen: M3.

**Pseudo-instruction**: A convenient spelling the assembler translates into a real instruction, e.g. `INC R0` → `ADDI R0, 1`. First seen: M4.

**Pseudo-random number generator**: A formula that produces numbers that look random but are completely determined by the starting value. *In TRIADA:* xorshift32 behind the RAND register (D-011). First seen: M3.

**Queue (FIFO)**: A waiting line: first in, first out. *In TRIADA:* the KEY queue. First seen: M2.

**RAM (random-access memory)**: Memory that can be read and written. *In TRIADA:* addresses 0 … 59,048. First seen: M2.

**Recursive descent**: A way of writing a parser with one function per grammar rule; the functions call each other as the rules nest. First seen: M4.

**Reference implementation**: A slow but obviously correct version of an algorithm, used only in tests to check the fast version. First seen: M1.

**Register**: A small, very fast storage slot inside the processor, like a notepad on the CPU's desk. *In TRIADA:* R0–R8, one word each (SPEC §3.1). First seen: M1.

**Render loop**: The cycle a game runs over and over: update, draw a frame, repeat. *In TRIADA:* libGDX calls `TriadaApp.render()` about 60 times a second. First seen: M0.

**Return address**: Where the program continues after a subroutine; JSR pushes it on the stack and RET pops it. First seen: M3.

**Ring buffer**: A fixed-size array used as a queue whose start and end chase each other round and round, so nothing is ever moved or allocated. First seen: M2.

**ROM (read-only memory)**: Memory that can be read but not changed by programs. *In TRIADA:* addresses −19,683 … −1, holding the kernel. First seen: M2.

**Rotate (ROT)**: *In TRIADA:* turn every trit one step around a three-hour clock: − → 0 → + → −. First seen: M1.

**Round trip**: Converting something there and back and getting exactly what you started with. *In TRIADA:* assemble → disassemble → assemble. First seen: M4.

**Seed**: The starting value of a pseudo-random number generator. The same seed always gives the same sequence. First seen: M3.

**Shift (SHT)**: Slide all digits left (multiply by the base) or right (drop the last digits). *In TRIADA:* a right shift rounds to the nearest number. First seen: M1.

**Side effect**: A change an operation makes beyond giving its answer. *In TRIADA:* reading KEY removes the key. First seen: M2.

**Smoke test**: A very small test that only checks "does it start at all?", like switching on a new appliance to see whether smoke comes out. First seen: M0.

**Source code**: The text of a program as people write it. First seen: M0.

**Spec (specification)**: The exact written description of how something must behave. *In TRIADA:* `docs/SPEC.md`, the law of the machine. First seen: M0.

**Sprite**: A small picture that moves freely over the background. *In TRIADA:* 27 sprites, table at RAM 44,600 (SPEC §4.7). First seen: M2.

**Stack**: A pile of notes where the last one added is the first one taken, used for return addresses and saved values. *In TRIADA:* grows downward from 59,049. First seen: M2.

**Stack pointer**: The register that remembers where the top of the stack is. *In TRIADA:* R8, also called SP. First seen: M2.

**stderr (standard error)**: A program's second output channel, for messages about the run (errors, notes), kept apart from its real output. First seen: M5.

**stdout (standard output)**: A program's main output channel. *In TRIADA:* `triada run` prints the debug-port text there. First seen: M5.

**Subroutine**: A piece of program that can be called from anywhere and returns to where it was called from. *In TRIADA:* JSR calls, RET returns. First seen: M3.

**Symbol**: A name the assembler knows the value of: a label, a constant or a built-in name like `PUTCHAR`. First seen: M4.

**System variables**: RAM cells where the built-in software keeps its own notes. *In TRIADA:* addresses 0 … 242. First seen: M2.

**Ternary**: Counting with three digits. *Balanced* ternary uses −1, 0 and +1 (written `-`, `0`, `+`). *In TRIADA:* everything. First seen: M0.

**Test (automatic test)**: A small program that checks whether another piece of code does what it should, and complains loudly if not. First seen: M0.

**Tile**: A small fixed picture (8×8 pixels) used as a building block for the screen. *In TRIADA:* 243 tiles at RAM 40,000 (SPEC §4.4). First seen: M2.

**Tile map**: The grid saying which tile goes in each square of the screen. *In TRIADA:* 27 × 18 cells at RAM 44,000. First seen: M2.

**Time-out**: Stopping something because it took longer than allowed. *In TRIADA:* `triada run` gives up after 3,600 frames without `--frames` (exit code 4). First seen: M5.

**Token**: One piece of a source line after the lexer has cut it up: a name, a number, a comma … First seen: M4.

**Toolchain (Gradle)**: Gradle's setting for "compile and test with exactly this Java version", even if a different one is installed as the default. First seen: M0.

**Trit**: One ternary digit: −1, 0 or +1. The ternary cousin of the bit. First seen: M0.

**Trit-wise operation**: An operation that treats each trit column separately, ignoring the number's value. *In TRIADA:* MIN, MAX, TMUL and ROT. First seen: M1.

**Truth table**: A table listing an operation's result for every combination of inputs. First seen: M1.

**Tryte**: Six trits, the ternary cousin of the byte. Range −364 … +364. First seen: M1.

**Two's complement**: The usual way binary computers store negative numbers, using the top bit as a sign. Balanced ternary doesn't need it. First seen: M1.

**Two-pass assembler**: An assembler that first lays out all addresses (pass 1) and then encodes everything (pass 2), so labels can be used before they are defined. First seen: M4.

**Unmapped**: An address with nothing behind it: reads 0, writes have no effect. First seen: M2.

**Version catalog**: One file (`gradle/libs.versions.toml`) that lists every external library and its version, so they aren't scattered across build files. First seen: M0.

**Version control (git)**: A system that remembers every saved version of a project, so you can see what changed, when and why, and go back. First seen: M0.

**Vsync**: Waiting for the monitor to finish showing one picture before handing it the next, so the image never tears halfway. First seen: M0.

**Word**: The amount of data a computer handles in one piece. *In TRIADA:* 12 trits, −265,720 … +265,720; every register and memory cell holds one word. First seen: M1.

**Wrap (overflow)**: When a result is too big for its space and rolls around to the other end, like a car's odometer. *In TRIADA:* SPEC §1.3. First seen: M1.
