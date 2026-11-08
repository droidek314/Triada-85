# Milestone 12 — The web version

## In one paragraph
Until now, trying the TRIADA-85 meant installing Java and building the project. This milestone puts the machine on a web page: open an address and it boots, exactly as on the desktop. The sound comes on with the first key press, because browsers keep pages silent until then. The games, the type-in listing, the CRT look and "inserting a triskette" (dropping a file on the page) all work. Only the workshop tools stay in the download: the debugger, the command line and the capture mode. The page is published on GitHub Pages and rebuilt by GitHub after every change, but only if all tests still pass.

## What was built
- **`triada-app`**, a new module with what the desktop and the web share: the picture (`MachineScreen`), the CRT look (`CrtEffect`), the keyboard (`InputMapper`) and the frame clock (`FrameClock`) ([D-039](../DECISIONS.md#d-039--the-web-version-gdx-teavm-a-shared-frontend-module-the-same-machine-without-the-debugger---milestone-m12-2026-11-08)).
- **`triada-web`**, the web frontend: `WebLauncher`, `WebApp`, `WebPage` and the page [`index.html`](../../triada-web/src/main/webapp/index.html), built with the libGDX web *backend* gdx-teavm 1.6.2. (A backend is the part of libGDX that connects it to one platform: LWJGL3 for the desktop window, gdx-teavm for a browser.)
- **Sound in the browser**, about 20 lines of JavaScript ([D-041](../DECISIONS.md#d-041--sound-in-the-browser-one-small-buffer-per-frame-scheduled-back-to-back---milestone-m12-2026-11-08)).
- **The CRT shader's source inside the code**, so neither platform has to load a file ([D-040](../DECISIONS.md#d-040--the-crt-shaders-source-moves-into-the-code---milestone-m12-2026-11-08)).
- **A GitHub Actions workflow**, [`pages.yml`](../../.github/workflows/pages.yml), that builds, tests and publishes the page to https://droidek314.github.io/Triada-85/ ([D-042](../DECISIONS.md#d-042--publishing-a-github-actions-workflow-builds-tests-and-deploys-to-github-pages---milestone-m12-2026-11-08)).
- **One new test**, `CrtEffectTest`. It replaces M11's shader-file test, so the total is still 540.

## Why it was built this way

**The same machine, not a copy of it.** The web version runs the very same `triada-core`, ROM and assembler, translated to JavaScript. The parts of the window code that both versions need moved into `triada-app`, so a change to the picture or the keyboard reaches both at once. The alternative, a separate web app with its own copy of the drawing code, would drift apart from the desktop over time.

**Without the debugger.** The user asked for the web version to match the download, minus the debugger. The browser also claims the debugger's keys: F5 reloads the page, F11 is full screen, F1 opens help. The debugger, the command line and the capture mode are tools for people working on the machine, and they stay in the download.

**gdx-teavm.** CLAUDE.md asks for a libGDX web backend. The M6 spike (D-022) had already shown that TeaVM, the *compiler* (translator of programs) under gdx-teavm, handles our Java 25 code. gdx-teavm 1.6.2 (August 2026) is made for libGDX 1.14.2, the version we use.

**Sound the simple way.** gdx-teavm has no raw sound output, so the page has its own tiny player: every frame's 735 samples become one small buffer, scheduled to start where the last one ends. The smoother alternative, a sound program running beside the page fed through shared memory, needs server settings that GitHub Pages doesn't allow.

**Programs travel as source.** The page ships the `.tasm` files and assembles them in the browser, which takes a few milliseconds. That needs no extra build step, and it makes a dropped `.tasm` file work like on the desktop.

**Built by GitHub, gated by the tests.** Nobody has to remember to rebuild the page: every *push* (sending new commits to GitHub) to `master` runs all 540 tests, builds the web version and publishes it. If a test fails, the old page stays online.

## How it works — the plain-language version

### From Java to a web page
The TRIADA is written in Java, and browsers don't run Java. A *compiler* called TeaVM translates the finished Java program into JavaScript, the language browsers do run. It is like translating a book: the story (the machine) stays the same, only the language changes. The result is one file, `app.js`, of about 480 KB. With the page, the program sources and some helper scripts, the whole site is about 0.9 MB, smaller than one photo from a phone.

GitHub Pages is a free service that hands out files over the web: it doesn't run anything itself. That is enough, because everything runs in the visitor's browser.

### The page
```
   +---------------------------------------------+
   |                  TRIADA-85                  |
   |   +-------------------------------------+   |
   |   |                                     |   |
   |   |      the machine's screen           |   |  <- a canvas: a drawing area
   |   |                                     |   |     on the page
   |   |                                     |   |
   |   +-------------------------------------+   |
   |   Running trizmeyka.tasm.                   |  <- status line
   |   Monitor TRIZMEYKA TROIKA OBLAKA ...       |  <- one link per program
   |   > Help                                    |  <- opens the keys and a short note
   +---------------------------------------------+
```
The screen is shown 1 to 4 times its real size of 240 × 168 pixels, whichever whole number fits the browser window, so the pixels stay square and sharp.

### Why the first sound waits for a key
Browsers don't let a page make sound until the visitor has pressed a key or clicked on it, so that pages can't start shouting by surprise. The first version of the page therefore had a "Click to switch on" button, so the boot chime was heard from its first note. That extra click got in the way, so now the machine switches itself on as soon as the page has loaded, and its sound is unmuted by the first key press or click ([D-043](../DECISIONS.md#d-043--the-web-page-switches-itself-on-sound-follows-the-first-key---milestone-m12-2026-11-08)). The price: the boot chime is usually silent.

### Sound, one frame at a time
The machine makes 60 frames a second, and each frame comes with 735 sound samples (1/60 of a second at 44,100 samples per second). The page wraps each frame's samples in a tiny sound buffer and books it on the browser's sound clock, right after the previous one, like train cars coupled end to end. A small head start of 60 ms (about 4 frames) keeps the train from running out of cars when the browser is briefly busy.

Worked example: the machine finishes a frame when the sound clock says 10.000 s. The first buffer is booked for 10.060 s and lasts 735 / 44,100 = 0.01667 s, so the next one is booked for 10.0767 s, the one after for 10.0933 s, and so on.

### Choosing a program
Each link adds `?program=NAME` to the page's address, for example `?program=oblaka`. The page reloads, the machine switches on fresh, and the program is assembled from `oblaka.tasm` and started after the boot, as if it were a triskette in the drive. Dropping your own `.tri` or `.tasm` file on the page does the same without reloading.

## How it works — the programmer's version

Read in this order.

### 1. The shared module
[`triada-app/build.gradle.kts`](../../triada-app/build.gradle.kts) depends only on `triada-core` and libGDX's core API, never on a backend.

[`MachineScreen.java`](../../triada-app/src/main/java/org/triada/app/MachineScreen.java) is the drawing code that used to be in `TriadaApp`:
- `MachineScreen.upload()` copies the `int[]` picture into the RGBA pixmap and the texture.
- `MachineScreen.drawCentred()` clears to black and draws at `MachineScreen.scaleFor()` scale.
- `MachineScreen.draw()` draws at a given place and scale, through the CRT shader if `MachineScreen.toggleCrt()` switched it on.

[`CrtEffect.java`](../../triada-app/src/main/java/org/triada/app/CrtEffect.java) now holds the GLSL in `CrtEffect.VERTEX` and `CrtEffect.FRAGMENT`. [`InputMapper.java`](../../triada-app/src/main/java/org/triada/app/InputMapper.java) and [`FrameClock.java`](../../triada-app/src/main/java/org/triada/app/FrameClock.java) moved unchanged, apart from the package name and `public`.

The desktop's [`TriadaApp.java`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java) now creates a `MachineScreen` in `create()` and keeps only what is desktop-only: the debugger layout, the F-keys, capture and triskettes.

### 2. The web app
[`WebApp.java`](../../triada-web/src/main/java/org/triada/web/WebApp.java) is a libGDX `ApplicationAdapter`, like `TriadaApp`, without the debugger:
- `create()` makes the `MachineScreen`, routes keys to `InputMapper` (and F2 to the CRT), and asks the browser to keep the arrow keys, Space and F2 for itself (`setCatchKey`), so they don't scroll the page. Because a caught Space produces no typed character, `keyDown` types the space itself. If the address has `?program=NAME` with a plain name (`[a-z0-9]+`), it inserts `NAME.tasm`.
- `WebApp.insert()` assembles a `.tasm` or reads a `.tri`, then `powerOn()` and `Rom.autorun()`, the same two calls as the desktop's `Debugger.insertTriskette()`. Errors go to the status line.
- `WebApp.source()` feeds the assembler: the dropped file itself, or a bundled file through `Gdx.files.internal`, so `.include "lib/game.tasm"` works.
- `render()` takes a dropped file if there is one, asks the `FrameClock` how many frames are due, runs them, hands each frame's samples to the page, and draws.

[`WebLauncher.java`](../../triada-web/src/main/java/org/triada/web/WebLauncher.java) starts it with `WebApplication`. The canvas keeps the size the page gave it.

### 3. The glue to the page
[`WebPage.java`](../../triada-web/src/main/java/org/triada/web/WebPage.java) has five one-line JavaScript methods, written with TeaVM's `@JSBody`:

```java
@JSBody(params = "samples", script = "triadaSound.play(samples);")
static native void playSamples(@JSByRef short[] samples);
```

- `@JSBody` means: this method's body is the JavaScript in `script`.
- `@JSByRef` passes the Java `short[]` to JavaScript as a *typed array* (JavaScript's compact array of plain numbers), by reference, without copying it.
- Both are *annotations*: notes in the code, starting with `@`, that a tool (here TeaVM) reads.
- `WebPage.programParameter()` reads `?program=`.
- `WebPage.droppedName()` and `WebPage.takeDroppedBytes()` collect a dropped file that the page has read into memory.
- `WebPage.status()` writes the status line.

### 4. The page
[`index.html`](../../triada-web/src/main/webapp/index.html) does four small jobs in plain JavaScript:
1. It picks the canvas size: the largest whole-number multiple of 240 × 168 that fits, from 1 to 4.
2. It provides `triadaSound.play`. Its heart:

```js
if (this.next < now) this.next = now + 0.06;        // starting, or fell behind
else if (this.next > now + 0.25) return;            // far ahead: drop this frame
var b = c.createBuffer(1, samples.length, 44100);   // fill it with samples / 32768
s.start(this.next);                                  // play it right after the last one
this.next += b.duration;
```

   `now` is the sound clock. `next` is where the last buffer ends. The `return` drops a frame instead of letting the sound lag ever further behind.
3. It reads a dropped file into `triadaDrop`.
4. It creates the `AudioContext` (the browser's sound system), which starts muted, and resumes it on the first key press or click (`unmute`). When the page has loaded it calls `main()`, which starts the Java program.
5. Clicks inside the "Help" section are kept from reaching the backend: gdx-teavm treats a click outside the canvas as "the keyboard belongs to the page now" and stops passing keys on.

### 5. The build
[`triada-web/build.gradle.kts`](../../triada-web/build.gradle.kts) applies the gdx-teavm plugin. It declares `programs/` as *assets* (files the app reads at run time, which gdx-teavm copies next to the page and downloads before start) and turns off the generated page (`generateIndexHtml = false`). After `gdx_teavm_web_js_build` it copies our `index.html` next to `app.js`. The finished site is in `triada-web/build/dist/js/webapp`.

[`settings.gradle.kts`](../../settings.gradle.kts) lists Maven Central as a source of Gradle plugins, where gdx-teavm's plugin is published.

[`.github/workflows/pages.yml`](../../.github/workflows/pages.yml) runs the whole thing on GitHub: set up JDK 25, `./gradlew build :triada-web:gdx_teavm_web_js_build`, upload the folder, deploy.

## Build it yourself from scratch
1. **Move the shared frontend code out of the desktop module.** This is the picture upload and draw, the integer scale, the CRT shader, the key mapping and the frame clock. The new module may depend only on `com.badlogicgames.gdx:gdx` (core). Run the desktop tests: nothing should change.
2. **Get rid of resource files in shared code.** A browser has no *class path* (the list of places where Java looks for classes and resource files). Put small texts like shader source into the code as Java *text blocks* (strings written between `"""` over several lines), or into web assets.
3. **Add a web module** with the gdx-teavm Gradle plugin (`id("com.github.xpenatan.gdx-teavm") version "1.6.2"`, plugin repository: Maven Central). Write a launcher whose `main` does `new WebApplication(app, config)`. Build with `gdx_teavm_web_js_build` and open the output through a web server, not as a file.
4. **Write the web `ApplicationAdapter`.** Create a machine with the ROM, map keys, run frames with a fixed time step, draw. Call `Gdx.input.setCatchKey` for the arrows and Space, or the page will scroll while you play. A caught key no longer produces the browser's typed character, so type the space yourself in `keyDown`.
5. **Do sound in JavaScript.** `Gdx.audio.newAudioDevice` throws on gdx-teavm. Pass each frame's samples out with `@JSBody` + `@JSByRef`, and schedule Web Audio buffers back to back with a small lead. Browsers keep a new `AudioContext` suspended until the visitor presses a key or clicks: call `resume()` on the first `keydown` or `pointerdown`, and skip scheduling while it is not `"running"`.
6. **Write your own page** (`generateIndexHtml = false`) and copy it next to `app.js` after the build. Use only relative paths: on GitHub Pages the site lives in a sub-folder (`/Triada-85/`).
7. **Test under a sub-folder.** Copy the output into `site/Triada-85/` and serve `site/` (for example `python -m http.server`), then open `/Triada-85/`.
8. **Publish with GitHub Actions:** `actions/setup-java` with JDK 25, run the build, then `actions/upload-pages-artifact` and `actions/deploy-pages`. Set the repository's Pages source to "GitHub Actions". Make sure `gradlew` is executable in git (`git update-index --chmod=+x gradlew`) or the Linux build machine can't start it.

What tends to go wrong:
- `-Werror` together with TeaVM's jars: they produce two warnings that are not about your code.
- Port clashes for the local server: give it its own port.
- Screenshots of a WebGL canvas can come out black even when it draws fine (see "Problems").

## How we know it works

All tests, including the new `CrtEffectTest`, plus the web build:

```bash
./gradlew clean build :triada-web:gdx_teavm_web_js_build
```

You should see `BUILD SUCCESSFUL`, with 540 tests and no failures: core 250, assembler 98, ROM 56, CLI 98, app 10, desktop 28. Before M12 the desktop module had 38 tests. Ten of them moved to `triada-app`: `InputMapperTest`, `FrameClockTest` (with the scale test), and the shader test, which is now `CrtEffectTest`.

The site is in `triada-web/build/dist/js/webapp`: `index.html`, `app.js` (about 480 KB), `assets/` (the program sources) and `scripts/`.

To try it locally:

```bash
./gradlew :triada-web:gdx_teavm_web_js_run
```

This serves the page at http://localhost:8385/. We checked that the page, `app.js` and the program files are served there (HTTP 200).

Checked in a browser, with the build copied into a `/Triada-85/` sub-folder as on GitHub Pages:
- The page loads and the machine boots by itself to `READY.`.
- Typing at the monitor works, including Space: `HELP 1` reached the machine with its space and got `?SYNTAX ERROR`, as on the desktop.
- `?program=triangle` and `?program=colors` assemble and run their programs.
- The `colors` picture, read back from the canvas, consists of exactly the 27 palette colours: 40,320 of 40,320 pixels.
- The sound player schedules buffers continuously while the machine runs.

The published page: https://droidek314.github.io/Triada-85/ (the workflow's run on GitHub shows the build, the test results and the deployment).

## Problems we hit and how we solved them
- **No raw sound output in gdx-teavm.** `newAudioDevice` throws "AudioDevice not supported by teaVM backend". We found this in the backend's source before writing any sound code, and wrote the page's own player (D-041).
- **Warnings turned into errors.** Our build treats every compiler warning as an error. TeaVM's annotations refer to an IntelliJ annotation that isn't on the class path, and the plugin adds TeaVM's annotation processor, which complains that it supports only Java 11 sources. Neither is about our code. `triada-web` switches off exactly these two (`-Xlint:-classfile`, `-proc:none`); everything else stays an error.
- **A Gradle task that didn't exist yet.** `tasks.named("gdx_teavm_web_js_build")` failed because the plugin creates its tasks late. `tasks.matching { … }.configureEach` waits for it.
- **A black screen that wasn't.** The browser pane's screenshots showed a black canvas, while sound was clearly being scheduled. We first suspected that `resize()` wasn't called at start and added a call. Then we read the pixels straight after a frame was drawn (`gl.readPixels`), and the picture was there. A WebGL canvas is cleared after it is shown on screen, so the screenshot tool captured an empty one. The gdx-teavm source confirmed that it calls `resize()` right after `create()`, so the extra call was removed again.
- **Space didn't type.** Reported by the user right after publishing. To stop Space from scrolling the page, `WebApp` asked the backend to catch it (`setCatchKey`). The backend then cancels the key's default action, and with it the browser's typed-character event, so the monitor never saw the space. `WebApp` now types it itself when Space goes down. The desktop never had this problem.
- **Port 8080 was taken** by another program on the development PC, so the local server failed to start. `triada-web` now uses port 8385.

## Limitations and what comes next
- **No debugger, command line or capture mode** on the web, by choice (D-039). They are in the download.
- **A keyboard is needed.** Phones and tablets show the page, but the TRIADA has no on-screen keyboard or joystick.
- **Sound runs about 60 ms behind the picture**, a tab in the background pauses the machine, and the boot chime is usually silent because browsers keep the page muted until the first key press (D-043).
- **Speed in the browser was not measured.** M6 measured TeaVM's JavaScript at about 1.3 times real time in the worst case (every frame using all 59,049 cycles). The games mostly wait for the next frame, so they need far less. If a slow computer struggles, gdx-teavm can also produce WebAssembly, usually faster, with a one-word change in the build (`wasm {}`).
- **Not checked by us in a browser:** playing a game with the arrow keys, dropping a file, F2 and *hearing* the sound. They use the same `InputMapper`, `MachineScreen` and libGDX input code as the desktop, but the browser's side of them is new.
- **What comes next:** the remaining stretch goals, a small high-level language and the cassette-loading effect. Both live in the shared code or the ROM, so they would reach the web version too.

## New words
- **Annotation:** a note in the code starting with `@` that a tool reads.
- **Asset:** a file an app reads at run time, downloaded with the page on the web.
- **Backend (libGDX):** the part of libGDX that connects it to one platform.
- **Canvas:** the drawing area of a web page, where the TRIADA's screen is shown.
- **Class path:** where Java looks for classes and resource files; a browser has none.
- **Compiler:** a program that translates programs from one language into another.
- **GitHub Actions:** GitHub's service that runs a list of steps (a *workflow*) on its own computers, for example on every push.
- **GitHub Pages:** GitHub's free hosting for static websites.
- **JavaScript:** the programming language that web browsers run.
- **Push (git):** sending new commits to GitHub.
- **Query string:** the part of a web address after `?`, like `?program=oblaka`, which a page can read.
- **Static website:** a site made only of files that the server hands out unchanged; everything runs in the visitor's browser.
- **Text block:** a Java string written over several lines between `"""` marks.
- **Typed array:** JavaScript's compact array of plain numbers.
- **Web Audio:** the browser's built-in sound system (`AudioContext`).
- **WebGL:** the browser's version of OpenGL, used to draw with the graphics card.
- **Workflow:** a list of automatic steps, written in a file in `.github/workflows/`, that GitHub Actions runs.
