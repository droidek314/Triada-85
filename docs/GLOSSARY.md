# Glossary

Every technical word used in the documents, explained simply, in alphabetical order. "First seen" names the milestone where the word first appears.

---

**Address**: The number of a memory cell, like a house number on a street. *In TRIADA:* a word, −265,720 … +265,720, with 0 in the middle (SPEC §2.1). First seen: M2.

**Annotation**: A note in Java code, starting with `@`, that a tool reads, e.g. `@Test` for JUnit. *In TRIADA:* `@JSBody` and `@JSByRef` tell TeaVM how to call JavaScript. First seen: M12.

**Assembler**: A program that turns assembly language text into machine words. *In TRIADA:* `tasm`, the `Assembler` class (M4). First seen: M4.

**Assembly language**: The thinnest layer above machine code: one line per instruction, written with mnemonics and names instead of numbers. First seen: M4.

**Asset**: A file an app reads at run time, like a picture or a text. *In TRIADA:* the web version's assets are the program sources in `programs/`, downloaded with the page. First seen: M12.

**Audio thread**: A separate worker inside a program that does nothing but feed the sound card. *In TRIADA:* `SoundOutput`'s thread `triada-audio` (D-025). First seen: M7.

**Auto-start (AUTORUN)**: A program started by the ROM right after boot. *In TRIADA:* the host writes the program's address into the system variable AUTORUN (D-028). First seen: M8.

**Autopilot (in tests)**: A few lines of test code that play a game by themselves, choosing joystick and button inputs from what they read in the machine's memory. *In TRIADA:* plays TRIZMEYKA and OBLAKA for two minutes each. First seen: M10.

**Backend (libGDX)**: The part of libGDX that connects it to one platform. *In TRIADA:* LWJGL3 for the desktop window, gdx-teavm for the browser. First seen: M12.

**Balanced ternary**: Base 3 with the digits −1, 0 and +1 instead of 0, 1 and 2. Negative numbers need no minus sign, and flipping every digit negates a number. *In TRIADA:* how every number is stored (SPEC §1.1). First seen: M1.

**Benchmark**: A program that measures how fast something runs. *In TRIADA:* `./gradlew :triada-core:benchmark` (M3). First seen: M3.

**Binary**: Counting with two digits, 0 and 1. Almost every real computer works this way, because a switch is easily either off or on. *In TRIADA:* the road not taken; TRIADA uses ternary instead. First seen: M0.

**Bit**: One binary digit, 0 or 1. *In TRIADA:* the font is stored as bits and turned into trit pixels at boot. First seen: M8.

**BOM (bill of materials)**: A list that pins every part of a library family to versions that work together. *In TRIADA:* the JUnit BOM. First seen: M0.

**Boot**: Starting a computer up: from power-on to the point where it waits for you. *In TRIADA:* about 48 frames: font, chime, boot screen, monitor. First seen: M8.

**Border (video)**: The frame of one colour around the screen. *In TRIADA:* 12 pixels wide, colour from the BORDER register (D-020). First seen: M6.

**Boundary test**: A test at the exact edges of a range (first, last, one beyond), where off-by-one mistakes hide. First seen: M2.

**Branch**: Another word for a jump, especially a conditional one. *In TRIADA:* BR3 is a three-way branch. First seen: M3.

**Breakpoint**: A mark on an instruction's address meaning "stop before executing this". *In TRIADA:* checked by `Cpu.run` before every instruction; set by clicking a line in the debugger (M9). First seen: M9.

**Build**: Turning source code into something that runs, usually running the tests on the way. *In TRIADA:* `./gradlew build` builds and tests everything. First seen: M0.

**Build tool**: A program that knows how to build a project: what to compile, in which order, and which libraries to download. *In TRIADA:* Gradle. First seen: M0.

**Bus**: The path that carries every read and write between the processor and memory or devices. *In TRIADA:* the `Bus` class. First seen: M2.

**Bytecode**: The compact instructions that the Java compiler produces and the Java virtual machine runs. TeaVM translates bytecode, not source, into JavaScript. First seen: M6.

**Call depth**: How many subroutine calls deep a program currently is. *In TRIADA:* visible in SP, which is lower the deeper the calls go. First seen: M9.

