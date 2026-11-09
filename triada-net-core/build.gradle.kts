// triada-net-core: TRIADA-NET without any network code (M17, M18). TRIMODEM,
// the server side of the wire protocol, and the boards. Pure Java and no
// sockets, so tests, the server and an in-process node all share it.
//
// Its test fixtures (a settable clock, a scripted caller, the store contract)
// are shared with triada-net-server's tests (M18).

plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":triada-core"))
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter)
}
