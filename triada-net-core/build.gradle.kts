// triada-net-core: TRIADA-NET without any network code (M17, M18, M19).
// TRIMODEM, the server side of the wire protocol, the boards and their
// sessions, and the starter boards' content. Pure Java and no sockets, so
// tests, the server and an in-process node all share it.
//
// Its test fixtures (a settable clock, a scripted caller, the store contract)
// are shared with triada-net-server's tests (M18).

plugins {
    `java-library`
    `java-test-fixtures`
}

// The programs the starter boards offer (M19) are built during the build by
// ContentGenerator, which needs the assembler and a whole TRIADA (for typing
// TRIBASIC programs in and SAVEing them). Only the generator uses those.
val contentgen: SourceSet by sourceSets.creating

dependencies {
    api(project(":triada-core"))
    "contentgenImplementation"(project(":triada-rom"))
    "contentgenImplementation"(project(":triada-assembler"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
}

val contentList = layout.projectDirectory.file("content/files.txt")
val contentDir = layout.buildDirectory.dir("generated/resources/content")

val generateContent by tasks.registering(JavaExec::class) {
    description = "Builds the programs the starter boards offer into net/files/."
    group = "build"
    classpath = contentgen.runtimeClasspath
    mainClass.set("org.triada.net.tools.ContentGenerator")
    inputs.file(contentList)
    inputs.dir(rootProject.file("programs")).withPropertyName("programs")
    outputs.dir(contentDir)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(rootProject.projectDir.absolutePath, contentList.asFile.absolutePath,
                contentDir.get().asFile.absolutePath)
    })
}

sourceSets.main {
    resources.srcDir(generateContent.map { contentDir.get() })
}
