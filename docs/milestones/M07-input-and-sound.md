# Milestone 07 — Input and sound

## In one paragraph
A home computer you can't touch or hear is only half a computer. This milestone gives the TRIADA-85 hands and a voice. The PC keyboard now types into the machine's keyboard queue, the arrow keys act as its three-position joystick, and Z, X and C are its fire buttons. A new sound chip with three voices plays notes in three waveforms. One is the machine's signature "ternary square", which steps up, to the middle, down, and back to the middle. The sound goes to the PC's speakers through a separate audio thread, so the picture and the sound never hold each other up. The demo `piano.tasm` turns the keyboard into a little piano: the letter keys play notes, and the joystick picks the waveform.

## What was built
- **`SoundChip`** (in `triada-core`):
  - registers V1–V3 FREQ/VOL/WAVE;
  - three voices with exact integer phase and three waveforms (ternary square, triangle, ternary noise);
  - a mix that can never clip;
  - 735 samples per frame at 44,100 Hz, allocating nothing.
- **`Machine.runFrame()`**: CPU, then video, then sound, then FRAME + 1.
- **`triada run … --dump-audio out.wav`** (`WavRecorder`) saves everything the chip played as a standard WAV file.
- **Desktop:**
  - `InputMapper`: typed keys go to the KEY queue, arrows to the joystick, Z/X/C to the buttons, and losing focus releases everything;
  - `SampleRing`: the thread-safe buffer between the render thread and the audio thread;
  - `SoundOutput`: the audio thread feeding libGDX's `AudioDevice`.
- **A bug fix from M6:** the desktop drew the picture **upside down**. M7's first real-window test revealed it.
- **`programs/piano.tasm`**: a drawn keyboard; 13 keys with their notes spread over the three voices in turn; fading notes; joystick → waveform; the border shows the waveform; a marker over the last key.
- **40 new tests**, decisions D-023 to D-025, and notes in SPEC §5 and §7.

## Why it was built this way

**Sound samples are made in `triada-core`; only playing them is the frontend's job.** As with the picture in M6, the chip produces plain numbers (`short[]`). That makes the sound testable sample by sample, saveable by the CLI, and reusable by a future web version.

