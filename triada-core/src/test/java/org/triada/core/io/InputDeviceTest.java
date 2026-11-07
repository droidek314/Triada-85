package org.triada.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.triada.core.memory.Bus;
import org.triada.core.memory.MemoryMap;

/** Tests the input device through the bus, using the addresses from SPEC §5. */
class InputDeviceTest {

    private static final int JOY_X = -20_411;
    private static final int JOY_Y = -20_410;
    private static final int BTN_A = -20_409;
    private static final int BTN_B = -20_408;
    private static final int BTN_C = -20_407;
    private static final int KEY = -20_406;

    private Bus bus;
    private InputDevice input;

    @BeforeEach
    void plugIn() {
        bus = new Bus();
        input = new InputDevice();
        bus.attach(InputDevice.FIRST_REGISTER, InputDevice.REGISTER_COUNT, input);
    }

    @Test
    void occupiesExactlyJoyXToKey() {
        assertNull(bus.deviceAt(IoRegisters.FRAME));
        assertEquals(input, bus.deviceAt(IoRegisters.JOY_X));
        assertEquals(input, bus.deviceAt(IoRegisters.KEY));
        assertNull(bus.deviceAt(IoRegisters.SCROLL_X));
    }

    @Test
    void everythingIsZeroAtPowerOn() {
        for (int a = JOY_X; a <= KEY; a++) {
            assertEquals(0, bus.read(a), "address " + a);
        }
    }

    @Test
    void joystickAndButtons() {
        input.setJoyX(-1);
        input.setJoyY(1);
        input.setButtonA(true);
        input.setButtonC(true);
        assertEquals(-1, bus.read(JOY_X));
        assertEquals(1, bus.read(JOY_Y));
        assertEquals(1, bus.read(BTN_A));
        assertEquals(0, bus.read(BTN_B));
        assertEquals(1, bus.read(BTN_C));
        input.setButtonA(false);
        input.setButtonB(true);
        assertEquals(0, bus.read(BTN_A));
        assertEquals(1, bus.read(BTN_B));
        assertThrows(IllegalArgumentException.class, () -> input.setJoyX(2));
        assertThrows(IllegalArgumentException.class, () -> input.setJoyY(-2));
    }

    @Test
    void readingKeyRemovesItOldestFirst() {
        input.pushKey('H');
        input.pushKey('I');
        assertEquals('H', bus.read(KEY));
        assertEquals('I', bus.read(KEY));
        assertEquals(0, bus.read(KEY), "empty queue reads 0");
        assertEquals(0, bus.read(KEY));
    }

    @Test
    void peekingKeyLeavesItInTheQueue() {
        input.pushKey('A');
        assertEquals('A', bus.peek(KEY));
        assertEquals('A', bus.peek(KEY));
        assertEquals(1, input.queuedKeys());
        assertEquals('A', bus.read(KEY));
        assertEquals(0, bus.peek(KEY));
    }

    @Test
    void readingOtherRegistersDoesNotTouchTheQueue() {
        input.pushKey('Q');
        bus.read(JOY_X);
        bus.read(BTN_C);
        assertEquals(1, input.queuedKeys());
    }

    @Test
    void fullQueueDropsNewKeys() {
        for (int i = 1; i <= InputDevice.KEY_QUEUE_CAPACITY; i++) {
            assertTrue(input.pushKey(i), "key " + i);
        }
        assertFalse(input.pushKey(999), "the 28th key is dropped");
        for (int i = 1; i <= InputDevice.KEY_QUEUE_CAPACITY; i++) {
            assertEquals(i, bus.read(KEY));
        }
        assertEquals(0, bus.read(KEY));
    }

    @Test
    void ringBufferWrapsAroundManyTimes() {
        // Keep the queue half full while 1000 keys pass through it.
        int next = 1;
        int expected = 1;
        for (int i = 0; i < 13; i++) {
            input.pushKey(next++);
        }
        for (int i = 0; i < 1000; i++) {
            input.pushKey(next++);
            assertEquals(expected++, bus.read(KEY));
        }
        assertEquals(13, input.queuedKeys());
    }

    @Test
    void clearKeysEmptiesTheQueue() {
        input.pushKey('X');
        input.clearKeys();
        assertEquals(0, bus.read(KEY));
    }

    @Test
    void writesAreIgnored() {
        input.setJoyX(1);
        input.pushKey('Z');
        bus.write(JOY_X, -1);
        bus.write(KEY, 'Y');
        assertEquals(1, bus.read(JOY_X));
        assertEquals('Z', bus.read(KEY));
        assertEquals(0, bus.read(KEY));
    }

    @Test
    void keyZeroAndNonWordsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> input.pushKey(0));
        assertThrows(IllegalArgumentException.class, () -> input.pushKey(300_000));
    }

    @Test
    void registerNumbersMatchSpecAddresses() {
        // SPEC §5 table: k = 0 … 22 at addresses −20,412 … −20,390.
        String[] names = {"FRAME", "JOY_X", "JOY_Y", "BTN_A", "BTN_B", "BTN_C", "KEY", "SCROLL_X",
            "SCROLL_Y", "BORDER", "V1_FREQ", "V1_VOL", "V1_WAVE", "V2_FREQ", "V2_VOL", "V2_WAVE",
            "V3_FREQ", "V3_VOL", "V3_WAVE", "RAND", "DEBUG_CHAR", "DEBUG_NUM", "HOST_EXIT"};
        for (int k = 0; k < names.length; k++) {
            assertEquals(names[k], IoRegisters.nameOf(k));
            assertEquals(-20_412 + k, MemoryMap.IO_BASE + k);
        }
        assertEquals(-20_390, MemoryMap.IO_BASE + IoRegisters.HOST_EXIT);
        assertEquals(IoRegisters.DEFINED_COUNT, names.length);
        assertNull(IoRegisters.nameOf(23));
        assertNull(IoRegisters.nameOf(-1));
        assertEquals(6, IoRegisters.KEY);
        assertEquals(22, IoRegisters.HOST_EXIT);
    }
}
