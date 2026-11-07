// triada-core: the machine itself (ternary math, memory, CPU, video, sound, I/O).
// Architecture rule (CLAUDE.md §2): pure Java, NO libGDX or any other UI library,
// and no threads, reflection or file I/O, so a future web build can compile it.

plugins {
    `java-library`
}
