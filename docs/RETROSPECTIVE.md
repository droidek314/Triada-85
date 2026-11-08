# Retrospective — building a computer that never existed

## In one paragraph
In twelve milestones (M0 to M11) we built the TRIADA-85: a ternary home computer from an alternate 1985, complete with its arithmetic, processor, memory, picture and sound chips, keyboard and joystick, a ROM written in its own assembly language, a debugger, three games, an owner's manual and a CRT look. Each step came with tests and a document written for two readers at once, someone who has never programmed and a programmer who wants to rebuild it. This page looks back: what went well, what was hard, and what we would do differently next time.

## What exists now

| | |
|---|---|
| Milestones | 12 (M0–M11), each a commit with code, tests and a chapter of documentation |
| Java | about 7,000 lines of program code and 8,000 lines of tests, in 5 Gradle modules |
| TRIADA assembly | about 3,600 lines: the ROM (kernel and monitor) and 9 programs, 3 of them games |
| Documentation | 12 milestone chapters, the spec, 38 recorded decisions, a glossary of more than 200 words, the owner's manual |
| Tests | 540 automatic tests, including one that checks all 531,441 words, and tests that play the games |
| Speed | about 47 million instructions per second on an ordinary PC, 13 times what the real machine would need |

## What went well

**A small, exact spec, and rule 1.** `docs/SPEC.md` said precisely what the machine does, and CLAUDE.md said what to do when it didn't: choose the simplest thing, write it down, update the spec. That happened 38 times, and every one is in `DECISIONS.md` with its reasons. Gaps never turned into arguments or hidden guesses. Some examples: what MOD by zero gives (D-006), how big the keyboard queue is (D-007), and what `PUSH SP` pushes (D-009).

**The core knows nothing about windows.** `triada-core` is plain Java that turns memory into an array of colours and a list of sound samples. Because of that, almost everything could be tested without a screen: the video chip against golden images, the sound against expected samples, the games by playing them in a test. The same core runs in the window, on the command line and in the tests. M6's spike showed it even compiles to JavaScript.

**Writing the ROM and the games in the machine's own language.** It was slower than writing them in Java, but it was the best test the machine could get. Every instruction and every rounding rule was used for real, in 3,600 lines of code. Several design properties only became visible this way. Rounding division makes digit extraction subtle (`divmod10`). `MOD 3` hands you a trit for free (the type-in listing). Pixel packing is linear, so tiles can be coloured by arithmetic (M10).

**Tests that act like people.** The monitor tests type into the keyboard queue and read the screen. The game tests move the joystick. The manual's test types a 143-line listing in. Tests like these found the bugs that mattered, and they made the documentation trustworthy: the README's pictures and the manual's examples are checked in every build.

**Mutation checks.** From M1 on, we regularly broke the code on purpose to see whether the tests noticed. They didn't always, and every miss led to a better test (the snake's "map matches the ring" check in M10 is one example). Once, the mutation check found a bug in the build itself (see below).

**Documentation as part of the work.** Writing "Build it yourself from scratch" and "How we know it works" for each milestone forced us to run every command we documented and to explain every decision. Several bugs were found while writing the documents, not while writing the code.

## What was hard

**Our own expectations.** The most frequent kind of mistake in the whole project was not in the machine but in our first drafts of the tests. Examples: a wrong wrap-around result in M1, a test searching for a ball in the wrong place in M6, a column off by one in M10, a manual sentence about PEEK in M11. The fix was always the same. Work out expected values by hand from the definition, and when a test fails, first ask whether the test is right. We recorded these honestly in each milestone's "Problems".

**Balanced ternary rounding, everywhere.** Rounding to nearest is elegant in the spec, but it reaches into everyday code. `7 MOD 10` is −3. A random number needs a correction to land in 0 … n−1. Printing a decimal number needs a "negative remainder" fix. A right shift rounds. None of this is hard once known, but it caught us more than once, and the documents point it out every time it matters.

**Testing what only a screen can show.** The window, the sound card and the shader can't run in a headless test. In M6 the picture was upside down in the window for a whole milestone, because the only check used a picture that looks the same both ways up. Driving the real window by injected keystrokes was fragile: arrow keys needed a special flag, and an unexplained extra `HELP` appeared once. In M10 the keystrokes could even have reached another application the user was working in. M11 answered this with an invisible capture mode, and that is the approach we would use from the start next time.

**The build tool's idea of "up to date".** For several milestones the CLI tests read `programs/*.tasm` at run time, but Gradle didn't know that. After a change to a program, it could decide the tests were already up to date and skip them. We only noticed in M10, when a deliberately planted bug seemed to slip through every test. Since then, every file the tests read is declared as an input.

**The environment.** Windows consoles mangled non-ASCII text, which led to D-015. JDK 25 is newer than the LWJGL that libGDX bundles, so one warning remains at start-up. The development PC's `JAVA_HOME` pointed to an older JDK. None of these were the machine's fault, but each cost time and each is written down where it happened.

## What we would do differently

1. **Write a test helper for "type into the machine, read the screen" in M2, not M8.** It became the most useful tool of the project, and earlier milestones would have used it too.
2. **Plan for checking graphics without a visible window from M6 on.** The off-screen capture of M11 would have caught the upside-down picture immediately.
3. **Declare test inputs from the first test that reads a file.** One line in the build file would have prevented the "up to date" trap.
4. **Make the assembler a little friendlier for big programs.** The games would have been shorter with `*` in expressions (constants like `TILES + 16 * 19` had to be computed by hand) and with local labels. These are small additions that SPEC §9 could allow.
5. **Give the ROM a few more shared routines.** Every game wrote its own "random number in a range" and "print a number at a position". A slightly bigger ROM, or a standard library include from the start, would have saved repetition.
6. **Use one shared vocabulary file for the documents earlier.** Keeping the glossary, the milestone "New words" and the explanations in place consistent was manual work. A small script checked links and symbols (§5.5). Another could have checked that every "New word" is in the glossary.

## What comes next
CLAUDE.md lists three stretch goals, and the foundations for each are in place:
- **A small high-level language** (Pascal- or BASIC-like) compiling to TRIADA assembly. The assembler, the ROM routines and the test harnesses are ready for it.
- **A cassette-loading effect:** screeching sound and stripes in the border while a program loads. The sound chip's noise wave and the BORDER register are all it needs.
- **A web build.** `triada-core` and the assembler were kept free of threads, reflection and file access, and M6 showed that TeaVM compiles them. *Done since, in [M12](milestones/M12-web-version.md): the TRIADA-85 now runs on GitHub Pages.*

## The end
The TRIADA-85 never existed, but now it boots, plays its chime, counts in threes, and runs a snake, a puzzle and a shooter, all written in a language that only it understands. If you have read this far: switch it on and type `TERN 42`.
