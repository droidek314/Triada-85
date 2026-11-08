package org.triada.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyFeederTest {

    private final InputDevice input = new InputDevice();

    private String drain() {
        StringBuilder b = new StringBuilder();
        for (int k = input.read(IoRegisters.KEY); k != 0; k = input.read(IoRegisters.KEY)) {
            b.append((char) k);
        }
        return b.toString();
    }

    @Test
    void typesWhatTheQueueTakesAndGoesOnNextTime() {
        String text = "10 PRINT \"A LONG LINE THAT DOES NOT FIT\"\r\n20 END\n";
        KeyFeeder f = new KeyFeeder(text);
        StringBuilder typed = new StringBuilder();
        int calls = 0;
        boolean more = true;
        while (more) {
            more = f.feed(input);
            assertTrue(input.queuedKeys() <= InputDevice.KEY_QUEUE_CAPACITY);
            typed.append(drain());          // the machine reads them during its frame
            calls++;
        }
        assertEquals(text.replace("\r", ""), typed.toString(), "\\r\\n is one Enter");
        assertEquals(2, calls, "27 keys per call");
        assertTrue(f.done());
    }

    @Test
    void charactersTheFontLacksAreLeftOut() {
        KeyFeeder f = new KeyFeeder("é\tx");
        assertFalse(f.feed(input));
        assertEquals("\tx", drain());
    }
}
