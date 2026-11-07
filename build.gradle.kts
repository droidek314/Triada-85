// Settings shared by every module. Each module's own build.gradle.kts only
// adds what is special about it (its dependencies, a main class, ...).

subprojects {
    apply(plugin = "java")

    group = "org.triada"
    version = "0.0.1"

    extensions.configure<JavaPluginExtension> {
        // Newest LTS JDK at the time of writing (see DECISIONS.md D-002).
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    // The version catalog (gradle/libs.versions.toml) is not type-safe inside
    // `subprojects {}`, so look the JUnit entries up by name.
    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
