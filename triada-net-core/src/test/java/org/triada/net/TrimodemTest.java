package org.triada.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import org.triada.core.TriImage;
import org.triada.core.ternary.Word;

/** TRIMODEM (NET-SPEC §3): the base-81 encoding, the checksum and whole transfers. */
class TrimodemTest {

    @Test
    void everyWordIsThreeCharactersAndBack() {
        StringBuilder b = new StringBuilder();
        for (int w = Word.MIN; w <= Word.MAX; w++) {
            b.setLength(0);
            Trimodem.encode(w, b);
            assertEquals(3, b.length());
            for (int i = 0; i < 3; i++) {
                assertTrue(Trimodem.isDigit(b.charAt(i)));
            }
            assertEquals(w, Trimodem.decode(b, 0));
        }
    }

    @Test
    void theWorkedExamples() {
        assertEquals("IUe", Trimodem.encode(1_000));               // NET-SPEC §3.1
        assertEquals("!!!", Trimodem.encode(Word.MIN));
        assertEquals("qqq", Trimodem.encode(Word.MAX));
        assertEquals("III", Trimodem.encode(0));                   // 265,720 = 40·6561 + 40·81 + 40; 33 + 40 = 73, "I"
        assertThrows(IllegalArgumentException.class, () -> Trimodem.decode("A A", 0));
    }

    @Test
    void theChecksumWeighsEachWordByItsPlace() {
        int[] words = new int[81];
        words[0] = 5;
        words[1] = 7;
        // block 2: 2 + 1·5 + 2·7 = 21
        assertEquals(21, Trimodem.checksum(2, words, 0));
        words[0] = 7;
        words[1] = 5;                                               // swapped: 2 + 7 + 10 = 19
        assertEquals(19, Trimodem.checksum(2, words, 0));
        java.util.Arrays.fill(words, Word.MAX);                     // the sum wraps like a TRIADA sum
        assertEquals(Word.wrap(3_321L * Word.MAX), Trimodem.checksum(0, words, 0));
    }

    /** Runs sender and receiver against each other; {@code damage} may change a message on the way. */
    private static TrimodemReceiver transfer(TrimodemSender sender, java.util.function.UnaryOperator<byte[]> damage) {
        TrimodemReceiver receiver = new TrimodemReceiver();
        ArrayDeque<byte[]> toReceiver = new ArrayDeque<>();
        toReceiver.add(damage.apply(sender.start()));
        for (int steps = 0; steps < 10_000 && !toReceiver.isEmpty(); steps++) {
            for (byte b : toReceiver.poll()) {
                for (byte answer : receiver.receive(b & 0xFF)) {
                    byte[] next = sender.receive(answer);
                    if (next.length > 0) {
                        toReceiver.add(damage.apply(next));
                    }
                }
            }
        }
        return receiver;
    }

    private static TriImage program(int address, int length) {
        int[] w = new int[length];
        for (int i = 0; i < length; i++) {
            w[i] = (i * 7_919) % Word.MAX - 1_000;
        }
        return new TriImage(address, w);
    }

    @Test
    void aWholeFileArrivesIntact() {
        TriImage file = program(243, 200);                          // 3 blocks, the last one padded
        TrimodemSender sender = new TrimodemSender("game.tri", Trimodem.Type.TRI, file);
        assertEquals(3, sender.blocks());
        TrimodemReceiver receiver = transfer(sender, m -> m);
        assertTrue(sender.done());
        assertTrue(receiver.done());
        assertEquals("GAME.TRI", receiver.name());
        assertEquals(Trimodem.Type.TRI, receiver.type());
        assertEquals(243, receiver.file().loadAddress());
        assertArrayEquals(file.words(), receiver.file().words());
        assertEquals(3, receiver.blocksDone());
    }

    @Test
    void anEmptyFileAndAnAddressThatContainsASemicolon() {
        // an address whose middle digit is 26, i.e. character 59 = ';'
        int address = 26 * 81 - Word.MAX;                           // digits 0, 26, 0: "!;!"
        assertEquals("!;!", Trimodem.encode(address));
        TrimodemReceiver r = transfer(new TrimodemSender("E.TB", Trimodem.Type.TB, new TriImage(address, new int[0])),
                m -> m);
        assertTrue(r.done());
        assertEquals(0, r.file().size());
        assertEquals(address, r.file().loadAddress());
        assertEquals(Trimodem.Type.TB, r.type());
    }