**Calling convention**: The agreement on which registers carry a routine's arguments and result, and which it must keep unchanged. *In TRIADA:* in R0–R2, out R0, R3–R8 preserved (SPEC §8.1). First seen: M8.

**Canonical form**: The one standard way of writing something. *In TRIADA:* an instruction word whose unused fields are all 0. First seen: M4.

**Canvas**: The drawing area of a web page. *In TRIADA:* the web version shows the machine's screen in one, 1 to 4 times its size. First seen: M12.

**Capture (mode)**: Saving a program's picture to a file instead of showing it. *In TRIADA:* the desktop's `--capture FILE` (M11, D-037). First seen: M11.

**Carry**: The digit that falls off the top when a result is too big for its space. *In TRIADA:* a trit (−1, 0 or +1), stored in flag O by ADD, SUB and ADDI. First seen: M1.

**Cartridge**: A plug-in module that adds memory or software to a computer. *In TRIADA:* lore only; addresses 59,049 and up are reserved for them. First seen: M2.

**Class path**: The list of places (folders and jar files) where Java looks for classes and resource files. A browser has none. First seen: M12.

**CLI (command-line interface)**: A program you control by typing commands in a terminal instead of clicking. *In TRIADA:* the `triada` tool in `triada-cli`. First seen: M0.

**Clipping**: Cutting off the parts of a picture that fall outside the visible area. *In TRIADA:* sprites are clipped to the 216 × 144 screen. First seen: M6.

**Clipping (audio)**: Distortion when a sound's samples exceed the allowed range and get squashed flat. *In TRIADA:* impossible by design: three voices peak at ±30,000. First seen: M7.

**Code generation**: A build step that writes source code from other files. *In TRIADA:* `generateRom` turns the assembled ROM into a Java class (D-029). First seen: M8.

**Collision box (hit box)**: The area around a game object that counts as "touching". *In TRIADA:* OBLAKA's `near` test, x and y closer than 6 pixels. First seen: M10.

**Commit**: A saved snapshot of the whole project in version control (git), with a message saying what changed. *In TRIADA:* at least one commit per milestone. First seen: M0.

**Commutative**: Said of operations whose order doesn't matter, like 2 + 3 = 3 + 2. *In TRIADA:* TROIKA's flip and rotate moves are not commutative, which makes the puzzle harder. First seen: M10.

**Compile**: Translate source code that people write (here, Java) into a form the computer can run. First seen: M0.

**Compiler**: A program that translates a program from one language into another, usually into one a machine can run. *In TRIADA:* `javac` (Java to bytecode), TeaVM (bytecode to JavaScript); the assembler does the same job for TRIADA assembly. First seen: M6.

**Conditional jump**: A jump that only happens if a condition on the flags holds. *In TRIADA:* JIF and its aliases JN, JZ, JP, JNZ, JLE, JGE. First seen: M3.

**Constant (assembler)**: A name for a number, defined with `NAME = value`. First seen: M4.

**CPU (central processing unit)**: The part of a computer that reads instructions from memory and carries them out, one after another. *In TRIADA:* the `Cpu` class. First seen: M3.

**CRT (cathode-ray tube)**: The glass tube of old televisions and monitors, which drew the picture with an electron beam on glowing phosphor. *In TRIADA:* the optional CRT look, F2. First seen: M11.

**Cycle**: One tick of a processor's clock. *In TRIADA:* every instruction takes exactly one cycle. First seen: M3.

**Cycle budget**: How many cycles the CPU may use per frame. *In TRIADA:* 59,049 = 3^10 (SPEC §6). First seen: M3.

**Debug port**: I/O registers through which a program sends text and numbers to the host instead of the screen. *In TRIADA:* DEBUG_CHAR, DEBUG_NUM, HOST_EXIT (registers 20–22). First seen: M5.

**Debugger**: A tool for stopping a program and looking inside the machine: registers, memory, the code around the current instruction. *In TRIADA:* F1 in the desktop window. First seen: M9.

**Decode**: Splitting an instruction word into its fields (opcode, registers, immediate). First seen: M3.

**Dependency**: A ready-made library your project uses instead of writing that code itself. *In TRIADA:* libGDX (window, sound, input) and JUnit (tests), and nothing else without a DECISIONS.md entry. First seen: M0.

