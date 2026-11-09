// triada-rom: the TRIADA-85 ROM (M8). The ROM is written in TRIADA assembly
// (rom/*.tasm). During the build, the task generateRom assembles it with our
// own assembler and writes a Java class holding the words, so the emulator
// has the ROM built in -- no file to load at run time (good for a web build).

plugins {
    `java-library`
}

// A separate source set for the generator tool: it needs the assembler, but
// the ROM library itself only needs triada-core.
val generator: SourceSet by sourceSets.creating

dependencies {
    api(project(":triada-core"))
    "generatorImplementation"(project(":triada-assembler"))
    testImplementation(project(":triada-assembler"))
    testImplementation(project(":triada-net-core"))   // the echo board for the modem tests (M17)
}

val romDir = rootProject.layout.projectDirectory.dir("rom")
val generatedJava = layout.buildDirectory.dir("generated/sources/rom/java")
val romListing = layout.buildDirectory.file("rom/rom.lst")

val generateRom by tasks.registering(JavaExec::class) {
    description = "Assembles rom/rom.tasm into Java source and a listing."
    group = "build"
    classpath = generator.runtimeClasspath
    mainClass.set("org.triada.rom.tools.RomGenerator")
    inputs.dir(romDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedJava)
    outputs.file(romListing)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            romDir.file("rom.tasm").asFile.absolutePath,
            generatedJava.get().asFile.absolutePath,
            romListing.get().asFile.absolutePath,
        )
    })
}

sourceSets.main {
    java.srcDir(generateRom.map { generatedJava.get() })
}

tasks.withType<Test>().configureEach {
    systemProperty("triada.root", rootProject.projectDir.absolutePath)
    systemProperty("triada.diffDir", layout.buildDirectory.dir("golden-diff").get().asFile.absolutePath)
    systemProperty("triada.updateGolden", System.getProperty("triada.updateGolden") ?: "false")
    // The TRIBASIC suite reads tests/basic/*.bas at run time (M14): a changed
    // program or expected screen must make the tests run again.
    inputs.dir(rootProject.file("tests/basic")).withPropertyName("basicSuite")
    // ... and so do the example programs (M15) and the manual's TRIBASIC chapter.
    inputs.dir(rootProject.file("programs/basic")).withPropertyName("basicExamples")
    inputs.dir(rootProject.file("docs/manual")).withPropertyName("manual")
}
