# Glossary

Every technical word used in the documents, explained simply, in alphabetical order. "First seen" names the milestone where the word first appears.

---

**Binary**: Counting with two digits, 0 and 1. Almost every real computer works this way, because a switch is easily either off or on. *In TRIADA:* the road not taken; TRIADA uses ternary instead. First seen: M0.

**BOM (bill of materials)**: A list that pins every part of a library family to versions that work together. *In TRIADA:* the JUnit BOM. First seen: M0.

**Build**: Turning source code into something that runs, usually running the tests on the way. *In TRIADA:* `./gradlew build` builds and tests everything. First seen: M0.

**Build tool**: A program that knows how to build a project: what to compile, in which order, and which libraries to download. *In TRIADA:* Gradle. First seen: M0.

**CLI (command-line interface)**: A program you control by typing commands in a terminal instead of clicking. *In TRIADA:* the `triada` tool in `triada-cli`. First seen: M0.

**Commit**: A saved snapshot of the whole project in version control (git), with a message saying what changed. *In TRIADA:* at least one commit per milestone. First seen: M0.

**Compile**: Translate source code that people write (here, Java) into a form the computer can run. First seen: M0.

**Dependency**: A ready-made library your project uses instead of writing that code itself. *In TRIADA:* libGDX (window, sound, input) and JUnit (tests), and nothing else without a DECISIONS.md entry. First seen: M0.

**Frame**: One complete picture on the screen. Showing many frames per second makes movement. *In TRIADA:* 60 frames per second (SPEC §6). First seen: M0.

**Frontend**: The part of a program that faces the person: window, sound, keyboard. *In TRIADA:* `triada-desktop`, built with libGDX. The machine itself (`triada-core`) knows nothing about it. First seen: M0.

**Gradle**: The build tool this project uses. Its settings are written in Kotlin script files (`*.gradle.kts`). First seen: M0.

**Gradle wrapper**: A tiny script (`gradlew`, `gradlew.bat`) plus a small jar, committed to the project, that downloads exactly the right Gradle version on first use. Nobody has to install Gradle by hand. First seen: M0.

**Headless**: Running without any window, sound or keyboard, which is useful for automatic tests. *In TRIADA:* the `triada run` command (M5). First seen: M0.

**JDK (Java Development Kit)**: The tools needed to compile and run Java programs. *In TRIADA:* JDK 25. First seen: M0.

**JUnit**: The standard Java library for writing automatic tests. *In TRIADA:* JUnit 5. First seen: M0.

**JVM (Java Virtual Machine)**: The program that runs compiled Java code. It accepts start-up settings called JVM flags. First seen: M0.

**libGDX**: A free Java library for games. It opens windows, draws pictures with the graphics card, plays sound and reads the keyboard. *In TRIADA:* used only by `triada-desktop`. First seen: M0.

**LWJGL**: "Lightweight Java Game Library", the layer libGDX uses on desktop computers to talk to the graphics card (OpenGL), the window system (GLFW) and sound (OpenAL). First seen: M0.

**Maven Central**: The big public warehouse of Java libraries that Gradle downloads dependencies from. First seen: M0.

**Module**: One self-contained part of a larger project, built separately, with its own code and tests. *In TRIADA:* `triada-core`, `triada-assembler`, `triada-cli` and `triada-desktop`. First seen: M0.

**Native library**: Code compiled directly for one kind of computer and operating system, not Java. LWJGL needs some to reach the graphics card. First seen: M0.

**OpenGL**: A standard way for programs to ask the graphics card to draw. libGDX uses it behind the scenes. First seen: M0.

**Plugin (Gradle)**: A ready-made bundle of build steps you switch on in a build file, for example `application`, which knows how to run a program. First seen: M0.

**Render loop**: The cycle a game runs over and over: update, draw a frame, repeat. *In TRIADA:* libGDX calls `TriadaApp.render()` about 60 times a second. First seen: M0.

**Smoke test**: A very small test that only checks "does it start at all?", like switching on a new appliance to see whether smoke comes out. First seen: M0.

**Source code**: The text of a program as people write it. First seen: M0.

**Spec (specification)**: The exact written description of how something must behave. *In TRIADA:* `docs/SPEC.md`, the law of the machine. First seen: M0.

**Ternary**: Counting with three digits. *Balanced* ternary uses −1, 0 and +1 (written `-`, `0`, `+`). *In TRIADA:* everything. First seen: M0.

**Test (automatic test)**: A small program that checks whether another piece of code does what it should, and complains loudly if not. First seen: M0.

**Toolchain (Gradle)**: Gradle's setting for "compile and test with exactly this Java version", even if a different one is installed as the default. First seen: M0.

**Trit**: One ternary digit: −1, 0 or +1. The ternary cousin of the bit. First seen: M0.

**Version catalog**: One file (`gradle/libs.versions.toml`) that lists every external library and its version, so they aren't scattered across build files. First seen: M0.

**Version control (git)**: A system that remembers every saved version of a project, so you can see what changed, when and why, and go back. First seen: M0.

**Vsync**: Waiting for the monitor to finish showing one picture before handing it the next, so the image never tears halfway. First seen: M0.