**Depth-first search**: A way of searching by following one possibility all the way down before trying the next. *In TRIADA:* a test uses it to find TROIKA solutions within par. First seen: M10.

**Device**: A piece of hardware the processor talks to through I/O registers. *In TRIADA:* anything implementing `IoDevice`, such as `InputDevice`. First seen: M2.

**Directive**: A line that tells the assembler what to do (`.org`, `.str`, `.include` …) rather than producing a CPU instruction. First seen: M4.

**Disassembler**: A program that turns machine words back into assembly text. *In TRIADA:* the `Disassembler` class, used by the debugger. First seen: M4.

**Drag and drop**: Moving a file onto a window with the mouse to open it there. *In TRIADA:* "inserting a triskette" (D-032). First seen: M9.

**Edge-triggered input**: Reacting when a button *becomes* pressed, instead of for as long as it is held. *In TRIADA:* the games compare a button with its value in the previous frame. First seen: M10.

**Emulator**: A program that imitates a different computer closely enough to run its programs. First seen: M3.

**End-to-end test**: A test that goes through the whole system the way a user would, e.g. assembling and running a real file through the real command line. First seen: M5.

**Enum**: A Java type with a fixed list of named values, such as `Region` with UNMAPPED, IO, ROM and RAM. First seen: M2.

**Envelope**: How a note's volume changes over time, e.g. loud at the start, then fading. *In TRIADA:* `piano.tasm` fades each note over 52 frames. First seen: M7.

**Environment variable**: A named setting the operating system gives every program, such as `JAVA_HOME`. First seen: M5.

**Escape sequence**: A backslash code for a special character inside quotes, such as `\n` for a new line. First seen: M4.

**Execute**: Carrying out an instruction. First seen: M3.

**Exhaustive test**: A test that tries every possible input instead of a sample. *In TRIADA:* all 531,441 words in `WordTest`. First seen: M1.

**Exit code**: The number a program hands back to the operating system when it ends; 0 means success. *In TRIADA:* `triada run` returns the value a program writes to HOST_EXIT. First seen: M5.

**Expression**: A small calculation in the source, such as `IO_BASE + KEY` or `end - start`. *In TRIADA:* numbers and names combined with + and −. First seen: M4.

**Fault**: An error that stops the CPU, recorded with a code and the address of the guilty instruction. *In TRIADA:* fault 1 = invalid JIF condition (D-010). First seen: M3.

**Fetch**: Reading the next instruction from memory at the address in PC. First seen: M3.

**Fixed time step**: Running a simulation in equal steps (here 1/60 s) whatever the display's refresh rate, by counting elapsed time. *In TRIADA:* `FrameClock`. First seen: M6.

**Flag**: A one-digit note the processor keeps about the last calculation, which later instructions can check. *In TRIADA:* S (sign) and O (overflow/carry), both trits. First seen: M1.

**Flipping**: Turning a picture upside down. *In TRIADA:* sprite FLAGS = −1. First seen: M6.

**Font**: A set of character pictures. *In TRIADA:* font8x8 (public domain), in tiles 32–126 (D-026). First seen: M8.

**Forward reference**: Using a label before the line that defines it, e.g. `JMP end` above `end:`. Two passes make it possible. First seen: M4.

**Fragment shader**: The part of a shader that decides the colour of each pixel. *In TRIADA:* `CrtEffect.FRAGMENT`. First seen: M11.

**Frame**: One complete picture on the screen. Showing many frames per second makes movement. *In TRIADA:* 60 frames per second (SPEC §6). First seen: M0.

**Frame buffer**: The block of memory that holds a finished picture. *In TRIADA:* `VideoChip.frame()`, 240 × 168 RGB values. First seen: M6.

**Frequency**: How many times per second a pattern repeats, measured in hertz (Hz). 440 Hz is the note A. First seen: M7.

**Frontend**: The part of a program that faces the person: window, sound, keyboard. *In TRIADA:* `triada-desktop`, built with libGDX. The machine itself (`triada-core`) knows nothing about it. First seen: M0.

**Garbage collector**: The part of Java that finds objects no longer used and frees their memory. It costs time, so fast code creates few objects. First seen: M1.

