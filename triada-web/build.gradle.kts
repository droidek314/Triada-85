// triada-web: the TRIADA-85 in a web browser (M12, D-039). gdx-teavm turns the
// Java bytecode of the machine, the ROM, the assembler and the shared frontend
// (triada-app) into JavaScript, and writes a folder of plain files that any
// static web host can serve -- GitHub Pages in our case.
//
//   ./gradlew :triada-web:gdx_teavm_web_js_build    -> triada-web/build/dist/webapp
//   ./gradlew :triada-web:gdx_teavm_web_js_run      -> the same, served at http://localhost:8385

import org.teavm.gradle.api.OptimizationLevel

plugins {
    id("com.github.xpenatan.gdx-teavm") version "1.6.2"
}

dependencies {
    implementation(project(":triada-app"))
    implementation(project(":triada-assembler"))
    implementation(project(":triada-rom"))
}

gdxTeaVM {
    // The programs' source travels with the page and is assembled in the browser.
    assets(rootProject.file("programs"))

    js {
        mainClass = "org.triada.web.WebLauncher"
        htmlTitle = "TRIADA-85"
        optimization = OptimizationLevel.BALANCED
        obfuscated = true
        generateIndexHtml = false    // our own page: src/main/webapp/index.html
        serverPort = 8385            // gdx_teavm_web_js_run serves at http://localhost:8385
    }
}

// Put our page next to the generated app.js.
tasks.matching { it.name == "gdx_teavm_web_js_build" }.configureEach {
    inputs.file("src/main/webapp/index.html")
    doLast {
        copy {
            from("src/main/webapp/index.html")
            into(layout.buildDirectory.dir("dist/js/webapp"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    // TeaVM's annotations refer to an IntelliJ annotation that isn't on the class path: a
    // warning about their jar, not our code. The plugin also adds TeaVM's annotation
    // processor, which our classes don't need (no TeaVM extension annotations) and which
    // warns about the Java version. All other warnings stay errors.
    options.compilerArgs.addAll(listOf("-Xlint:-classfile", "-proc:none"))
}
