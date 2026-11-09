// triada-net-core: TRIADA-NET without any network code (M17, M18). TRIMODEM,
// the server side of the wire protocol, and (from M18) the boards. Pure Java
// and no sockets, so tests, the server and an in-process node all share it.

plugins {
    `java-library`
}

dependencies {
    api(project(":triada-core"))
}
