// triada-cli: command-line tools (`tasm` assembles, `run` runs headless).
// End-to-end tests of whole programs also live here.

plugins {
    application
}

dependencies {
    implementation(project(":triada-core"))
    implementation(project(":triada-assembler"))
    implementation(project(":triada-rom"))
}

application {
    mainClass.set("org.triada.cli.Main")
    applicationName = "triada"
}

tasks.named<JavaExec>("run") {
    // `./gradlew :triada-cli:run --args="run programs/hello.tasm"` works from the project root.
    workingDir = rootProject.projectDir
}

tasks.withType<Test>().configureEach {
    // Integration tests find programs/ and rom/ through this property.
    systemProperty("triada.root", rootProject.projectDir.absolutePath)
    systemProperty("triada.diffDir", layout.buildDirectory.dir("golden-diff").get().asFile.absolutePath)
    systemProperty("triada.updateGolden", System.getProperty("triada.updateGolden") ?: "false")
    // The tests assemble programs/*.tasm at run time, so Gradle must know that
    // a changed program means the tests are out of date (otherwise it skips them).
    inputs.dir(rootProject.file("programs")).withPropertyName("programs")
}