**Exact integer phase** ([D-023](../DECISIONS.md#d-023--sound-44100-hz-exact-phase-levels-noise-once-per-frame---milestone-m07-2026-11-07)). A typical synthesizer keeps its position in the wave as a floating-point fraction and accumulates tiny rounding errors. We keep it as a whole number from 0 to 44,099 and add FREQ every sample. A 441 Hz tone then repeats every 100 samples exactly, forever, which lets the tests predict every single sample.

**The registers are read once per frame.** That is simple and matches how programs drive sound (from their frame loop). The cost is that nothing shorter than a frame can be played. D-023 describes the alternative, should it ever be needed.

**Two threads, one small shared class** ([D-025](../DECISIONS.md#d-025--desktop-audio-an-audio-thread-fed-through-a-ring-buffer---milestone-m07-2026-11-07)). Feeding the sound card *blocks* until it has room. So the render thread runs the machine and only drops samples into `SampleRing`, and a separate audio thread does the waiting. Only `SampleRing` is touched by both threads, and it is small enough to test completely, including from two threads at once.

**Input logic without a window.** `InputMapper` holds all the rules (which key goes where, cancelling opposite arrows, releasing on focus loss) and is fed events by the app. The tests call it directly, with no window, OpenGL or keyboard needed.

**Key codes and double roles** ([D-024](../DECISIONS.md#d-024--keys-joystick-and-buttons-on-a-pc-keyboard---milestone-m07-2026-11-07)). Enter is 10, the same code the debug port prints as a new line. A letter like Z both types "z" and presses button A.

## How it works — the plain-language version

### Keys, stick and buttons
The TRIADA's keyboard queue (M2) now has something to listen to. Every character you type on the PC lands in the queue, where the program picks it up by reading KEY. The arrow keys are the joystick: the program reads −1 (left), 0 or +1 (right) from JOY_X, and the same for up and down from JOY_Y. Hold left and right together and they cancel out, like a real stick that can't point both ways. Z, X and C are the fire buttons A, B and C.

### Making a sound from numbers
A loudspeaker is a paper cone pushed back and forth. A sound is a list of positions for the cone, 44,100 of them per second. Each one is a *sample*: a number between −32,768 (all the way in) and +32,767 (all the way out). Repeat a pattern 440 times per second and you hear the note A.

Each of the three voices has three settings:
- **FREQ:** how many times per second the pattern repeats (440 = the note A; 0 = silence);
- **VOL:** 0 (silent) to 13 (loudest);
- **WAVE:** the shape of the pattern.

The three shapes:

```
 ternary square (+1)        triangle (0)               noise (-1)
  +--+      +--+               /\      /\               +-+   +-+-+
  |  |      |  |              /  \    /  \              | |   |   |   +
 -+  +--+  -+  +--          -/    \  /    \          -+-+ +-+-+   +-+-+
        |  |                       \/      \/          (random -1/0/+1,
        +--+                                            new every quarter)
  +1, 0, -1, 0 in quarters   up, down, up                FREQ = graininess
```

The ternary square is the TRIADA's signature: a square wave with a *middle* step, because a trit has three values. It sounds a little softer and rounder than the harsh two-level square of binary home computers.

The three voices are added together. Each voice at full volume swings ±10,000, so even all three at full volume (±30,000) stay inside the speaker's range. They never *clip*, which is what we call squashing a sound against the limit and distorting it.

### How the exact pitch works
The chip keeps each voice's position in its pattern as a counter from 0 to 44,099. Every sample it adds FREQ. When the counter passes 44,100, one full period is over and it wraps around. With FREQ = 441, the counter reaches 44,100 after exactly 100 samples, so the tone repeats every 100 samples. Counting whole numbers like this never drifts. The pattern is chosen by the counter: in the first quarter (0 to 11,024) the square wave is up, in the second it is in the middle, and so on.

### Getting sound out of the PC
Sixty times a second the machine produces 735 new samples. The sound card wants a steady stream of exactly 44,100 per second and makes whoever feeds it *wait* until it has room. If the picture loop did that waiting, the picture would stutter. So a second worker, the *audio thread*, does nothing but carry samples from a small waiting room (the *ring buffer*) to the sound card. The two clocks never agree perfectly, so the waiting room has two rules:
- when it's too full, the oldest samples are thrown away, so the sound never lags far behind the keys;
- when it's empty, the last value is repeated instead of jumping to silence, because a sudden jump makes a click.

### The piano
`piano.tasm` draws a keyboard out of tiles. Five tiles are enough: white, black, background, a white key's edge, and the red marker. Every frame it does three things:
1. reads the joystick and remembers the chosen waveform, colouring the border white for noise, blue for triangle and red for the square;
2. lets each playing note fade: a counter runs from 52 down to 0, and the volume is counter ÷ 4;
3. takes every key waiting in the queue. It looks the key up in a table of 13 notes (character, frequency, marker position), gives the note to the next voice in turn, sets the voice to full volume, and moves the marker.

Because notes go to the three voices in turn, you can play three-note chords.

## How it works — the programmer's version

### 1. `SoundChip`
[`SoundChip.java`](../../triada-core/src/main/java/org/triada/core/sound/SoundChip.java):
- Constants: `SAMPLE_RATE` = 44,100, `SAMPLES_PER_FRAME` = 735, `VOICE_PEAK` = 10,000, `MAX_VOLUME` = 13, and the waveform numbers.
- `SoundChip.read()` and `SoundChip.write()` map register k to (voice, field) with `(k − 10) / 3` and `(k − 10) % 3`.
- `SoundChip.renderFrame()` fills the reused `frameSamples` buffer by calling `SoundChip.generate()`, which mixes the three voices for each sample:

```java
long mix = 0;
for (int v = 0; v < VOICES; v++) {
    mix += (long) level(v) * effectiveVolume(v);
}
out[offset + i] = (short) (mix * VOICE_PEAK / ((long) SAMPLE_RATE * MAX_VOLUME));
```
- `level(v)` is the voice's current value in units of 1/44,100 of full scale, so ±44,100 is the peak.
- Multiplied by a volume of 0 … 13, the largest possible sum is 3 × 44,100 × 13. The last line scales that to ±30,000, using `long` so nothing overflows.

`level(v)` computes the quarter `q = 4·phase / 44,100` (0 … 3):
- **square:** +44,100 in quarter 0, −44,100 in quarter 2, 0 otherwise;
- **triangle:** a straight line through 4·phase, folded twice;
- **noise:** a new trit from `nextRandomTrit()` (xorshift32, mod 3) whenever q changes.

Then it advances `phase += FREQ` with wrap-around. `SoundChip.foldWave()` keeps the lowest trit of WAVE; `effectiveVolume()` clamps VOL to 0 … 13.

### 2. `Machine` and the CLI
[`Machine.java`](../../triada-core/src/main/java/org/triada/core/Machine.java) attaches the chip to k = 10 … 18, calls `sound.renderFrame()` in `runFrame()`, resets it in `powerOn()`, and exposes it as `Machine.sound()`.

[`WavRecorder.java`](../../triada-cli/src/main/java/org/triada/cli/WavRecorder.java): `WavRecorder.add()` appends each frame's samples as little-endian bytes, and `WavRecorder.write()` prepends the 44-byte WAV header (RIFF, fmt = PCM mono 44,100 Hz 16-bit, data). [`RunCommand`](../../triada-cli/src/main/java/org/triada/cli/RunCommand.java) records every frame when `--dump-audio` is given.

### 3. Desktop input
[`InputMapper.java`](../../triada-desktop/src/main/java/org/triada/desktop/InputMapper.java):
- `InputMapper.keyDown()` / `keyUp()` track the four arrows, set JOY_X = right − left and JOY_Y = down − up, and set the buttons for Z/X/C.
- `InputMapper.keyTyped()` translates a character with `InputMapper.keyCode()` (D-024) and calls `InputDevice.pushKey`.
- `InputMapper.releaseAll()` clears everything.

[`TriadaApp.java`](../../triada-desktop/src/main/java/org/triada/desktop/TriadaApp.java) installs a libGDX `InputAdapter` that forwards the three events. [`DesktopLauncher.java`](../../triada-desktop/src/main/java/org/triada/desktop/DesktopLauncher.java) registers a window listener whose `focusLost` calls `TriadaApp.focusLost()`.

### 4. Desktop sound
[`SampleRing.java`](../../triada-desktop/src/main/java/org/triada/desktop/SampleRing.java) is a ring buffer whose methods are all `synchronized`:
- `SampleRing.write()` drops the oldest sample when full;
- `SampleRing.read()` always delivers the requested count, padding with the last sample, and returns how many were real;
- `dropped()` and `underruns()` count both events.

[`SoundOutput.java`](../../triada-desktop/src/main/java/org/triada/desktop/SoundOutput.java) starts a daemon thread `triada-audio` that loops "read 512 samples from the ring → `device.writeSamples`". `SoundOutput.submit()` is called on the render thread after every machine frame; `SoundOutput.dispose()` stops the thread and closes the device. `TriadaApp` opens the device with `Gdx.audio.newAudioDevice(44100, true)`. If that fails (no sound card), it logs the problem and runs silently. The launcher sets OpenAL to 6 × 512-sample buffers with `setAudioConfig(16, 512, 6)`.

### 5. The piano program
[`programs/piano.tasm`](../../programs/piano.tasm) is commented line by line. Points worth studying:
- Each voice's registers are reached through one computed base address: `R6 = IO_BASE + 3 × voice`, then `ST R5, [R6+V1_FREQ]`, `[R6+V1_VOL]`, `[R6+V1_WAVE]`.
- The fade uses TRIADA's rounding division: `counter DIV 4`, so 52 → 13, …, 3 → 1, 2 → 0 (a tie, rounded toward zero).
- Lower-case letters become upper case with `ADDI R0, -32`.
- The note table has three words per entry and ends with 0; `ADDI R2, 3` steps through it.

## Build it yourself from scratch
1. **Sound chip first, with exact phase.** Keep phase as an integer 0 … rate − 1 and add the frequency every sample. Pick test frequencies that divide the rate (441 Hz → 100 samples per period), so expected samples are whole numbers.
2. **Test each waveform sample by sample:** the square's four quarters, the triangle's peaks and its symmetry, and noise having only three levels that change only at quarter boundaries.
3. **Test continuity:** three frames generated separately must equal 2,205 samples generated at once. Otherwise you'll hear a click 60 times a second.
4. **Choose levels so the worst case can't clip:** three voices at full volume must fit in 16 bits.
5. **A WAV writer** is 44 header bytes plus the samples. It lets you *listen* to your chip without any audio code, and the JDK's `AudioSystem` can read the file back in a test.
6. **Input mapping as a plain class:** test the arrow cancellation, the double role of Z/X/C, special key codes, and releasing on focus loss.
7. **Audio thread + ring buffer.** **Pitfall:** never let the render thread call a blocking audio write. **Pitfall:** decide what happens on overflow (drop the oldest) and underflow (hold the last value). Test with two real threads.
8. **Test in the real window.** Unit tests could not catch the upside-down picture; looking at the window did.

## How we know it works

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`, with 239 tests in `triada-core`, 98 in `triada-assembler`, 46 in `triada-cli` and 14 in `triada-desktop`. 40 of them are new.

| Test class | Tests | What it proves |
|---|---|---|
| [`SoundChipTest`](../../triada-core/src/test/java/org/triada/core/sound/SoundChipTest.java) | 15 | The registers at the spec's addresses. Silence at power-on and for FREQ 0, negative FREQ or VOL 0. **The ternary square sample by sample**: 25 × +10,000, 25 × 0, 25 × −10,000, 25 × 0. The triangle's peaks, slope and symmetry. Volume scaling and clamping. Three voices summing to exactly ±30,000. 440 Hz over a full second. Frame-by-frame = all at once. Noise has three levels changing only at quarters, is repeatable and roughly even. WAVE folding. One frame per machine frame. No allocation. |
| [`AudioDumpTest`](../../triada-cli/src/test/java/org/triada/cli/AudioDumpTest.java) | 4 | The WAV header field by field. Java Sound reads the file as 44.1 kHz 16-bit mono PCM. The samples are the chip's. An unwritable path is reported. |
| [`PianoProgramTest`](../../triada-cli/src/test/java/org/triada/cli/PianoProgramTest.java) | 10 | The real `programs/piano.tasm`, played through the KEY queue and joystick: silent at start, keyboard drawn, notes on voices 1 → 2 → 3 → 1, several keys in one frame, other keys ignored, the fade to 0 in 52 frames, the joystick → waveform → border colour, the marker position, and a golden screenshot. **`aNoteActuallySoundsAtItsPitch`** counts exactly 146 periods of A4 in 14,700 samples. |
| [`InputMapperTest`](../../triada-desktop/src/test/java/org/triada/desktop/InputMapperTest.java) | 6 | Arrows → JOY_X/Y with cancelling. Z/X/C → buttons. Typed keys in order. Special key codes. Foreign keys ignored. Focus loss releases everything. |
| [`SampleRingTest`](../../triada-desktop/src/test/java/org/triada/desktop/SampleRingTest.java) | 5 | Order. Full → oldest dropped. Empty → last value held. 1,000 wrap-arounds. A writer and a reader thread at once lose nothing when there's room. |

Play the piano (the window must have focus; the letter keys play, the arrows change the sound):

```bash
./gradlew :triada-desktop:run --args="programs/piano.tasm"
```

Record a tone without a window and listen to it in any audio player:

```bash
triada-cli/build/install/triada/bin/triada run programs/colors.tasm --frames 60 --dump-audio silent.wav
```

`colors.tasm` makes no sound, so this file is a second of silence, 44 + 60 × 735 × 2 = 88,244 bytes. The test programs in `AudioDumpTest` play a 441 Hz ternary square.

**In the real window.** We started `piano.tasm` and sent it keystrokes from outside: the left arrow held for 200 ms, then D. A screenshot showed:
- the border switched to white (noise);
- the marker above the E key;
- the keyboard the right way up (after the fix below).

No audio error was logged, so the audio device opened. While idle, the process used about 30 % of one CPU core, so there is no busy-waiting thread. **What we could not check: how it sounds.** No one listened to the speakers during this milestone. The sample-exact tests and the WAV file cover the sound's *content*. Smooth playback is covered by `SampleRing`'s tests and the design, not by ear.

**Mutation check.** We broke three things at once: the square went +, −, 0, 0; up and down on the joystick were swapped; and an empty ring padded with 0 instead of the last value. 6 tests failed in three modules, including `ternarySquareStepsPlusZeroMinusZeroInQuarters`, `arrowsAreTheJoystick` and `whenEmptyTheLastSampleIsHeldNotZero`. With the code restored, all pass.

## Problems we hit and how we solved them
- **The picture had been upside down since M6.** The first screenshot of the piano in a real window showed the keyboard at the top and the marker pointing up. In M6 we had told libGDX to flip the texture, believing that image row 0 ends up at the bottom; in fact libGDX already draws a `Pixmap` upright. M6's only window check used `colors.tasm`, whose bars look the same either way up. The fix is one argument; the M6 document has been corrected. The lesson is now step 8 of "Build it yourself": test in the real window with an asymmetric picture.
- **Simulated arrow keys didn't arrive.** Our first keystroke injection used plain virtual-key events. Without the "extended key" flag, Windows reports VK_LEFT as the numeric keypad's 4. With the flag (and a 200 ms hold, like a real finger), the joystick worked. This was a problem in the *test tool*, not in TRIADA.
- **Mistakes in our own tests.**
  - The first pitch test used a wrong rising-edge condition and counted 0. It also kept re-striking the note, which spread it over all three voices. It now strikes once and expects exactly 146 rising edges.
  - The first two-thread `SampleRing` test was wrong too: the writer can legitimately run ahead and make the ring drop samples. The test now gives the ring room for everything.
  - A WAV test left the file open, so Windows refused to delete the test's temporary folder; it now closes the stream.
- **Noise started with a silent quarter.** The first version only drew a new random trit when the quarter *changed*, so a fresh noise voice was silent for its first quarter period. A "no quarter yet" start value (−1) fixed it.

## Limitations and what comes next
- **Sound changes take effect per frame** (D-023): nothing shorter than 16.7 ms.
- **Latency:** up to about 150 ms from key press to sound in the worst case (D-025). Fine for the piano; a rhythm game would want less.
- **No key remapping** (optional in CLAUDE.md), and no gamepad support.
- **Not verified by ear**, as described above.
- **Headless runs can't press keys yet:** there's no `--keys` option, so piano tests drive `InputDevice` from Java.
- **Next, M8:** the ROM, written in TRIADA assembly: a font, the kernel routines (PUTCHAR, PRINT …), the boot chime (now that there is sound), the boot screen and the monitor with its commands.

## New words
All of these are also in [GLOSSARY.md](../GLOSSARY.md).
- **Sample / Sample rate:** one number giving the speaker's position / how many of them per second (44,100).
- **Frequency / Hertz (Hz):** how many times per second a pattern repeats.
- **Waveform:** the shape of the repeating pattern: square, triangle, noise.
- **Ternary square:** TRIADA's three-level square wave: +1, 0, −1, 0.
- **Noise:** a random pattern; here random trits.
- **Voice:** one independent sound generator; TRIADA has three.
- **Mixing / Clipping:** adding voices together / distortion when the sum exceeds the allowed range.
- **Phase:** where in its repeating pattern a wave currently is.
- **Envelope / Fade:** how a note's volume changes over time / getting quieter until silent.
- **PCM / WAV:** sound stored as a plain list of samples / a common file format for it.
- **Audio thread:** a separate worker that does nothing but feed the sound card.
- **Thread / Thread-safe:** an independent line of work inside a program / safe to use from several threads at once.
- **Latency:** the delay between cause (a key press) and effect (the sound).
- **Underrun / Overrun:** the sound card running out of samples / too many samples waiting.
- **Input focus:** which window currently receives the keyboard.
