package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.assembler.Assembler;
import org.triada.core.Machine;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;
import org.triada.core.sound.SoundChip;

/**
 * {@code programs/piano.tasm} (CLAUDE.md M7: "keys play notes, and the
 * joystick changes the waveform"). The test plays it like a person would:
 * keys into the KEY queue, the joystick through the input device, one frame at
 * a time, and checks the sound registers, the samples and the screen.
 */
class PianoProgramTest {

    private Machine m;

    @BeforeEach
    void boot() throws Exception {
        m = new Machine();
        String path = Cli.root().resolve("programs/piano.tasm").toString();
        m.loadProgram(new Assembler(new FileSourceLoader()).assemble(path).image());
        frames(2);                                   // draw the keyboard
    }

    private void frames(int n) {
        for (int i = 0; i < n; i++) {
            m.runFrame();
        }
        assertFalse(m.cpu().isHalted(), "the piano must keep running");
    }

    private int reg(int k) {
        return m.bus().read(MemoryMap.IO_BASE + k);
    }

    private void press(char key) {
        m.input().pushKey(key);
        frames(1);
    }

    private int borderRgb() {
        return m.video().frame()[0];
    }

    @Test
    void silentAndRedUntilAKeyIsPressed() {
        for (int v = 0; v < 3; v++) {
            assertEquals(0, reg(IoRegisters.V1_VOL + 3 * v));
        }
        for (short s : m.sound().frameSamples()) {
            assertEquals(0, s);
        }
        assertEquals(0xFF0000, borderRgb(), "default waveform: the ternary square");
    }

    @Test
    void theKeyboardIsDrawn() {
        // White key C at map column 1, row 7; black key C# at column 3; background above.
        assertEquals(13, m.video().screenPixel(8, 7 * 8));
        assertEquals(-13, m.video().screenPixel(3 * 8, 7 * 8));
        assertEquals(-12, m.video().screenPixel(8, 6 * 8));
        assertEquals(13, m.video().screenPixel(3 * 8, 12 * 8), "below the black keys: white again");
    }

    @Test
    void keysPlayNotesOnTheThreeVoicesInTurn() {
        press('a');                                  // C4 on voice 1
        assertEquals(262, reg(IoRegisters.V1_FREQ));
        assertEquals(13, reg(IoRegisters.V1_VOL));
        assertEquals(1, reg(IoRegisters.V1_WAVE));
        press('H');                                  // A4 on voice 2 (upper case works too)
        assertEquals(440, reg(IoRegisters.V2_FREQ));
        press('w');                                  // C#4 on voice 3
        assertEquals(277, reg(IoRegisters.V3_FREQ));
        press('k');                                  // C5: back to voice 1
        assertEquals(523, reg(IoRegisters.V1_FREQ));
        assertEquals(440, reg(IoRegisters.V2_FREQ), "voice 2 still rings");
    }

    @Test
    void severalKeysInOneFrameAreAllPlayed() {
        for (char c : "asd".toCharArray()) {
            m.input().pushKey(c);
        }
        frames(1);
        assertEquals(262, reg(IoRegisters.V1_FREQ));
        assertEquals(294, reg(IoRegisters.V2_FREQ));
        assertEquals(330, reg(IoRegisters.V3_FREQ));
        assertEquals(0, m.input().queuedKeys());
    }

    @Test
    void otherKeysAreIgnored() {
        press('q');
        press('1');
        assertEquals(0, reg(IoRegisters.V1_FREQ));
        assertEquals(0, reg(IoRegisters.V1_VOL));
    }

    @Test
    void notesFadeOutOverFiftyTwoFrames() {
        press('a');
        assertEquals(13, reg(IoRegisters.V1_VOL));
        frames(20);
        int mid = reg(IoRegisters.V1_VOL);
        assertTrue(mid > 0 && mid < 13, "fading: " + mid);
        frames(32);
        assertEquals(0, reg(IoRegisters.V1_VOL), "silent after 52 frames");
    }

    @Test
    void theJoystickChoosesTheWaveformOfNewNotes() {
        m.input().setJoyX(-1);                       // LEFT: noise
        frames(1);
        m.input().setJoyX(0);
        assertEquals(0xFFFFFF, borderRgb(), "white border = noise");
        press('a');
        assertEquals(-1, reg(IoRegisters.V1_WAVE));

        m.input().setJoyY(-1);                       // UP: triangle
        frames(1);
        m.input().setJoyY(0);
        assertEquals(0x0000FF, borderRgb(), "blue border = triangle");
        press('s');
        assertEquals(0, reg(IoRegisters.V2_WAVE));
        assertEquals(-1, reg(IoRegisters.V1_WAVE), "a note that is already playing keeps its waveform");

        m.input().setJoyX(1);                        // RIGHT: ternary square
        frames(1);
        assertEquals(0xFF0000, borderRgb());
        press('d');
        assertEquals(1, reg(IoRegisters.V3_WAVE));
    }

    @Test
    void aNoteActuallySoundsAtItsPitch() {
        // Strike A4 (440 Hz, ternary square) once and record 20 frames of sound:
        // the frame in which the key was read, and 19 more (the note is still fading).
        int n = 20 * SoundChip.SAMPLES_PER_FRAME;            // 14,700 samples = 1/3 s
        short[] sound = new short[n];
        press('h');
        for (int f = 0; f < 20; f++) {
            if (f > 0) {
                frames(1);
            }
            System.arraycopy(m.sound().frameSamples(), 0, sound, f * SoundChip.SAMPLES_PER_FRAME,
                    SoundChip.SAMPLES_PER_FRAME);
        }
        // The square goes +, 0, -, 0 each period: count the steps from 0 up to +.
        // Periods start at samples 0, 100.2, 200.5 ... (44,100 / 440 = 100.23 apart):
        // 147 of them fit into 14,700 samples, and every one but the first is a step up.
        int rises = 0;
        for (int i = 1; i < n; i++) {
            if (sound[i] > 0 && sound[i - 1] <= 0) {
                rises++;
            }
        }
        assertEquals(146, rises);
    }

    @Test
    void theMarkerPointsAtTheLastKey() {
        press('d');                                  // E4: white key 2, marker at x = 64
        int sprite = MemoryMap.SPRITES;
        assertEquals(64, m.bus().read(sprite));
        assertEquals(1, m.bus().read(sprite + 3), "visible");
        press('t');                                  // F#4: black key, x = 100
        assertEquals(100, m.bus().read(sprite));
    }

    /** A golden screenshot after playing E: keyboard, marker, red border. */
    @Test
    void goldenScreenAfterPlayingE() throws Exception {
        press('d');
        java.awt.image.BufferedImage actual = ScreenDump.toImage(m.video().frame());
        File golden = Cli.root().resolve("triada-cli/src/test/resources/golden/piano-e.png").toFile();
        if (Boolean.getBoolean("triada.updateGolden")) {
            golden.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(actual, "png", golden);
            return;
        }
        assertTrue(Files.exists(golden.toPath()), "missing " + golden);
        java.awt.image.BufferedImage expected = javax.imageio.ImageIO.read(golden);
        for (int y = 0; y < 168; y++) {
            for (int x = 0; x < 240; x++) {
                assertEquals(expected.getRGB(x, y) & 0xFFFFFF, actual.getRGB(x, y) & 0xFFFFFF,
                        "piano golden image differs at (" + x + ", " + y + ")");
            }
        }
    }
}
