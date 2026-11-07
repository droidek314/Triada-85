// triada-assembler: turns TRIADA assembly text into machine words, and back
// (disassembler). Pure Java, like triada-core.

plugins {
    `java-library`
}

dependencies {
    api(project(":triada-core"))
}
