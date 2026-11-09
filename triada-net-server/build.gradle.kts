// triada-net-server: the TRIADA-NET server (M17 echo, M18 boards). TCP with a
// plain ServerSocket and a virtual thread per caller, WebSocket with Javalin
// (DECISIONS.md D-062), SQLite through JDBC with our own migration runner (D-066).

plugins {
    application
}

dependencies {
    implementation(project(":triada-net-core"))
    implementation(libs.javalin)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(testFixtures(project(":triada-net-core")))
}

application {
    mainClass.set("org.triada.net.server.NetServer")
    applicationName = "triada-net-server"
    // sqlite-jdbc loads its native library; newer JDKs ask for permission first.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
