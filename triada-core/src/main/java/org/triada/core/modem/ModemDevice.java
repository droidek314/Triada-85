package org.triada.core.modem;

import org.triada.core.MachineProfile;
import org.triada.core.io.IoDevice;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.Bus;

/**
 * The modem (SPEC §5.2, M17), on I/O registers 28–35. A program puts the
 * address of a phone number into MDM_ADDR and writes 1 (DIAL) into MDM_CMD.
 * After a dialing pause MDM_STATUS says how the call went; once it is 2
 * (connected), characters written to MDM_TX travel down the line and the
 * other side's characters arrive in MDM_RX.
 *
 * <p><b>Speed is simulated.</b> The line runs at the profile's bit rate
 * (1200 bit/s; ten bits carry one character), so at the end of every frame
 * {@link #endFrame()} moves at most two characters each way between the
 * buffers and the {@link ModemPort}. Each buffer holds 729 characters;
 * characters arriving at a full receive buffer are dropped and counted
 * ({@link #droppedCharacters()}).
 *
 * <p>Like the triskette drive, the modem never opens a connection itself: the
 * host plugs in a port with {@link #setPort}. Without one, every call ends
 * "network offline". The buffers are plain arrays used as rings, so a frame
 * allocates nothing.
 */
public final class ModemDevice implements IoDevice {

    public static final int FIRST_REGISTER = IoRegisters.MDM_CMD;
    public static final int REGISTER_COUNT = 8;

    /** MDM_CMD values. */
    public static final int CMD_DIAL = 1;
    public static final int CMD_HANG_UP = 2;

    /** MDM_STATUS values. */
    public static final int IDLE = 0;
    public static final int DIALING = 1;
    public static final int CONNECTED = 2;
    public static final int NO_CARRIER = -1;
    public static final int BUSY = -2;
    public static final int NO_NUMBER = -3;
    public static final int OFFLINE = -4;

    /** Characters each buffer holds (3^6). */
    public static final int BUFFER_SIZE = 729;
    /** Longest phone number read from RAM; a longer one is "no such number". */
    public static final int MAX_NUMBER_LENGTH = 32;

    private final Bus bus;
    private final int bitsPerSecond;
    private final int charsPerFrame;
    private final int framesPerSecond;

    private ModemPort port = ModemPort.OFFLINE;
    private boolean turbo;

    private int addr;
    private int status;
    /** Frames left in the dialing pause. */
    private int dialFrames;
    /** The number dialed wasn't a valid string: the call ends "no such number". */
    private boolean badNumber;

    private final int[] rx = new int[BUFFER_SIZE];
    private int rxHead;
    private int rxCount;
    private final int[] tx = new int[BUFFER_SIZE];
    private int txHead;
    private int txCount;
    private long dropped;

    /** A modem whose speed comes from the bus's machine profile. */
    public ModemDevice(Bus bus) {
        this.bus = bus;
        MachineProfile p = bus.profile();
        bitsPerSecond = p.modemBitsPerSecond();
        charsPerFrame = p.modemCharsPerFrame();
        framesPerSecond = p.framesPerSecond();
    }

    // ---------------------------------------------------------------- host side

    /** Plugs the modem into a line. */
    public void setPort(ModemPort port) {
        this.port = port;
    }

    public ModemPort port() {
        return port;
    }

    /** Turbo: no dialing pause and no speed limit (SPEC §5.2: off by default). */
    public void setTurbo(boolean on) {
        turbo = on;
    }

    public boolean turbo() {
        return turbo;
    }

    /** Received characters dropped because the receive buffer was full, since power-on. */
    public long droppedCharacters() {
        return dropped;
    }

    /** Power-on: any call is hung up, the registers and buffers are cleared. */
    public void reset() {
        port.hangUp();
        addr = 0;
        status = IDLE;
        dialFrames = 0;
        badNumber = false;
        clearBuffers();
        dropped = 0;
    }

    // ---------------------------------------------------------------- CPU side

    @Override
    public int read(int k) {
        switch (k) {
            case IoRegisters.MDM_ADDR:
                return addr;
            case IoRegisters.MDM_STATUS:
                return status;
            case IoRegisters.MDM_RX:
                return takeReceived();
            case IoRegisters.MDM_RXCOUNT:
                return rxCount;
            case IoRegisters.MDM_TXFREE:
                return BUFFER_SIZE - txCount;
            case IoRegisters.MDM_SPEED:
                return bitsPerSecond;
            default:
                return 0;           // MDM_CMD and MDM_TX are write-only
        }
    }