**GIF**: An old image format with a palette of at most 256 colours that can also hold an animation. *In TRIADA:* the README's `oblaka.gif`, with exactly the machine's 27 colours. First seen: M11.

**GitHub Actions**: GitHub's service that runs a *workflow* (a list of automatic steps) on its own computers, for example on every push. *In TRIADA:* builds, tests and publishes the web version (`pages.yml`). First seen: M12.

**GitHub Pages**: GitHub's free hosting for static websites. *In TRIADA:* https://droidek314.github.io/Triada-85/. First seen: M12.

**Glyph**: The picture of one character in a font. First seen: M8.

**Golden image**: A stored, checked-by-a-person picture that new output is compared with pixel by pixel; any difference fails the test. First seen: M6.

**Gradle**: The build tool this project uses. Its settings are written in Kotlin script files (`*.gradle.kts`). First seen: M0.

**Gradle wrapper**: A tiny script (`gradlew`, `gradlew.bat`) plus a small jar, committed to the project, that downloads exactly the right Gradle version on first use. Nobody has to install Gradle by hand. First seen: M0.

**Grammar**: The rules for what a valid line looks like. First seen: M4.

**Halt**: Stop the CPU for good, until a reset. *In TRIADA:* HLT, or a fault. First seen: M3.

**Headless**: Running without any window, sound or keyboard, which is useful for automatic tests. *In TRIADA:* the `triada run` command (M5). First seen: M0.

**Heuristic**: A rule of thumb that usually, but not always, gives the right answer. *In TRIADA:* disassembling backwards from PC. First seen: M9.

**Horner's rule**: Building a number digit by digit: multiply what you have by the base, then add the next digit. `+-0` → 1, 1·3−1 = 2, 2·3+0 = 6. First seen: M1.

**Host**: The real computer the emulator runs on, as opposed to the imaginary TRIADA-85. First seen: M5.

**HUD (head-up display)**: The score, lives and similar information drawn on top of a game's picture. *In TRIADA:* OBLAKA draws it with sprites so it doesn't scroll. First seen: M10.

**I/O register**: An address that isn't storage but a doorbell or window to hardware: reading or writing it talks to a device. *In TRIADA:* 729 slots from −20,412, 23 of them defined (SPEC §5). First seen: M2.

**Image (program image)**: The block of words a program occupies, together with its load address. *In TRIADA:* `TriImage`, stored in a `.tri` file. First seen: M4.

**Immediate**: A small number built into the instruction itself instead of coming from a register. *In TRIADA:* the imm5 field, −121 … +121. First seen: M3.

**Include library**: A source file of shared routines that programs paste into themselves with `.include`. *In TRIADA:* `programs/lib/game.tasm`. First seen: M10.

**Input focus**: Which window currently receives the keyboard. *In TRIADA:* losing it releases all joystick directions and buttons. First seen: M7.

**Instruction**: One command for the CPU, such as "add R2 to R1". *In TRIADA:* one to four words. First seen: M3.

**Integer scaling**: Enlarging a picture by a whole number (2×, 3×, 4× …) so every pixel stays exactly the same size. First seen: M6.

**Integration test**: A test that checks several parts working together, rather than one part alone. First seen: M5.

**Interface (Java)**: A list of methods a class promises to provide, so code can work with any class that keeps the promise. First seen: M2.

**Interpreter**: An emulator that handles one instruction at a time: fetch, decode, execute, repeat. First seen: M3.

**Invulnerability**: A short time after being hit in which a game's hero can't be hit again, usually shown by blinking. *In TRIADA:* two seconds in OBLAKA. First seen: M10.

**JAVA_HOME**: An environment variable telling scripts which Java installation to use. The `triada` launcher needs it to point to Java 25. First seen: M5.

**JavaScript**: The programming language that web browsers run. *In TRIADA:* TeaVM translates the Java program into one JavaScript file, `app.js`. First seen: M12.

**JDK (Java Development Kit)**: The tools needed to compile and run Java programs. *In TRIADA:* JDK 25. First seen: M0.

**JIT compiler (just-in-time)**: The part of Java that translates frequently used code into fast machine code while the program runs. That is why benchmarks warm up first. First seen: M3.

**Jump table**: A list of jump instructions at fixed addresses, so the routines they lead to can move without breaking the programs that call them. *In TRIADA:* the first 14 ROM words (SPEC §8.1). First seen: M8.

