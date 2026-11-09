// triada-cli: command-line tools (`tasm` assembles, `run` runs headless).
// End-to-end tests of whole programs also live here.

plugins {
    application
}

dependencies {
    implementation(project(":triada-core"))
    implementation(project(":triada-assembler"))
    implementation(project(":triada-rom"))
    implementation(project(":triada-net-core"))      // --local-node: TRIADA-NET in process (M19)
    // Modem tests call a real TRIADA-NET server (M17).
    testImplementation(project(":triada-net-server"))
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
    systemProperty("triada.updateDocImages", System.getProperty("triada.updateDocImages") ?: "false")
    // The tests assemble programs/*.tasm at run time, so Gradle must know that
    // a changed program means the tests are out of date (otherwise it skips them).
    inputs.dir(rootProject.file("programs")).withPropertyName("programs")
    // Likewise the manual (ManualTest replays its examples) and the README's
    // pictures (DocImagesTest compares them with the machine), M11.
    inputs.files(rootProject.fileTree("docs/manual"), rootProject.fileTree("docs/images"))
        .withPropertyName("docs")
}