    /** Like {@link #read(int)}, but MDM_RX shows the next character and leaves it waiting (for the debugger). */
    @Override
    public int peek(int k) {
        if (k == IoRegisters.MDM_RX) {
            return rxCount == 0 ? 0 : rx[rxHead];
        }
        return read(k);
    }

    @Override
    public void write(int k, int value) {
        switch (k) {
            case IoRegisters.MDM_CMD:
                if (value == CMD_DIAL) {
                    dial();
                } else if (value == CMD_HANG_UP) {
                    hangUp();
                }
                break;              // other values do nothing
            case IoRegisters.MDM_ADDR:
                addr = value;
                break;
            case IoRegisters.MDM_TX:
                if (status == CONNECTED && txCount < BUFFER_SIZE) {
                    tx[(txHead + txCount) % BUFFER_SIZE] = Math.floorMod(value, 256);
                    txCount++;
                }
                break;
            default:
                break;              // the rest are read-only
        }
    }

    private int takeReceived() {
        if (rxCount == 0) {
            return 0;               // "undefined" in SPEC §5.2; we say 0
        }
        int c = rx[rxHead];
        rxHead = (rxHead + 1) % BUFFER_SIZE;
        rxCount--;
        return c;
    }

    private void dial() {
        port.hangUp();
        clearBuffers();
        String number = number();
        badNumber = number == null;
        status = DIALING;
        dialFrames = turbo || badNumber ? 0 : dialPause(number);
        if (!badNumber) {
            port.dial(number);
        }
    }

    private void hangUp() {
        port.hangUp();
        txCount = 0;                // what wasn't sent is lost; what arrived can still be read
        status = NO_CARRIER;
    }

    /**
     * The dialing pause in frames: one second, plus a fifth of a second for
     * every digit the rotary dial has to turn through, at most three seconds.
     * "1-11-11" (five digits) takes two seconds.
     */
    int dialPause(String number) {
        int digits = 0;
        for (int i = 0; i < number.length(); i++) {
            if (number.charAt(i) >= '0' && number.charAt(i) <= '9') {
                digits++;
            }
        }
        return Math.min(3 * framesPerSecond, framesPerSecond + framesPerSecond / 5 * digits);
    }

    /** The 0-terminated number at MDM_ADDR, or null if it isn't a string of 1 … 32 printable characters. */
    private String number() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i <= MAX_NUMBER_LENGTH; i++) {
            long a = (long) addr + i;
            if (a < bus.profile().ramStart() || a > bus.profile().ramEnd()) {
                return null;
            }
            int c = bus.peek((int) a);
            if (c == 0) {
                return b.length() == 0 ? null : b.toString();
            }
            if (c < 32 || c > 126) {
                return null;
            }
            b.append((char) c);
        }
        return null;                // no 0 within 33 words: too long
    }

    // ---------------------------------------------------------------- once per frame

    /**
     * End of a frame (the machine calls this): the dialing pause counts down
     * and the call is answered, or up to two characters move each way.
     */
    public void endFrame() {
        if (status == DIALING) {
            if (dialFrames > 0) {
                dialFrames--;
                return;
            }
            if (badNumber) {
                status = NO_NUMBER;
                return;
            }
            int s = port.status();
            if (s != DIALING) {
                status = s == IDLE ? OFFLINE : s;
            }
            return;
        }
        if (status != CONNECTED) {
            return;
        }
        int budget = turbo ? BUFFER_SIZE : charsPerFrame;
        for (int i = 0; i < budget && txCount > 0; i++) {
            port.write(tx[txHead]);
            txHead = (txHead + 1) % BUFFER_SIZE;
            txCount--;
        }
        boolean lineEmpty = false;
        for (int i = 0; i < budget; i++) {
            int c = port.read();
            if (c < 0) {
                lineEmpty = true;
                break;
            }
            if (rxCount < BUFFER_SIZE) {
                rx[(rxHead + rxCount) % BUFFER_SIZE] = c;
                rxCount++;
            } else {
                dropped++;
            }
        }
        // The other side hung up: report it once everything it sent has arrived.
        if (lineEmpty && port.status() != CONNECTED) {
            status = NO_CARRIER;
            txCount = 0;
        }
    }

    private void clearBuffers() {
        rxHead = 0;
        rxCount = 0;
        txHead = 0;
        txCount = 0;
    }
}
