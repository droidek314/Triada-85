package org.triada.core.io;

import org.triada.core.ternary.Trit;
import org.triada.core.ternary.Word;

/**
 * The joystick, the three fire buttons and the keyboard queue: I/O registers
 * JOY_X … KEY (k = 1 … 6, SPEC §5). All are read-only for the CPU; writes are
 * ignored.
 *
 * <p>The frontend (M7) calls the {@code set…} and {@link #pushKey(int)} methods
 * when the person presses things. The CPU reads the registers through the bus.
 *
 * <p><b>The KEY queue.</b> Keys wait in a first-in, first-out queue. Reading
 * KEY returns the oldest key and removes it, or returns 0 if the queue is
 * empty. The queue holds {@link #KEY_QUEUE_CAPACITY} keys. When it is full,
 * new keys are dropped, the way a real keyboard buffer loses keystrokes
 * typed faster than the program reads them (DECISIONS.md D-007).
 *
 * <p>Not thread-safe, like everything in triada-core: the frontend must call
 * it from the same thread that runs the CPU.
 */
public final class InputDevice implements IoDevice {

    /** First register this device occupies. */
    public static final int FIRST_REGISTER = IoRegisters.JOY_X;
    /** Number of registers it occupies (JOY_X, JOY_Y, BTN_A, BTN_B, BTN_C, KEY). */
    public static final int REGISTER_COUNT = 6;
    /** How many keys the queue holds before it drops new ones. */
    public static final int KEY_QUEUE_CAPACITY = 27;

    private int joyX;
    private int joyY;
    private boolean buttonA;
    private boolean buttonB;
    private boolean buttonC;

    // A ring buffer: a fixed array where "head" chases "tail" round and round,
    // so adding and removing keys never allocates.
    private final int[] keys = new int[KEY_QUEUE_CAPACITY];
    private int head;    // index of the oldest key
    private int size;    // number of keys waiting

    // ---------------------------------------------------------------- frontend side

    /** Joystick left/right: −1 left, 0 centre, +1 right. */
    public void setJoyX(int value) {
        joyX = checkTrit("JOY_X", value);
    }

    /** Joystick up/down: −1 up, 0 centre, +1 down. */
    public void setJoyY(int value) {
        joyY = checkTrit("JOY_Y", value);
    }

    public void setButtonA(boolean pressed) {
        buttonA = pressed;
    }

    public void setButtonB(boolean pressed) {
        buttonB = pressed;
    }

    public void setButtonC(boolean pressed) {
        buttonC = pressed;
    }

    /**
     * Adds a key (its ASCII code) to the end of the queue.
     *
     * @return {@code true} if it was queued, {@code false} if the queue was full
     *     and the key was dropped
     * @throws IllegalArgumentException for 0 (which means "no key") or a value
     *     that is not a word
     */
    public boolean pushKey(int code) {
        if (code == 0 || !Word.isValid(code)) {
            throw new IllegalArgumentException("key code must be a non-zero word, was " + code);
        }
        if (size == KEY_QUEUE_CAPACITY) {
            return false;
        }
        keys[(head + size) % KEY_QUEUE_CAPACITY] = code;
        size++;
        return true;
    }

    /** Number of keys waiting in the queue. */
    public int queuedKeys() {
        return size;
    }

    /** Empties the key queue, e.g. on reset. */
    public void clearKeys() {
        head = 0;
        size = 0;
    }

    // ---------------------------------------------------------------- CPU side

    @Override
    public int read(int k) {
        if (k == IoRegisters.KEY) {
            if (size == 0) {
                return 0;
            }
            int code = keys[head];
            head = (head + 1) % KEY_QUEUE_CAPACITY;
            size--;
            return code;
        }
        return peek(k);
    }

    @Override
    public int peek(int k) {
        switch (k) {
            case IoRegisters.JOY_X:
                return joyX;
            case IoRegisters.JOY_Y:
                return joyY;
            case IoRegisters.BTN_A:
                return buttonA ? 1 : 0;
            case IoRegisters.BTN_B:
                return buttonB ? 1 : 0;
            case IoRegisters.BTN_C:
                return buttonC ? 1 : 0;
            case IoRegisters.KEY:
                return size == 0 ? 0 : keys[head];
            default:
                return 0;
        }
    }

    @Override
    public void write(int k, int value) {
        // All input registers are read-only (SPEC §5); writes are ignored.
    }

    private static int checkTrit(String name, int value) {
        if (!Trit.isValid(value)) {
            throw new IllegalArgumentException(name + " must be -1, 0 or +1, was " + value);
        }
        return value;
    }
}
