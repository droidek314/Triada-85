// M6 web spike (DECISIONS.md D-022): can TeaVM 0.13.0 -- the compiler used by
// the libGDX TeaVM backend gdx-teavm 1.4.0 -- compile triada-core and
// triada-assembler to JavaScript?
//
//   ./gradlew -p docs/spikes/m06-teavm teavm
//   node -e "require('./docs/spikes/m06-teavm/build/js/spike.js').main([])"
plugins { java }
repositories { mavenCentral() }

val teavm by configurations.creating
dependencies {
    teavm("org.teavm:teavm-cli:0.13.0")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

// Compile the project's own sources straight into this build.
sourceSets["main"].java.srcDirs(
    "src/main/java",
    "../../../triada-core/src/main/java",
    "../../../triada-assembler/src/main/java")

tasks.register<JavaExec>("teavm") {
    dependsOn("classes")
    // TeaVM reads the classes to compile from its OWN classpath.
    // (Its -p option is only an extra classpath for "wait" mode.)
    classpath = teavm + sourceSets["main"].output
    mainClass.set("org.teavm.cli.TeaVMRunner")
    args = listOf("-t", "javascript", "--js-module-type", "common-js",
        "-d", layout.buildDirectory.dir("js").get().asFile.path, "-f", "spike.js", "spike.WebSpike")
}
