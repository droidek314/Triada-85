# Milestone 06 — Video chip and frontend display

## In one paragraph
Until now the TRIADA-85 could only talk through the debug port. Now it has a face. The *video chip* looks at a few areas of memory 60 times a second and paints a picture from them: small 8 × 8 pictures called *tiles* are laid out like a mosaic to make the background, up to 27 *sprites* (little pictures that move freely) are drawn on top, and a coloured border frames it all. Every colour is made of three trits, one each for red, green and blue, so the machine has exactly 27 colours. The desktop window now shows this picture, enlarged with crisp, sharp pixels. A new demo, `colors.tasm`, shows all 27 colours and a bouncing ball. We also checked early that the machine could one day run in a web browser: it can.

## What was built
- **`Palette`**: pixel value (−13 … 13) → RGB colour, straight from its trits.
- **`VideoChip`** (in `triada-core`):
  - the I/O registers SCROLL_X, SCROLL_Y and BORDER;
  - `render()`: tile map → background, scrolling, sprites in order with transparency, mirroring, flipping and clipping, then the border;
  - output as an `int[]` of RGB pixels, 240 × 168, allocating nothing per frame.
- **`Machine`**: renders after every CPU turn, and has a new `Machine.loadProgram()` shared by the CLI and the desktop.
- **`triada run … --dump-screen out.png`** now saves the last picture (`ScreenDump`, using the JDK's `javax.imageio`).
- **The desktop window** shows the picture at the largest whole-number scale that fits, with nearest-neighbour filtering. A `FrameClock` keeps the machine at exactly 60 frames per second on any monitor, and a program can be given on the command line.
- **`programs/colors.tasm`**: 27 colour bars and a ball that bounces, mirrored and flipped so its highlight always faces where it is going.
- **Golden images**: four stored pictures (`palette`, `tiles-mirrored-scrolled`, `sprites`, and `colors-60` from the demo) that whole scenes are compared against pixel by pixel.
- **The web spike** (`docs/spikes/m06-teavm/`): `triada-core` and the assembler compiled to JavaScript with TeaVM and run in Node.js.
- **37 new tests**, and decisions D-020 to D-022.

## Why it was built this way

**The renderer lives in `triada-core` and outputs plain numbers.** The chip produces an `int[]` of colours and knows nothing about windows or graphics cards (CLAUDE.md §2). Because of that, the same picture can go to a desktop window, a PNG file, a test comparing pixels, or (later) a web page. The desktop only copies the numbers into a texture.

**Three steps through buffers that are made once.** The chip first decodes the whole tile map into a 216 × 144 *background buffer* of pixel values. Next it copies that buffer onto the screen with the scroll offset, then draws the sprites. Last, it converts to RGB and adds the border. Each step is easy to understand and test on its own, and the buffers are reused, so a frame allocates nothing. Rendering takes about 0.15 ms per frame, about 1 % of the 16.7 ms a frame lasts.

**The chip reads memory through `Bus.peek()`.** Peek has no side effects (M2, D-008) and costs only a few comparisons per word, and the chip reads about 8,000 words per frame. Giving it direct access to the RAM array would be slightly faster but would break the bus's single-path design, so we didn't.

**A 12-pixel border; out-of-range values handled the hardware way** ([D-020](../DECISIONS.md#d-020--a-12-pixel-border-the-picture-is-240--168---milestone-m06-2026-11-07), [D-021](../DECISIONS.md#d-021--video-values-outside-the-defined-range---milestone-m06-2026-11-07)). The spec didn't fix the border width or say what odd register values mean, so we decided and recorded both.

**Golden images *and* hand-computed tests.** A golden image only proves "the picture didn't change". If the code was wrong when the image was made, the image is wrong too. So every rule of SPEC §4 also has a test whose expected value is worked out by hand. The golden images catch everything else: a pixel anywhere in a whole scene that changes unexpectedly. Each golden image was looked at by a person (us) before it was committed.

**A fixed time step in the frontend.** Monitors refresh 60, 75, 144 … times per second, but the machine must run at exactly 60 frames per second. `FrameClock` turns real elapsed time into a whole number of machine frames. It is a separate class so it can be tested without a window.

## How it works — the plain-language version

### 27 colours from three trits
Each pixel is a number from −13 to +13, which is exactly three trits. The video chip reads them as the brightness of red, green and blue:

| trit | brightness |
|---|---|
| `-` | off (0) |
| `0` | half (128) |
| `+` | full (255) |

So `+--` (value 5) is full red, no green, no blue: **red**. `+++` (13) is **white**, `---` (−13) is **black**, and `000` (0) is half of everything: **gray**. Three channels with three levels each give 3 × 3 × 3 = 27 colours. That's the whole palette, with nothing to look up.

### Tiles: a mosaic of small pictures
Storing a separate colour for each of the screen's 31,104 pixels would fill half of RAM. Instead, the screen is a mosaic: 27 columns × 18 rows of squares (the *tile map*), and each square says which of 243 small 8 × 8 pictures (the *tiles*) to show there. A letter, a brick, a piece of grass: draw it once as a tile, then use it as often as you like.

```
 tile map (27 x 18 numbers)          tiles (243 little pictures)
 +----+----+----+----+               tile 5:  . . # # # # . .
 |  5 |  5 |  7 | -7 | ...                    . # . . . . # .
 +----+----+----+----+                        ...
 |  0 |  0 | 12 |  0 | ...           tile 7:  an arrow pointing right
```

A **negative** number in the tile map means "the same tile, mirrored left-right", so `-7` is the arrow pointing left. One tile serves both directions.

A tile row is 8 pixels, stored in two words, because one word holds four pixels (SPEC §4.3). For example, red (5), black (−13), white (13) and gray (0) pack into 5 × 19683 + (−13) × 729 + 13 × 27 + 0 = 89,289.

### Scrolling: sliding the mosaic
SCROLL_X and SCROLL_Y slide the whole background. Whatever slides out on one side comes back in on the other, like a picture printed on a rolling drum. With SCROLL_X = 1, the screen shows the background one pixel further right, so everything appears to move left by one pixel. A game that changes SCROLL_X every frame gets smooth sideways scrolling for free.

### Sprites: stickers on top
A sprite is a tile that can be placed anywhere, pixel by pixel, on top of the background. The sprite table in RAM holds 27 of them, four numbers each: X, Y, which tile, and FLAGS (0 hidden, +1 visible, −1 visible and upside down). A negative tile number mirrors the sprite, just as in the tile map. In a sprite, gray (value 0) is *transparent*: the background shows through, so sprites can have round shapes. Sprites are drawn in order 0 to 26, so a higher number covers a lower one. A sprite partly off the screen is simply cut off at the edge.

### The border
Around the screen is a 12-pixel frame in the colour set by the BORDER register, like the area around the picture on an old TV. The whole picture is 240 × 168 pixels.

### On the desktop
The desktop window shows the picture enlarged by a whole number: 4× in a 960 × 672 window, 3× if the window gets smaller, and so on. Each TRIADA pixel becomes a crisp 4 × 4 square. Scaling by 3.7 would make some pixels wider than others, and smoothing them would blur the pixel art. *Nearest-neighbour filtering* means "no smoothing: copy the nearest pixel".

### The demo
`colors.tasm` builds 27 one-colour tiles in a loop. It uses a cute trick: four pixels of colour *c* are the word *c* × 20,440, because 20,440 = 19,683 + 729 + 27 + 1. It then lays them out as bars and copies a ball picture into tile 28. Every frame it moves the ball one pixel and executes WAIT. When the ball hits an edge it bounces, and it mirrors or flips its sprite so that the white highlight faces the direction of travel.

### The browser check
Could this machine one day run on a web page? libGDX can build for browsers with a tool called TeaVM, which translates Java into JavaScript. We tried it early. TeaVM translated the whole machine and the assembler, and in Node.js (a program that runs JavaScript outside a browser) the result assembled a hello program, ran it, drew a frame, and ran a demanding loop at about 1.3× real speed. One small incompatibility was found and fixed on the spot ([D-022](../DECISIONS.md#d-022--web-spike-teavm-compiles-triada-core-and-the-assembler---milestone-m06-2026-11-07)).

## How it works — the programmer's version

### 1. `Palette`
[`Palette.java`](../../triada-core/src/main/java/org/triada/core/video/Palette.java) builds a 27-entry table once. For value v, `u = v + 13` has base-3 digits that are the trits + 1, so `LEVEL[u / 9]`, `LEVEL[u / 3 % 3]` and `LEVEL[u % 3]` are red, green and blue. `Palette.rgb()` is a table lookup. `Palette.fold()` keeps the lowest three trits of any word (`floorMod(v + 13, 27) − 13`) for the BORDER register (D-021).

### 2. `VideoChip`: registers and buffers
[`VideoChip.java`](../../triada-core/src/main/java/org/triada/core/video/VideoChip.java). The constants `SCREEN_WIDTH`/`HEIGHT` (216, 144), `BORDER` (12), `FRAME_WIDTH`/`HEIGHT` (240, 168) and the table sizes come straight from SPEC §4.

As an `IoDevice` on k = 7 … 9, `VideoChip.read()` and `VideoChip.write()` just store and return the three registers.

Four buffers are allocated once:
- `bg`: the background, pixel values;
- `screen`: after scrolling and sprites;
- `frame`: RGB output;
- `tile`: one decoded tile.

### 3. `VideoChip.render()`, step by step
```java
drawBackground();
scrollAndCompose();
for (int i = 0; i < SPRITE_COUNT; i++) { drawSprite(i); }
output();
```

**`decodeTile(v)`** is used by both the background and the sprites:
- It picks tile |v| (|v| > 242 → tile 0) and remembers whether v was negative.
- For each of the 16 words it uses the offset trick once more: `u = word + Word.MAX` has base-27 digits equal to pixel + 13. So `u / 19683 − 13` is pixel 0, `u / 729 % 27 − 13` pixel 1, `u / 27 % 27 − 13` pixel 2 and `u % 27 − 13` pixel 3.
- `put()` writes each pixel to column x, or to 7 − x when mirrored.

**`drawBackground()`** decodes each of the 486 map cells (address `MAP + row·27 + col`, read with `bus.peek`) and copies its 8 rows into `bg`.

**`scrollAndCompose()`** reduces the scroll values with `Math.floorMod` (negative and huge values work). Each screen row is then the background row `(y + sy) % 144`, rotated left by `sx`, which is two `System.arraycopy` calls:
```java
int first = SCREEN_WIDTH - sx;
System.arraycopy(bg, bgRow + sx, screen, out, first);
System.arraycopy(bg, bgRow, screen, out + first, sx);
```
- The first copy takes the part of the background row from `sx` to the end.
- The second takes the part from 0 to `sx`, which wraps around to the right end of the screen row.

**`drawSprite(i)`**:
- reads X, Y, TILE and FLAGS at `SPRITES + 4i`;
- returns unless FLAGS is +1 or −1;
- decodes the tile (mirrored if TILE < 0);
- for each of the 64 pixels, reads row `7 − y` when flipped, skips value 0 (transparent), and skips coordinates outside 0 … 215 / 0 … 143 (clipping).

**`output()`** fills `frame` with the folded border colour, then writes `Palette.rgb()` of every screen pixel at offset (12, 12).

`VideoChip.frame()` returns the RGB buffer. `VideoChip.screenPixel()` returns the pixel value at a screen position, for tests and the debugger.

### 4. Machine integration
[`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java): `Machine.runFrame()` is now CPU, then `video.render()`, then FRAME + 1 (SPEC §6). `Machine.powerOn()` also resets the video registers. `Machine.loadProgram()` checks that a `TriImage` fits in RAM, loads it, and sets PC and SP. The CLI's `RunCommand` now uses it instead of its own copy. `Machine.video()` exposes the chip.

### 5. CLI: `--dump-screen`
[`ScreenDump.java`](../../triada-cli/src/main/java/org/triada/cli/ScreenDump.java): `ScreenDump.toImage()` wraps the frame in a `BufferedImage` of type `TYPE_INT_RGB`, which stores exactly 0xRRGGBB ints, and `ScreenDump.writePng()` saves it with `ImageIO`. [`RunCommand`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java) writes it when the run ends, however it ends.

### 6. Desktop
[`TriadaApp.java`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java):
- `create()` makes a `Pixmap` (RGBA8888), a `Texture` with `TextureFilter.Nearest`, and a `SpriteBatch`.
- `resize()` works in back-buffer pixels, the real screen pixels, so integer scaling stays exact when Windows scales windows to 125 %.
- `render()` asks the `FrameClock` how many machine frames are due, runs them, uploads the newest picture (`upload()` writes R, G, B, 255 bytes into the pixmap and calls `texture.draw`), and draws it centred at `MachineScreen.scaleFor()` scale. (Since M12, uploading and drawing live in `MachineScreen` in `triada-app`, shared with the web version, and `FrameClock` moved there too.) The texture is drawn as is: libGDX shows a `Pixmap` upright. (The first M6 version flipped it vertically, and the picture was upside down. M7 found and fixed this; see its "Problems" section.)

[`FrameClock.java`](../../triada-app/src/main/java/org/triada/app/FrameClock.java): `FrameClock.advance(seconds)` adds the elapsed time (capped at 4 frames' worth) to an accumulator and returns the number of whole frames it now holds.

[`DesktopLauncher.java`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java) builds the `Machine`, loads a `.tasm` or `.tri` given as the first argument, and opens the window.

### 7. Golden images
[`GoldenImages.java`](../../triada-core/src/test/java/org/triada/core/video/GoldenImages.java): `GoldenImages.check()` compares a frame with `src/test/resources/golden/<name>.png`. On a mismatch it reports the number of differing pixels and the first one, and saves the actual picture into `build/golden-diff/`. With `-Dtriada.updateGolden=true` it writes new golden images instead. The paths come from system properties set in [`triada-core/build.gradle.kts`](../../triada-core/build.gradle.kts). [`VideoScene.java`](../../triada-core/src/test/java/org/triada/core/video/VideoScene.java) is the test helper that writes tiles, the map, sprites and registers in one line each, with its own copy of the packing formula.

## Build it yourself from scratch
1. **Palette first.** 27 entries straight from the trits. Test the spec's table, then all 27 colours against a separate trit-by-trit calculation.
2. **Decode one tile.** Write a test tile with a different value in every pixel, so a swapped pixel or row is noticed. **Pitfall:** pixel 0 is the *highest* trits of the word; it's easy to unpack the four pixels in reverse order.
3. **Background, then scroll.** Test scrolling by rendering an unscrolled random background, then checking *every* scrolled pixel against `((x + sx) mod 216, (y + sy) mod 144)`. Include negative and huge scroll values. **Pitfall:** Java's `%` is negative for negative numbers; use `floorMod`.
4. **Sprites.** Use an asymmetric tile (a triangle) so mirroring and flipping are visible. Test transparency, order, all four clipping edges, and coordinates far off-screen.
5. **Border.** Decide its width. Test the first and last pixels of border and screen.
6. **Allocation.** Make all buffers fields, and check with the per-thread allocation counter that rendering allocates nothing.
7. **Golden images last.** Generate them, *look at them*, and commit them. Make the comparison report the first differing pixel and save the actual picture.
8. **Frontend.** Copy the frame into a texture with nearest filtering. Scale by `min(width / 240, height / 168)`. Run the machine with a fixed time step, not "one frame per refresh". **Pitfall:** with OS window scaling, the window size and the back buffer size differ; scale using the back buffer.

## How we know it works

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`, with 224 tests in `triada-core`, 98 in `triada-assembler`, 32 in `triada-cli` and 3 in `triada-desktop`.

| Test class | Tests | What it proves |
|---|---|---|
| [`VideoChipTest`](../../triada-core/src/test/java/org/triada/core/video/VideoChipTest.java) | 27 | The palette (spec table, all 27 against the trits, fold). The all-gray power-on picture. Pixel order in a word, the two words per tile row, map cell addressing at all four corners. Mirroring; values beyond 242 → tile 0; gray is opaque in the background. **Scrolling checked on every one of the 31,104 pixels** for right, down, wrap-around, negative and huge values. Sprites: position, transparency, not scrolled, mirror/flip/both, order, hidden (0 and other values), clipping at all four edges, TILE > 242. The border's exact edges and folding; register read-back and power-on. The machine renders after every frame. Rendering allocates nothing. |
| [`GoldenImageTest`](../../triada-core/src/test/java/org/triada/core/video/GoldenImageTest.java) | 3 | Whole scenes against stored PNGs: the palette, random mirrored tiles scrolled by (13, 7), and a sprite scene with all orientations, overlaps and clipping. |
| [`ColorsProgramTest`](../../triada-cli/src/test/java/org/triada/cli/ColorsProgramTest.java) | 3 | `colors.tasm` through the real CLI: all 27 colours are on screen and the border is blue; the ball moves exactly 20 pixels in 20 frames and its highlight changes side at the bounce; frame 60 matches the golden image. |
| [`RunCommandTest`](../../triada-cli/src/test/java/org/triada/cli/RunCommandTest.java) | 14 (2 new) | `--dump-screen` writes a 240 × 168 PNG with the right border and tile pixels, and reports an unwritable path. |
| [`FrameClockTest`](../../triada-app/src/test/java/org/triada/app/FrameClockTest.java) | 3 | 60 frames per second at 30, 60, 75 and 144 Hz, ±1 frame after 10 seconds *and* after an hour (no drift). Pauses are capped. Integer scale. |

See the demo in a window:

```bash
./gradlew :triada-desktop:run --args="programs/colors.tasm"
```

A 960 × 672 window titled "TRIADA-85 - colors.tasm" opens and shows the bars and the bouncing ball. Or save a picture instead:

```bash
triada-cli/build/install/triada/bin/triada run programs/colors.tasm --frames 60 --dump-screen colors.png
```

That writes the same picture as the golden image `triada-cli/src/test/resources/golden/colors-60.png`.

Re-run the web spike (needs Node.js):

```bash
./gradlew -p docs/spikes/m06-teavm teavm
```

```bash
node -e "require('./docs/spikes/m06-teavm/build/js/spike.js').main([])"
```

Output:

```
HELLO FROM TERNARY JS
exit=0 border=ff0000
60 frames (3.5M instructions + 60 renders) in 791 ms
```

**Mutation check.** We broke the chip three ways at once: tiles were never mirrored, the scroll direction was reversed, and sprites were drawn 26 → 0. 9 tests failed, including both golden images that cover those features, `negativeMapValuesMirrorTheTile`, `scrollRight` and `laterSpritesAreOnTop`. With the code restored, all pass.

**Speed.** Rendering alone takes about **0.15 ms** per frame. The CPU benchmark (which now includes rendering in every frame) reported 7.6× real time for the mixed program and 3.5× for the trit-heavy one. That is lower than M3's 13× and 5.6×, but most of the difference was the PC itself: M3's exact code, checked out and benchmarked on the same afternoon, measured 8.0× and 3.7×. Like for like, M6 adds about 0.1 ms per frame.

## Problems we hit and how we solved them
- **Mistakes in our own demo.**
  - The first draft of `colors.tasm` used `TILES + 16 * BALL`, but the assembler has no `*` (SPEC §9.1). It also had a muddled attempt at computing 16 × t, and a wrong comment about which colour value is dark red.
  - The first *running* version showed the highlight on the wrong side when moving vertically, and its black bar was invisible on the black background.

  All of this was found by reading the code carefully and by looking at screen dumps before writing the tests. The fixes: an explicit constant, `MUL`, white lines around the bars, and the flip logic inverted.
- **A test that looked in the wrong place.** The first ball-movement test searched for the ball where it was actually over the colour bars, which the search deliberately skips. The test now uses frames where the ball is over the black background.
- **Floating-point time.** The first `FrameClock` test expected exactly 600 frames in 10 seconds at 144 Hz and got 599: 1,440 additions of 1/144 land a hair below 10.0. The real requirement is *no drift*, so the test now allows ±1 frame and checks a whole hour.
- **The web spike needed several attempts.** TeaVM 0.13.0 crashed with a `NullPointerException` even on a trivial program. Changing the bytecode level, the JDK and the path style didn't help. The tool's own help text showed the real cause: its `-p` option is only an extra classpath for "wait" mode, and classes must be on the tool's own classpath. After that, the only real incompatibility was `Integer.toUnsignedLong`, now replaced in `SystemDevice`.
- **A benchmark scare.** After M6 the benchmark was much slower than M3's numbers. Rendering alone takes only 0.15 ms, and M3's exact code turned out to be just as slow on that afternoon's (busier, hotter) PC.

- **Found later (in M7): the window showed the picture upside down.** M6's window check used `colors.tasm`, whose bars look the same either way up, so the wrong flip went unnoticed. It is fixed in M7.

## Limitations and what comes next
- **No font yet.** The ROM copies a font into tiles 32–126 at boot (M8). Until then programs bring their own tiles.
- **Every frame is rendered, even headless.** At 0.15 ms per frame this costs little, and it keeps "the picture after frame N" well defined for `--dump-screen` and tests.
- **No input or sound in the window.** That's M7. The window also can't load a program by drag-and-drop yet (M9).
- **The libGDX part of the web build is untested.** The spike only proved the machine itself compiles and runs (D-022).
- **Next, M7:** keyboard and joystick into the KEY queue and joystick registers, and the three-voice sound chip with its ternary square wave.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Video chip:** the part of a computer that turns memory into a picture.
- **Pixel / Palette:** one dot of the picture / the list of colours a machine can show.
- **RGB:** describing a colour by how much red, green and blue light it has.
- **Tile map / Sprite / Tile:** see M2; this milestone makes them visible.
- **Mirroring / Flipping:** reversing a picture left-right / upside down.
- **Transparency:** a colour that isn't drawn, so what is behind shows through.
- **Scrolling:** sliding the whole background; with wrap-around it reappears on the other side.
- **Clipping:** cutting off the parts of a picture that fall outside the screen.
- **Border:** the frame of one colour around the screen.
- **Frame buffer:** the block of memory holding a finished picture.
- **Texture / Nearest-neighbour filtering:** a picture uploaded to the graphics card / enlarging it by copying the nearest pixel, with no smoothing.
- **Integer scaling:** enlarging by a whole number, so every pixel stays the same size.
- **Fixed time step:** running a simulation in equal steps (here 1/60 s), whatever the display does.
- **Golden image:** a stored "known good" picture that new output is compared with.
- **Spike:** a quick, throwaway experiment to answer one question.
- **TeaVM / Node.js:** a tool that translates Java bytecode into JavaScript / a program that runs JavaScript outside a browser.
