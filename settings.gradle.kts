// TRIADA-85 — a fantasy ternary home computer from an alternate 1985.
// This file tells Gradle which sub-projects ("modules") make up the build.

// The libGDX web backend's Gradle plugin (M12) is published on Maven Central.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "triada-85"

include(
    "triada-core",       // ternary math, memory, CPU, video, sound, I/O (pure Java, no UI)
    "triada-assembler",  // assembler + disassembler library
    "triada-rom",        // the ROM, assembled from rom/*.tasm during the build
    "triada-cli",        // command-line tools: tasm (assemble), run (headless)
    "triada-app",        // frontend parts shared by desktop and web (M12)
    "triada-desktop",    // libGDX desktop frontend
    "triada-web",        // libGDX web frontend, for GitHub Pages (M12)
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
