package org.triada.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.triada.core.sound.SoundChip;

/**
 * Collects the sound chip's samples frame by frame and saves them as a WAV
 * file: 16-bit signed mono PCM at 44,100 Hz, which any audio player opens.
 *
 * <p>A WAV file is a 44-byte header followed by the raw samples, each 2 bytes,
 * least significant byte first:
 * <pre>
 *   "RIFF" size "WAVE"                       container
 *   "fmt " 16 PCM(1) channels rate byte-rate block-align bits
 *   "data" size  samples…
 * </pre>
 */
final class WavRecorder {

    private final ByteArrayOutputStream samples = new ByteArrayOutputStream();

    /** Appends one frame's samples. */
    void add(short[] frame) {
        for (short s : frame) {
            samples.write(s & 0xFF);
            samples.write((s >> 8) & 0xFF);
        }
    }

    /** Number of samples recorded so far. */
    int sampleCount() {
        return samples.size() / 2;
    }

    void write(Path file) throws IOException {
        byte[] data = samples.toByteArray();
        int rate = SoundChip.SAMPLE_RATE;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + data.length);
        ascii(out, "RIFF");
        int32(out, 36 + data.length);       // size of everything after this field
        ascii(out, "WAVE");
        ascii(out, "fmt ");
        int32(out, 16);                     // size of the format block
        int16(out, 1);                      // 1 = plain PCM
        int16(out, 1);                      // mono
        int32(out, rate);                   // samples per second
        int32(out, rate * 2);               // bytes per second
        int16(out, 2);                      // bytes per sample frame
        int16(out, 16);                     // bits per sample
        ascii(out, "data");
        int32(out, data.length);
        out.write(data);
        Files.write(file, out.toByteArray());
    }

    private static void ascii(ByteArrayOutputStream out, String s) {
        for (char c : s.toCharArray()) {
            out.write(c);
        }
    }

    private static void int16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void int32(ByteArrayOutputStream out, int v) {
        int16(out, v & 0xFFFF);
        int16(out, (v >>> 16) & 0xFFFF);
    }
}
