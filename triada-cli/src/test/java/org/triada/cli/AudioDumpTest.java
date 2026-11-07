package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code triada run … --dump-audio out.wav}. */
class AudioDumpTest {

    @TempDir
    Path dir;

    private Path dump(String source, int frames) throws Exception {
        String src = Cli.write(dir, "snd.tasm", source);
        Path wav = dir.resolve("out.wav");
        Cli.Result r = Cli.run("run", src, "--frames", Integer.toString(frames), "--dump-audio", wav.toString(),
                "--no-boot");
        assertEquals(0, r.code(), r.toString());
        return wav;
    }

    private static final String TONE = """
                    LDI R1, IO_BASE
                    LDI R0, 441
                    ST R0, [R1+V1_FREQ]
                    LDI R0, 13
                    ST R0, [R1+V1_VOL]
                    LDI R0, 1
                    ST R0, [R1+V1_WAVE]     ; ternary square
            loop:   WAIT
                    JMP loop
            """;

    @Test
    void wavHeaderIsStandardPcm() throws Exception {
        byte[] b = Files.readAllBytes(dump(TONE, 10));
        ByteBuffer h = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("RIFF", new String(b, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(b.length - 8, h.getInt(4));
        assertEquals("WAVEfmt ", new String(b, 8, 8, StandardCharsets.US_ASCII));
        assertEquals(1, h.getShort(20), "PCM");
        assertEquals(1, h.getShort(22), "mono");
        assertEquals(44_100, h.getInt(24));
        assertEquals(16, h.getShort(34), "bits");
        assertEquals("data", new String(b, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(10 * 735 * 2, h.getInt(40), "10 frames x 735 samples x 2 bytes");
        assertEquals(44 + 10 * 735 * 2, b.length);
    }

    @Test
    void javaSoundCanReadIt() throws Exception {
        File wav = dump(TONE, 6).toFile();
        AudioFormat f;
        try (var stream = AudioSystem.getAudioInputStream(wav)) {   // close it, or Windows keeps the file locked
            f = stream.getFormat();
        }
        assertEquals(44_100f, f.getSampleRate());
        assertEquals(16, f.getSampleSizeInBits());
        assertEquals(1, f.getChannels());
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, f.getEncoding());
    }

    @Test
    void theSamplesAreTheChipsOutput() throws Exception {
        byte[] b = Files.readAllBytes(dump(TONE, 3));
        ByteBuffer h = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        // Frame 1 plays the registers set during frame 1: the ternary square at
        // 441 Hz, 25 samples +10000, 25 samples 0, 25 samples -10000, 25 samples 0.
        assertEquals(10_000, h.getShort(44));
        assertEquals(10_000, h.getShort(44 + 2 * 24));
        assertEquals(0, h.getShort(44 + 2 * 25));
        assertEquals(-10_000, h.getShort(44 + 2 * 50));
        assertEquals(0, h.getShort(44 + 2 * 99));
        assertEquals(10_000, h.getShort(44 + 2 * 100));
    }

    @Test
    void unwritableAudioPathIsReported() throws Exception {
        String src = Cli.write(dir, "s.tasm", "HLT\n");
        Cli.Result r = Cli.run("run", src, "--dump-audio", dir.resolve("no/such/dir/x.wav").toString());
        assertEquals(2, r.code());
        assertTrue(r.err().contains("can't write"), r.err());
    }
}
