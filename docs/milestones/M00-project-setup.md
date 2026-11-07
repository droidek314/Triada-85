# Milestone 00 — Project setup

## In one paragraph
Before you can build a computer, you need a workshop: a bench, labelled drawers for the parts and a way to check your work. This milestone builds that workshop for TRIADA-85. Nothing ternary exists yet. What exists is a project that a *build tool* (a program that assembles all the pieces) can turn into runnable software with one command. It runs a first set of automatic checks, and it opens an empty window titled "TRIADA-85", which will later become the machine's screen. Every later milestone adds parts to this workshop.

## What was built
- A **Gradle multi-module project**: one project made of four separate parts (*modules*). Gradle is the build tool.
  - `triada-core`: the future machine (math, memory, CPU, chips). For now it holds only the machine's name.
  - `triada-assembler`: the future translator from assembly text to machine words. It is empty for now.
  - `triada-cli`: the future *command-line* tool `triada` (a program you control by typing commands in a terminal instead of clicking). For now it prints a usage message.
  - `triada-desktop`: the window. For now it opens an empty 960 × 672 window titled "TRIADA-85".
- The **Gradle wrapper** (`gradlew`, `gradlew.bat`, `gradle/wrapper/`), so nobody has to install Gradle.
- A **version catalog** (`gradle/libs.versions.toml`): one list of every outside library and its version.
- Three **smoke tests** (tiny "does it work at all?" checks) that prove the test setup runs.
- Folders `rom/` and `programs/` for later assembly code, each with a short README.
- All living documents: `README.md`, `docs/INDEX.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `GLOSSARY.md` and `PROGRESS.md` (listing M0–M11).

## Why it was built this way

**Four modules instead of one big program.** The machine (`triada-core`) must not know anything about windows or sound cards. A real home computer doesn't know which TV it is plugged into either. Keeping the machine in its own module, with no libGDX dependency, means Gradle *refuses* to compile it if someone accidentally uses window code there. It also means the headless test runner and a possible future web version can reuse the machine unchanged. The alternative, one module with "please don't" comments, relies on discipline instead of the compiler.

**The newest long-term-support Java (25), Gradle 9.3.1, libGDX 1.14.2 and JUnit 5.** See [D-002](../DECISIONS.md#d-002--tool-versions-jdk-25-gradle-931-libgdx-1142-junit-5144---milestone-m00-2026-11-07). In short: JDK 25 follows CLAUDE.md's "newest LTS" rule, and the Gradle version is the newest one that is already proven on this machine.

**Shared settings in one root file, and warnings count as errors.** See [D-003](../DECISIONS.md#d-003--shared-build-settings-in-the-root-build-file-strict-compiler-warnings---milestone-m00-2026-11-07).

**The spec was moved into `docs/`.** See [D-001](../DECISIONS.md#d-001--the-spec-lives-in-docsspecmd---milestone-m00-2026-11-07).

## How it works — the plain-language version

**The build tool is a recipe-follower.** Compiling a Java program by hand means typing long commands in the right order, downloading libraries and running tests. Gradle reads a few small recipe files and does all of that. You type `./gradlew build` and Gradle:

1. downloads itself if needed (that's what the *wrapper* is for),
2. downloads a matching Java (the *toolchain*) and the libraries (libGDX, JUnit),
3. *compiles* every module, meaning it translates the Java text into runnable form,
4. runs every automatic test,
5. prints `BUILD SUCCESSFUL`, or tells you exactly what broke.

**The modules are drawers with rules about who may borrow from whom.**

```
   triada-desktop ──uses──► triada-assembler ──uses──► triada-core
        │                                                ▲
        └──────────────────────uses──────────────────────┘
   triada-cli ──uses──► triada-assembler, triada-core
