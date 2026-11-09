// triada-net-server: the TRIADA-NET server (M17 echo, M18 boards). TCP with a
// plain ServerSocket and a virtual thread per caller, WebSocket with Javalin
// (DECISIONS.md D-062).

plugins {
    application
}

dependencies {
    implementation(project(":triada-net-core"))
    implementation(libs.javalin)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("org.triada.net.server.NetServer")
    applicationName = "triada-net-server"
}
