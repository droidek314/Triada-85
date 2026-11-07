// TRIADA-85 — a fantasy ternary home computer from an alternate 1985.
// This file tells Gradle which sub-projects ("modules") make up the build.

rootProject.name = "triada-85"

include(
    "triada-core",       // ternary math, memory, CPU, video, sound, I/O (pure Java, no UI)
    "triada-assembler",  // assembler + disassembler library
    "triada-cli",        // command-line tools: tasm (assemble), run (headless)
    "triada-desktop",    // libGDX desktop frontend
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
