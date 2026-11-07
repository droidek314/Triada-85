// triada-core: the machine itself (ternary math, memory, CPU, video, sound, I/O).
// Architecture rule (CLAUDE.md §2): pure Java, NO libGDX or any other UI library,
// and no threads, reflection or file I/O, so a future web build can compile it.

plugins {
    `java-library`
}

// `./gradlew :triada-core:benchmark` measures CPU speed (M3). It is a separate
// task, not a test, because timings depend on the machine it runs on.
tasks.register<JavaExec>("benchmark") {
    description = "Measures TRIADA CPU instructions per second."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.triada.core.cpu.CpuBenchmark")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