**JUnit**: The standard Java library for writing automatic tests. *In TRIADA:* JUnit 5. First seen: M0.

**JVM (Java Virtual Machine)**: The program that runs compiled Java code. It accepts start-up settings called JVM flags. First seen: M0.

**Kernel**: The core built-in software of a computer. *In TRIADA:* lives in ROM, written in TRIADA assembly (M8). First seen: M2.

**Label**: A name for an address in a program, written `name:` at the start of a line. First seen: M4.

**Latency**: The delay between a cause and its effect, e.g. pressing a key and hearing the note. First seen: M7.

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

**Mask**: A picture or word made only of "on" and "off" values that selects which parts of something else to use or colour. *In TRIADA:* TROIKA's move masks (+1 = this cell) and the games' tile masks (pixels 0/1). First seen: M10.

**Maven Central**: The big public warehouse of Java libraries that Gradle downloads dependencies from. First seen: M0.

**Memory map**: The plan of which addresses are RAM, ROM, I/O or empty. *In TRIADA:* SPEC §2.2 and `MemoryMap`. First seen: M2.

**Mirroring**: Reversing a picture left to right. *In TRIADA:* a negative tile number in the tile map or a sprite's TILE. First seen: M6.

**Mixing**: Adding several sounds together into one stream of samples. First seen: M7.

**Mnemonic**: The short name of an instruction, such as `ADD` or `JSR`. First seen: M3.

**Module**: One self-contained part of a larger project, built separately, with its own code and tests. *In TRIADA:* `triada-core`, `triada-assembler`, `triada-rom`, `triada-cli`, `triada-app`, `triada-desktop` and `triada-web`. First seen: M0.

**Monitor (program)**: A tiny built-in command line for looking at and changing memory and running programs. *In TRIADA:* HELP, PEEK, POKE, RUN, CLS, TERN (SPEC §8.3). First seen: M8.

**Monospace font**: A font whose characters are all equally wide, so text lines up in columns. First seen: M9.

**Most significant trit**: The leftmost trit, with the biggest place value. *In TRIADA:* t11, worth 3^11 = 177,147. First seen: M1.

**Mutation check**: Breaking the code on purpose to confirm that the tests notice. If they don't, the tests are too weak. First seen: M1.

**Native library**: Code compiled directly for one kind of computer and operating system, not Java. LWJGL needs some to reach the graphics card. First seen: M0.

**Nearest-neighbour filtering**: Enlarging a picture by copying the nearest original pixel, with no smoothing, so pixel art stays crisp. First seen: M6.

**Node.js**: A program that runs JavaScript outside a web browser. Used for the M6 web spike. First seen: M6.

**Noise (audio)**: A random, hissing sound. *In TRIADA:* random trits (−1, 0, +1), a new one every quarter period (D-023). First seen: M7.

**Off-screen frame buffer**: A picture in the graphics card's memory that can be drawn into like the screen, but isn't shown. *In TRIADA:* used by the capture mode. First seen: M11.

**Opcode**: The number that says which instruction it is. *In TRIADA:* 3 trits, −13 … +13, all 27 used. First seen: M3.

**OpenGL**: A standard way for programs to ask the graphics card to draw. libGDX uses it behind the scenes. First seen: M0.

**Operand**: An input to an instruction: a register, an immediate or an address. First seen: M3.

**Option (command line)**: A setting given on a command line, such as `--frames 10` or `-o out.tri`. First seen: M5.

**Palette**: The list of colours a machine can show. *In TRIADA:* 27 colours, each one's three trits giving red, green and blue (SPEC §4.2). First seen: M6.

**Par**: A number of moves within which a puzzle is known to be solvable, as in golf. *In TRIADA:* TROIKA's scramble length. First seen: M10.

**Parameterized test**: One test method run once for every row of a table of inputs. First seen: M2.

**Parser**: The part of a translator that recognises the structure of the tokens (label, instruction, operands …). First seen: M4.

**PCM (pulse-code modulation)**: Sound stored as a plain list of samples. *In TRIADA:* 16-bit mono, 44,100 per second. First seen: M7.

**Peek**: Looking at a memory address without any side effect. *In TRIADA:* `Bus.peek()`, used by the debugger; also a monitor command (M8). First seen: M2.

