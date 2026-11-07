# Decision log

Every choice that the spec (`SPEC.md`) or the project rules (`CLAUDE.md`) did not settle, or that changed them, is recorded here. Entries are never deleted. A later entry can replace an earlier one and says so.

Format: see CLAUDE.md §5.4.

---

## D-001 — The spec lives in `docs/SPEC.md`   (Milestone M00, 2026-11-07)
**Situation:** The repository started with `SPEC.md` in the root folder, but `CLAUDE.md` says the spec is `docs/SPEC.md`.
**Options:** A) Leave it in the root and change CLAUDE.md. B) Move it into `docs/` as CLAUDE.md describes.
**Decision:** B. The file was moved to `docs/SPEC.md` unchanged.
**Why:** CLAUDE.md's layout keeps all documents together in `docs/`, and every other rule refers to that path.
**Consequences:** None for the machine. Links to the spec always use `docs/SPEC.md`.

## D-002 — Tool versions: JDK 25, Gradle 9.3.1, libGDX 1.14.2, JUnit 5.14.4   (Milestone M00, 2026-11-07)
**Situation:** CLAUDE.md asks for "the newest LTS JDK (21 or later)", Gradle with the Kotlin DSL, libGDX and JUnit 5, but names no exact versions.
**Options:**
- JDK: 21 (older LTS, most widely tested) or 25 (newest LTS, September 2025).
- Gradle: 9.9.0 (released the day before this decision) or 9.3.1 (already in use on the development machine, supports JDK 25).
- JUnit: 6.x (newest) or 5.14.x (the last JUnit 5 line).

**Decision:** JDK 25 via a Gradle *toolchain*, Gradle 9.3.1 through the committed wrapper, libGDX 1.14.2 (newest release on Maven Central) and JUnit 5.14.4.
**Why:** JDK 25 is the newest LTS, as CLAUDE.md asks. A day-old Gradle release gives no benefit and some risk, while 9.3.1 is proven here. CLAUDE.md names JUnit 5 explicitly.
**Consequences:**
- All library versions sit in one file, `gradle/libs.versions.toml`, so upgrading is a one-line change.
- libGDX 1.14.2 ships LWJGL 3.3.3, which prints a harmless warning on JDK 25 ("Unsupported JNI version detected"). See the M0 document, "Problems".
- A future web build (TeaVM/GWT) may need a lower language level for `triada-core`. That is checked in M6.

## D-003 — Shared build settings in the root build file; strict compiler warnings   (Milestone M00, 2026-11-07)
**Situation:** Four modules need the same Java version, test setup and compiler options.
**Options:** A) Repeat the settings in each module. B) A Gradle "convention plugin" in `buildSrc/`. C) One `subprojects {}` block in the root `build.gradle.kts`.
**Decision:** C, with `-Xlint:all -Werror`, so every compiler warning fails the build.
**Why:** C is the shortest option and the easiest for a beginner to read. B only pays off in much larger builds. Treating warnings as errors keeps a teaching codebase clean from the first day.
**Consequences:** If the build grows complicated, moving to B later is straightforward. Now and then a warning has to be fixed, or explicitly suppressed with a comment, before the code compiles.

## D-004 — Words are plain `int`s, operations are static methods   (Milestone M01, 2026-11-07)
**Situation:** The code needs a "word" type. The obvious object-oriented design is a `Word` class with 12 trit fields, or an immutable wrapper object around a number.
**Options:** A) A `Word` object per value. B) An `int` holding the value, plus static helper classes (`Word`, `WordMath`). C) An array of 12 trits per word.
**Decision:** B.
**Why:** The CPU must run about 3.5 million instructions per second (SPEC §6), and CLAUDE.md forbids creating objects in that loop. Normal integer arithmetic plus the wrap formula gives exactly the ternary result, so trits only need to be taken apart for the four trit-wise operations and for display. A, or the trits of C, would cost an object or array for every result.
**Consequences:** RAM can be a plain `int[]`. Java's type system can't tell a word from any other `int`, so methods document "inputs are valid words", and the tests check that every result stays in range.

## D-005 — Trit arrays are indexed by position, least significant first   (Milestone M01, 2026-11-07)
**Situation:** A word converted to an array of trits needs an index order. The written notation is most significant first (`+-0`).
**Options:** A) `array[0]` = leftmost written trit (t11). B) `array[i]` = trit `ti`, so `array[0]` = t0.
**Decision:** B.
**Why:** The spec numbers trits by position (`t0` … `t11`, SPEC §1.4), so `trits[i]` = `ti` needs no translation and its weight is simply 3^i. The cost is that an array printed in index order reads backwards compared with the string. Tests check that the two agree.
**Consequences:** Code that prints an array must walk it from index 11 down to 0. `Word.toString` does that for you.

## D-006 — MOD by zero also gives 0   (Milestone M01, 2026-11-07)
**Situation:** SPEC §3.5 says "Division by zero: result 0, S = 0, O = +1" in a paragraph about both DIV and MOD, and the opcode table says both set O on ÷0. But the formula for MOD (`rd − round(rd ÷ rs) × rs`) with the DIV result 0 would give `rd`, not 0.
**Options:** A) MOD by zero returns 0. B) MOD by zero returns `rd` unchanged (what the formula gives).
**Decision:** A. SPEC §3.5 now says "Division by zero (in `DIV` or `MOD`): result 0".
**Why:** It is the most literal reading of the existing sentence, and one simple rule for both instructions is easier to remember. It also matches S = 0, which the spec already requires: S is the sign of the result, and only a result of 0 has sign 0.
**Consequences:** `WordMath.mod(a, 0)` returns 0. In M3 the CPU writes 0 into `rd` for both DIV and MOD by zero.
