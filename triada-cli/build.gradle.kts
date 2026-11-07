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