**Pending wrap**: Moving to the next line only when another character arrives after a full line, so a full line plus a newline gives no empty line. First seen: M8.

**Phase**: Where in its repeating pattern a wave currently is. *In TRIADA:* an exact integer 0 … 44,099 per voice. First seen: M7.

**Phosphor**: The material on a CRT's glass that glows red, green or blue when the electron beam hits it. First seen: M11.

**Pixel**: One dot of a picture. *In TRIADA:* a 3-trit value from −13 to 13; four pixels fit in one word. First seen: M6.

**Plugin (Gradle)**: A ready-made bundle of build steps you switch on in a build file, for example `application`, which knows how to run a program. First seen: M0.

**Power-on state**: How a machine looks the instant it is switched on. *In TRIADA:* RAM all 0, registers 0, PC = −19,683 (SPEC §2.4). First seen: M2.

**Program counter (PC)**: The register holding the address of the next instruction, like a bookmark. *In TRIADA:* starts at −19,683 (SPEC §2.4). First seen: M3.

**Pseudo-instruction**: A convenient spelling the assembler translates into a real instruction, e.g. `INC R0` → `ADDI R0, 1`. First seen: M4.

**Pseudo-random number generator**: A formula that produces numbers that look random but are completely determined by the starting value. *In TRIADA:* xorshift32 behind the RAND register (D-011). First seen: M3.

**Public domain**: Free of copyright: anyone may use, change and share it for any purpose. First seen: M8.

**Push (git)**: Sending new commits from your computer to a shared repository such as GitHub. *In TRIADA:* every push to `master` rebuilds the web version. First seen: M12.

**Query string**: The part of a web address after `?`, which the page can read. *In TRIADA:* `?program=oblaka` picks the program to start. First seen: M12.

**Queue (FIFO)**: A waiting line: first in, first out. *In TRIADA:* the KEY queue. First seen: M2.

**RAM (random-access memory)**: Memory that can be read and written. *In TRIADA:* addresses 0 … 59,048. First seen: M2.

**Recursive descent**: A way of writing a parser with one function per grammar rule; the functions call each other as the rules nest. First seen: M4.

**Reference implementation**: A slow but obviously correct version of an algorithm, used only in tests to check the fast version. First seen: M1.

**Register**: A small, very fast storage slot inside the processor, like a notepad on the CPU's desk. *In TRIADA:* R0–R8, one word each (SPEC §3.1). First seen: M1.

**Render loop**: The cycle a game runs over and over: update, draw a frame, repeat. *In TRIADA:* libGDX calls `TriadaApp.render()` about 60 times a second. First seen: M0.

**Return address**: Where the program continues after a subroutine; JSR pushes it on the stack and RET pops it. First seen: M3.

**RGB**: Describing a colour by how much red, green and blue light it contains. First seen: M6.

**Ring buffer**: A fixed-size array used as a queue whose start and end chase each other round and round, so nothing is ever moved or allocated. First seen: M2.

**ROM (read-only memory)**: Memory that can be read but not changed by programs. *In TRIADA:* addresses −19,683 … −1, holding the kernel. First seen: M2.

**Rotate (ROT)**: *In TRIADA:* turn every trit one step around a three-hour clock: − → 0 → + → −. First seen: M1.

**Round trip**: Converting something there and back and getting exactly what you started with. *In TRIADA:* assemble → disassemble → assemble. First seen: M4.

**Sample (audio)**: One number giving the loudspeaker's position at one instant. *In TRIADA:* −32,768 … 32,767, 735 per frame. First seen: M7.

**Sample rate**: How many samples per second a sound has. *In TRIADA:* 44,100 (the CD rate). First seen: M7.

**Scanline**: One of the horizontal lines a CRT draws its picture with, with thin darker gaps between them. First seen: M11.

**Scrolling**: Sliding the whole background so a different part of it is visible. *In TRIADA:* SCROLL_X/SCROLL_Y; what slides out comes back on the other side. First seen: M6.

**Seed**: The starting value of a pseudo-random number generator. The same seed always gives the same sequence. First seen: M3.

**Shader**: A small program that runs on the graphics card, for example once for every pixel. *In TRIADA:* the CRT look (`CrtEffect`). First seen: M11.

