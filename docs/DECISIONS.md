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
