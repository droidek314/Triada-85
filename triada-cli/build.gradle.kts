// triada-cli: command-line tools (`tasm` assembles, `run` runs headless).
// End-to-end tests of whole programs also live here.

plugins {
    application
}

dependencies {
    implementation(project(":triada-core"))
    implementation(project(":triada-assembler"))
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
}