**Shift (SHT)**: Slide all digits left (multiply by the base) or right (drop the last digits). *In TRIADA:* a right shift rounds to the nearest number. First seen: M1.

**Side effect**: A change an operation makes beyond giving its answer. *In TRIADA:* reading KEY removes the key. First seen: M2.

**Single step**: Executing exactly one instruction and stopping again. First seen: M9.

**Skin**: The set of looks (fonts, colours, backgrounds) a user interface's widgets use. *In TRIADA:* built in code for the debugger (D-031). First seen: M9.

**Smoke test**: A very small test that only checks "does it start at all?", like switching on a new appliance to see whether smoke comes out. First seen: M0.

**Source code**: The text of a program as people write it. First seen: M0.

**Spec (specification)**: The exact written description of how something must behave. *In TRIADA:* `docs/SPEC.md`, the law of the machine. First seen: M0.

**Spike**: A quick, throwaway experiment that answers one question before committing to a plan. *In TRIADA:* the M6 web spike (D-022). First seen: M6.

**Sprite**: A small picture that moves freely over the background. *In TRIADA:* 27 sprites, table at RAM 44,600 (SPEC §4.7). First seen: M2.

**Stack**: A pile of notes where the last one added is the first one taken, used for return addresses and saved values. *In TRIADA:* grows downward from 59,049. First seen: M2.

**Stack pointer**: The register that remembers where the top of the stack is. *In TRIADA:* R8, also called SP. First seen: M2.

**Static website**: A site made only of files that the server hands out unchanged; everything runs in the visitor's browser. First seen: M12.

**stderr (standard error)**: A program's second output channel, for messages about the run (errors, notes), kept apart from its real output. First seen: M5.

**stdout (standard output)**: A program's main output channel. *In TRIADA:* `triada run` prints the debug-port text there. First seen: M5.

**Step over**: Executing a whole subroutine call as if it were one step, stopping at the instruction after the call. First seen: M9.

**Subroutine**: A piece of program that can be called from anywhere and returns to where it was called from. *In TRIADA:* JSR calls, RET returns. First seen: M3.

**Symbol**: A name the assembler knows the value of: a label, a constant or a built-in name like `PUTCHAR`. First seen: M4.

**System variable**: A RAM word the built-in software uses for its own state. *In TRIADA:* AUTORUN, CURSOR_X, CURSOR_Y, RUN_DONE, LINEBUF (in RAM 0 … 242). First seen: M8.

**System variables**: RAM cells where the built-in software keeps its own notes. *In TRIADA:* addresses 0 … 242. First seen: M2.

**Tail call**: Ending a routine by jumping to another one, whose RET then returns directly to the original caller. First seen: M8.

**TeaVM**: A tool that translates Java bytecode into JavaScript (or WebAssembly). The libGDX community web backend gdx-teavm is built on it. First seen: M6.

**Ternary**: Counting with three digits. *Balanced* ternary uses −1, 0 and +1 (written `-`, `0`, `+`). *In TRIADA:* everything. First seen: M0.

**Ternary square**: TRIADA's signature waveform: three levels, +1, 0, −1, 0, in four equal quarters of each period (SPEC §7). First seen: M7.

**Test (automatic test)**: A small program that checks whether another piece of code does what it should, and complains loudly if not. First seen: M0.

**Text block**: A Java string written between `"""` marks over several lines, keeping its line breaks. *In TRIADA:* the CRT shader's source in `CrtEffect`. First seen: M12.

**Texture**: A picture uploaded to the graphics card so it can be drawn quickly. First seen: M6.

**Thread**: An independent line of work inside a program; several threads run at the same time. First seen: M7.

**Thread-safe**: Safe to use from several threads at once without mixing up data. *In TRIADA:* `SampleRing`. First seen: M7.

**Tile**: A small fixed picture (8×8 pixels) used as a building block for the screen. *In TRIADA:* 243 tiles at RAM 40,000 (SPEC §4.4). First seen: M2.

**Tile map**: The grid saying which tile goes in each square of the screen. *In TRIADA:* 27 × 18 cells at RAM 44,000. First seen: M2.

