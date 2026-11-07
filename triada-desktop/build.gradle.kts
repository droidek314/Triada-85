// triada-desktop: the libGDX frontend. It owns the window, sound output and
// input, and only *shows* what triada-core computes.

plugins {
    application
}

dependencies {
    implementation(project(":triada-core"))
    implementation(project(":triada-assembler"))
    implementation(project(":triada-rom"))
    implementation(libs.gdx.core)
    implementation(libs.gdx.backend.lwjgl3)
    // Native libraries (OpenGL, OpenAL, GLFW glue) for Windows, Linux and macOS.
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

application {
    mainClass.set("org.triada.desktop.DesktopLauncher")
    // LWJGL loads native code; newer JDKs warn about that unless it is allowed.
    // LWJGL also uses sun.misc.Unsafe, which JDK 24+ warns about; allow it quietly.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
    )
}

tasks.named<JavaExec>("run") {
    // Run from the project root so later milestones can find rom/ and programs/.
    workingDir = rootProject.projectDir
    // macOS needs GLFW on the main thread.
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}