```

`triada-core` borrows from no one. That rule is what keeps the machine pure.

**The window is a flip-book.** libGDX opens a window and then, about 60 times a second, asks our code: "draw the next picture". Each picture is a *frame*. Right now every frame is the same dark color. In M6 each frame will be the TRIADA screen, freshly drawn from the machine's memory. 60 frames per second matches the machine's own clock from SPEC §6.

**A smoke test** is the simplest possible check. For example, "the machine's name is `TRIADA-85`". It sounds silly, but if it passes, we know Java compiled, JUnit found the test and Gradle ran it. Every later, more serious test relies on that chain working.

## How it works — the programmer's version

Study the files in this order.

1. **[`settings.gradle.kts`](../../settings.gradle.kts)** lists the four modules with `include(...)` and declares Maven Central (the big public warehouse of Java libraries) as the only place to download from. Defining repositories here (`dependencyResolutionManagement`) instead of in each module means no module can quietly add another source.

2. **[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)** is the version catalog. Gradle turns `gdx-backend-lwjgl3` into the accessor `libs.gdx.backend.lwjgl3` inside module build files.

3. **[`build.gradle.kts`](../../build.gradle.kts)** (root) applies the same settings to every module in a `subprojects {}` block:
   ```kotlin
   extensions.configure<JavaPluginExtension> {
       toolchain.languageVersion.set(JavaLanguageVersion.of(25))
   }
   ...
   tasks.withType<JavaCompile>().configureEach {
       options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
   }
   tasks.withType<Test>().configureEach { useJUnitPlatform() ... }
   ```
   - The first block says "compile and test with Java 25, whatever Java is installed as the default."
   - The second turns on every compiler warning and makes each warning fail the build.
   - The third tells Gradle's test task to use JUnit 5 (the "JUnit Platform").

   Inside `subprojects {}` the type-safe `libs.` accessors don't exist, so the JUnit dependencies are looked up by name with `VersionCatalogsExtension.named("libs").findLibrary(...)`.

4. **Module build files.**
   - [`triada-core/build.gradle.kts`](../../triada-core/build.gradle.kts) and [`triada-assembler/build.gradle.kts`](../../triada-assembler/build.gradle.kts) use the `java-library` *plugin* (a ready-made bundle of build steps; this one knows how to compile and package a library). The assembler declares `api(project(":triada-core"))`, so anyone using the assembler can also see the core types (the assembler's public methods will return core types).
   - [`triada-cli/build.gradle.kts`](../../triada-cli/build.gradle.kts) uses the `application` plugin with `mainClass = org.triada.cli.Main` and the launcher name `triada`.
   - [`triada-desktop/build.gradle.kts`](../../triada-desktop/build.gradle.kts) adds `gdx`, `gdx-backend-lwjgl3` and the `gdx-platform:natives-desktop` *native libraries* (non-Java code for the graphics card and sound). It also passes two JVM flags (start-up settings for the Java Virtual Machine, the program that runs compiled Java) that newer JDKs need for LWJGL (see "Problems"), and runs from the project root so later milestones find `rom/` and `programs/`.

5. **[`MachineInfo`](../../triada-core/src/main/java/org/triada/core/MachineInfo.java)** (`org.triada.core.MachineInfo`) holds two constants, `NAME = "TRIADA-85"` and `SPEC_VERSION = "1.0"`. Both the window title and the CLI banner read `MachineInfo.NAME`, so the name is spelled in exactly one place.

6. **[`DesktopLauncher.main()`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java)** (`org.triada.desktop.DesktopLauncher`) builds an `Lwjgl3ApplicationConfiguration` (title, a 960 × 672 window, 60 fps and *vsync*, which waits for the monitor's refresh so the picture never tears) and starts `new Lwjgl3Application(new TriadaApp(), config)`. That call doesn't return until the window closes. libGDX now owns the main loop.

7. **[`TriadaApp.render()`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java)** (`org.triada.desktop.TriadaApp`, an `ApplicationAdapter`) is called once per frame:
   ```java
   Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
   Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
   ```
   - The first line chooses the "empty screen" color: red, green, blue and opacity, each from 0 to 1.
   - The second line fills the whole window with that color.

8. **[`Main`](../../triada-cli/src/main/java/org/triada/cli/Main.java)** (`org.triada.cli.Main`) prints `Main.usage()` and exits with 0, or with 2 (wrong usage) if any argument was given, because no commands exist yet.

9. **Tests.**
   - [`MachineInfoTest`](../../triada-core/src/test/java/org/triada/core/MachineInfoTest.java) checks both constants.
   - [`MainTest`](../../triada-cli/src/test/java/org/triada/cli/MainTest.java) checks that the usage text starts with the machine's name and mentions both future commands. It also proves that the CLI module can reach `triada-core`.

## Build it yourself from scratch
1. Install a JDK (25, or at least 21 if you change the toolchain number). Install Gradle once, temporarily, or borrow any Gradle 9.x distribution.
2. Create the root folder with `settings.gradle.kts` that `include`s your four module names and sets `mavenCentral()` in `dependencyResolutionManagement.repositories`.
3. Run `gradle wrapper --gradle-version 9.3.1` once. From then on, use only `./gradlew`. Commit `gradlew`, `gradlew.bat` and `gradle/wrapper/*` (the jar too).
4. Create `gradle/libs.versions.toml` with libGDX (`gdx`, `gdx-backend-lwjgl3`, `gdx-platform`) and JUnit (`junit-bom`, `junit-jupiter`, `junit-platform-launcher`).
5. Write the root `build.gradle.kts`: apply `java` to all subprojects, set the toolchain, add the JUnit BOM (a "bill of materials" that pins all JUnit parts to one matching version) and both JUnit dependencies, and call `useJUnitPlatform()`. **Pitfall:** without `junit-platform-launcher` on the test runtime classpath, recent Gradle versions fail to start JUnit.
6. For each module, create `src/main/java`, `src/test/java` and `src/main/resources` and a `build.gradle.kts` with the right plugin (`java-library` or `application`) and project dependencies. Make sure `triada-core` has **no** dependencies.
7. Write a constants class in core and a test for it. Run `./gradlew build` and check that the test actually ran (`build/reports/tests/test/index.html`).
8. In the desktop module, add the libGDX dependencies including `natives-desktop` (as `runtimeOnly`, with the classifier). Write a launcher with `Lwjgl3ApplicationConfiguration` and an `ApplicationAdapter` that clears the screen in `render()`. Set `application.mainClass`.
9. Run `./gradlew :triada-desktop:run`. **Pitfalls:** on macOS add `-XstartOnFirstThread`. On JDK 24+ add `--enable-native-access=ALL-UNNAMED` and `--sun-misc-unsafe-memory-access=allow` to silence LWJGL warnings.
10. Add a `.gitignore` (exclude `build/` and `.gradle/`, but **not** the wrapper jar), run `git init` and commit.

## How we know it works

Run the whole build from the project root:

```bash
./gradlew build
```

Expected: every module compiles, the tests run (`triada-core:test` and `triada-cli:test`), and the last lines are:

```
BUILD SUCCESSFUL in 22s
18 actionable tasks: 18 executed
```

The time and the task count vary (`./gradlew clean build` runs 22 tasks, and a repeated build reports most tasks as up to date). Modules without tests report `test NO-SOURCE`, which is fine. To see the individual results, open `triada-core/build/reports/tests/test/index.html`. It shows 2 tests passed, and the CLI report shows 1.

Open the window:

```bash
./gradlew :triada-desktop:run
```

Expected: a window titled **TRIADA-85**, filled with a dark blue-gray, appears within a few seconds and stays until you close it. We checked the title automatically by asking Windows for the window titles of running Java processes (`Get-Process java | Select MainWindowTitle` printed `TRIADA-85`). Closing the window ends the command. If it is stopped by force instead, Gradle reports a failure (exit code 1), which is normal.

## Problems we hit and how we solved them
- **The spec was in the wrong place.** `SPEC.md` was in the root, while CLAUDE.md expects `docs/SPEC.md`. We moved it ([D-001](../DECISIONS.md#d-001--the-spec-lives-in-docsspecmd---milestone-m00-2026-11-07)).
- **Gradle wasn't installed.** We used a Gradle 9.3.1 distribution already in the local Gradle cache to generate the wrapper once. Nobody needs a global Gradle after that.
- **JDK 25 warns about LWJGL.** Out of the box the window printed several `WARNING: sun.misc.Unsafe::objectFieldOffset has been called…` lines. LWJGL uses an old internal Java API that JDK 24+ plans to remove. The flag `--sun-misc-unsafe-memory-access=allow` silences them.
- **One warning remains:** `[LWJGL] [ThreadLocalUtil] Unsupported JNI version detected, this may result in a crash.` LWJGL 3.3.3, which libGDX 1.14.2 bundles, doesn't know JDK 25's version number for its native interface. The window works normally. We chose **not** to override libGDX's LWJGL version, because mixing versions that libGDX wasn't tested with is a bigger risk than a warning. We'll revisit this if a newer libGDX bundles a newer LWJGL, or if a real crash ever appears.

## Limitations and what comes next
- The window size (960 × 672 = the 216 × 144 screen plus a guessed 12-pixel border, times 4) is a placeholder. The real border size and scaling rules are decided in M6.
- The window ignores high-DPI scaling settings. On a 125 % Windows display it appears somewhat larger. This is also revisited in M6.
- There is no ternary code yet. **M1** builds the foundation: the trit and word types, conversion between numbers and `-0+` strings, and every arithmetic and trit-wise operation the CPU will need, with an exhaustive test over all 531,441 word values.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Binary / Ternary / Trit:** counting with two digits / with three digits (−1, 0, +1) / one ternary digit.
- **Build / Build tool / Gradle:** turning source into a runnable program / the program that does it / the one we use.
- **Gradle wrapper:** a committed script that fetches the right Gradle version.
- **Toolchain:** Gradle's "use exactly this Java version" setting.
- **Version catalog:** one file listing every library version.
- **Module:** a self-contained part of the project.
- **Dependency:** a ready-made library a project uses.
- **Compile:** translating Java text into runnable form.
- **JDK:** the Java tools.
- **libGDX / LWJGL / OpenGL / Native library:** the game library / its desktop layer / the graphics standard underneath / non-Java code for one operating system.
- **Frontend / Headless:** the part a person sees and touches / running without it.
- **Frame / Render loop:** one picture / the draw-repeat cycle.
- **JVM / Maven Central / Plugin / BOM / Vsync / CLI:** the Java runner / the public library warehouse / a bundle of build steps / a version-pinning list / waiting for the monitor's refresh / command-line interface.
- **Test / Smoke test / JUnit:** automatic check / the simplest such check / the Java testing library.
- **Spec:** the exact written rules (`docs/SPEC.md`).
- **Source code / Version control / Commit:** program text / the history-keeping system (git) / one saved snapshot.