**Time-out**: Stopping something because it took longer than allowed. *In TRIADA:* `triada run` gives up after 3,600 frames without `--frames` (exit code 4). First seen: M5.

**Token**: One piece of a source line after the lexer has cut it up: a name, a number, a comma … First seen: M4.

**Toolchain (Gradle)**: Gradle's setting for "compile and test with exactly this Java version", even if a different one is installed as the default. First seen: M0.

**Transparency**: A colour that is not drawn, so whatever is behind shows through. *In TRIADA:* gray (value 0) in sprites. First seen: M6.

**Trit**: One ternary digit: −1, 0 or +1. The ternary cousin of the bit. First seen: M0.

**Trit-wise operation**: An operation that treats each trit column separately, ignoring the number's value. *In TRIADA:* MIN, MAX, TMUL and ROT. First seen: M1.

**Truth table**: A table listing an operation's result for every combination of inputs. First seen: M1.

**Tryte**: Six trits, the ternary cousin of the byte. Range −364 … +364. First seen: M1.

**Two's complement**: The usual way binary computers store negative numbers, using the top bit as a sign. Balanced ternary doesn't need it. First seen: M1.

**Two-pass assembler**: An assembler that first lays out all addresses (pass 1) and then encodes everything (pass 2), so labels can be used before they are defined. First seen: M4.

**Type-in listing**: A program printed in a magazine for readers to type into their computers. *In TRIADA:* "Triangle of Threes" in chapter 7 of the manual, as 143 POKE lines. First seen: M11.

**Typed array**: A JavaScript array that holds only plain numbers of one kind, compactly, like a Java `short[]`. *In TRIADA:* each frame's sound samples are passed to the page as one. First seen: M12.

**Underrun / Overrun**: The sound card running out of samples (a gap) / more samples waiting than allowed (delay). *In TRIADA:* `SampleRing` holds the last value on underrun and drops the oldest on overrun. First seen: M7.

**Uniform (shader)**: A setting a program passes to a shader, the same for every pixel. *In TRIADA:* `u_curvature`, `u_scanlines`. First seen: M11.

**Unmapped**: An address with nothing behind it: reads 0, writes have no effect. First seen: M2.

**Up to date (build tool)**: A build tool's judgement that a task's inputs haven't changed since the last run, so the task can be skipped. Wrong when the tool doesn't know all the inputs. First seen: M10.

**Version catalog**: One file (`gradle/libs.versions.toml`) that lists every external library and its version, so they aren't scattered across build files. First seen: M0.

**Version control (git)**: A system that remembers every saved version of a project, so you can see what changed, when and why, and go back. First seen: M0.

**Video chip**: The part of a computer that turns memory contents into a picture, many times per second. *In TRIADA:* `VideoChip`, once per frame (SPEC §4). First seen: M6.

**Vignette**: A darkening towards the corners of a picture. First seen: M11.

**Voice**: One independent sound generator with its own pitch, volume and waveform. *In TRIADA:* three (V1–V3). First seen: M7.

**Vsync**: Waiting for the monitor to finish showing one picture before handing it the next, so the image never tears halfway. First seen: M0.

**WAV**: A common sound file format: a short header followed by PCM samples. *In TRIADA:* `triada run … --dump-audio`. First seen: M7.

**Waveform**: The shape of a sound's repeating pattern. *In TRIADA:* ternary square, triangle or noise. First seen: M7.

**Web Audio**: The browser's built-in sound system (`AudioContext`). It stays silent until the visitor clicks or types. *In TRIADA:* the page's `triadaSound` player (D-041). First seen: M12.

**WebGL**: The browser's version of OpenGL, for drawing with the graphics card. *In TRIADA:* libGDX draws the screen and the CRT shader with it on the web. First seen: M12.

**Widget**: One piece of a user interface: a label, a button, a text field … First seen: M9.

**Word**: The amount of data a computer handles in one piece. *In TRIADA:* 12 trits, −265,720 … +265,720; every register and memory cell holds one word. First seen: M1.

**Workflow**: A list of automatic steps in a file in `.github/workflows/`, run by GitHub Actions. *In TRIADA:* `pages.yml`. First seen: M12.

**Wrap (overflow)**: When a result is too big for its space and rolls around to the other end, like a car's odometer. *In TRIADA:* SPEC §1.3. First seen: M1.
