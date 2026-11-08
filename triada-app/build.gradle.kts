// triada-app: the parts of the frontend that every platform shares (M12):
// the picture (MachineScreen, CrtEffect), the keyboard (InputMapper) and the
// frame clock. It uses libGDX's core API only -- no desktop or web backend --
// so the desktop window and the web page show exactly the same machine.

plugins {
    `java-library`
}

dependencies {
    api(project(":triada-core"))
    api(libs.gdx.core)
}