    @Test
    void aDamagedBlockIsSentAgain() {
        int[] damaged = {0};
        TriImage file = program(243, 100);
        TrimodemSender sender = new TrimodemSender("A.TRI", Trimodem.Type.TRI, file);
        TrimodemReceiver r = transfer(sender, m -> {
            if (m.length > 100 && damaged[0] < 2) {                  // the first two block sends
                damaged[0]++;
                byte[] copy = m.clone();
                copy[50] = (byte) (copy[50] == 'A' ? 'B' : 'A');     // still a digit: only the checksum notices
                return copy;
            }
            return m;
        });
        assertTrue(r.done());
        assertArrayEquals(file.words(), r.file().words());
    }

    @Test
    void theThirdFailureAborts() {
        TrimodemSender sender = new TrimodemSender("A.TRI", Trimodem.Type.TRI, program(243, 100));
        TrimodemReceiver r = transfer(sender, m -> {
            if (m.length > 100) {
                byte[] copy = m.clone();
                copy[50] = (byte) (copy[50] == 'A' ? 'B' : 'A');
                return copy;
            }
            return m;
        });
        assertTrue(sender.failed());
        assertTrue(r.failed());
        assertFalse(r.done());
        assertThrows(IllegalStateException.class, r::file);
    }

    @Test
    void timeoutsCountAsFailures() {
        TrimodemSender sender = new TrimodemSender("A.TB", Trimodem.Type.TB, program(243, 10));
        byte[] first = sender.start();
        assertArrayEquals(first, sender.timeout(), "the same message again");
        assertArrayEquals(first, sender.timeout());
        assertArrayEquals(new byte[] {Trimodem.ESC, Trimodem.ABORT}, sender.timeout());
        assertTrue(sender.failed());
        assertEquals(0, sender.timeout().length);
    }

    @Test
    void aLostAckMeansARepeatThatIsAcknowledgedAgain() {
        TriImage file = program(243, 10);
        TrimodemSender sender = new TrimodemSender("A.TB", Trimodem.Type.TB, file);
        TrimodemReceiver receiver = new TrimodemReceiver();
        byte[] header = sender.start();
        assertArrayEquals(new byte[] {Trimodem.ACK}, feed(receiver, header));
        assertArrayEquals(new byte[] {Trimodem.ACK}, feed(receiver, header), "the start again");
        byte[] block = sender.receive(Trimodem.ACK);
        assertArrayEquals(new byte[] {Trimodem.ACK}, feed(receiver, block));
        assertArrayEquals(new byte[] {Trimodem.ACK}, feed(receiver, block), "block 0 again");
        assertEquals(1, receiver.blocksDone());
        byte[] end = sender.receive(Trimodem.ACK);
        assertArrayEquals(new byte[] {Trimodem.ESC, Trimodem.END}, end);
        assertArrayEquals(new byte[] {Trimodem.ACK}, feed(receiver, end));
        assertEquals(0, sender.receive(Trimodem.ACK).length);
        assertTrue(sender.done());
        assertArrayEquals(file.words(), receiver.file().words());
    }

    @Test
    void brokenStartsAndOrderAreRefused() {
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(new TrimodemReceiver(), ascii("\u001bFBAD NAME;!!!;TB;!!!")));
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(new TrimodemReceiver(), ascii("\u001bFA.TB;!!!;XY;!!!")));
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(new TrimodemReceiver(), ascii("\u001bFA.TB;!!!:TB;!!!")));
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(new TrimodemReceiver(), ascii("\u001bE")), "an end first");
        TrimodemReceiver r = new TrimodemReceiver();
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(r, ascii("\u001bB" + "!".repeat(249))), "a block first");
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(r, ascii("\u001bB!!\u0001")), "a bad character");
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(r, ascii("\u001bE")));
        assertTrue(r.failed(), "three failures in a row");
        TrimodemReceiver tooBig = new TrimodemReceiver();
        assertArrayEquals(new byte[] {Trimodem.NAK}, feed(tooBig, ascii("\u001bFA.TRI;" + Trimodem.encode(59_050)
                + ";TRI;" + Trimodem.encode(0))));
        TrimodemReceiver aborted = new TrimodemReceiver();
        feed(aborted, ascii("\u001bX"));
        assertTrue(aborted.failed());
        assertThrows(IllegalArgumentException.class,
                () -> new TrimodemSender("NO SPACES", Trimodem.Type.TB, program(243, 1)));
    }

    @Test
    void noiseBeforeAMessageIsIgnored() {
        TrimodemReceiver r = new TrimodemReceiver();
        assertEquals(0, feed(r, ascii("hello\u001b\u001b")).length);
        assertArrayEquals(new byte[] {Trimodem.ACK}, feed(r, ascii("FA.TB;III;TB;III")));
    }

    private static byte[] feed(TrimodemReceiver r, byte[] bytes) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte b : bytes) {
            out.writeBytes(r.receive(b & 0xFF));
        }
        return out.toByteArray();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
