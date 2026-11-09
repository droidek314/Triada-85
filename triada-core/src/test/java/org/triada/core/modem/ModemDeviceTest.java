package org.triada.core.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;

/** The modem device of SPEC §5.2 (M17), driven through its registers, with a scripted line behind it. */
class ModemDeviceTest {

    private static final int IO = MemoryMap.IO_BASE;
    private static final int NUMBER_AT = 1_000;

    /** A line whose answers the test decides. */
    static final class FakePort implements ModemPort {
        String dialed;
        int answer = ModemDevice.CONNECTED;
        boolean hungUp;
        final ArrayDeque<Integer> incoming = new ArrayDeque<>();
        final List<Integer> sent = new ArrayList<>();

        @Override
        public void dial(String number) {
            dialed = number;
            hungUp = false;
        }

        @Override
        public int status() {
            return answer;
        }

        @Override
        public int read() {
            Integer c = incoming.poll();
            return c == null ? -1 : c;
        }

        @Override
        public void write(int c) {
            sent.add(c);
        }

        @Override
        public void hangUp() {
            hungUp = true;
        }

        void receive(String s) {
            for (char c : s.toCharArray()) {
                incoming.add((int) c);
            }
        }
    }

    private final Machine machine = new Machine();
    private final FakePort port = new FakePort();

    ModemDeviceTest() {
        machine.modem().setPort(port);
    }

    private int reg(int k) {
        return machine.bus().read(IO + k);
    }

    private void set(int k, int value) {
        machine.bus().write(IO + k, value);
    }

    /** Puts {@code number} into RAM and writes DIAL. */
    private void dial(String number) {
        for (int i = 0; i < number.length(); i++) {
            machine.bus().write(NUMBER_AT + i, number.charAt(i));
        }
        machine.bus().write(NUMBER_AT + number.length(), 0);
        set(IoRegisters.MDM_ADDR, NUMBER_AT);
        set(IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
    }

    private void frames(int n) {
        for (int i = 0; i < n; i++) {
            machine.modem().endFrame();
        }
    }

    private void connect() {
        dial("1-11-11");
        frames(121);
        assertEquals(ModemDevice.CONNECTED, reg(IoRegisters.MDM_STATUS));
    }

    @Test
    void powerOnState() {
        assertEquals(ModemDevice.IDLE, reg(IoRegisters.MDM_STATUS));
        assertEquals(1_200, reg(IoRegisters.MDM_SPEED));
        assertEquals(729, reg(IoRegisters.MDM_TXFREE));
        assertEquals(0, reg(IoRegisters.MDM_RXCOUNT));
        assertEquals(0, reg(IoRegisters.MDM_RX), "an empty buffer reads 0");
        assertEquals(0, reg(IoRegisters.MDM_CMD), "MDM_CMD is write-only");
    }

    @Test
    void dialingTakesAPauseThenConnects() {
        dial("1-11-11");
        assertEquals("1-11-11", port.dialed);
        assertEquals(ModemDevice.DIALING, reg(IoRegisters.MDM_STATUS));
        frames(120);                    // one second plus a fifth per digit: 2 s
        assertEquals(ModemDevice.DIALING, reg(IoRegisters.MDM_STATUS));
        frames(1);
        assertEquals(ModemDevice.CONNECTED, reg(IoRegisters.MDM_STATUS));
    }

    @Test
    void theNetworkMayTakeLongerThanThePause() {
        port.answer = ModemDevice.DIALING;
        dial("1-11-11");
        frames(200);
        assertEquals(ModemDevice.DIALING, reg(IoRegisters.MDM_STATUS));
        port.answer = ModemDevice.CONNECTED;
        frames(1);
        assertEquals(ModemDevice.CONNECTED, reg(IoRegisters.MDM_STATUS));
    }

    @Test
    void everyFailureStatus() {
        for (int answer : new int[] {ModemDevice.NO_CARRIER, ModemDevice.BUSY, ModemDevice.NO_NUMBER,
            ModemDevice.OFFLINE}) {
            port.answer = answer;
            dial("2-22-22");
            frames(121);
            assertEquals(answer, reg(IoRegisters.MDM_STATUS));
        }
    }

    @Test
    void withoutALineTheNetworkIsOffline() {
        Machine m = new Machine();
        for (int i = 0; i < 3; i++) {
            m.bus().write(NUMBER_AT + i, "123".charAt(i));
        }
        m.bus().write(IO + IoRegisters.MDM_ADDR, NUMBER_AT);
        m.bus().write(IO + IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        for (int i = 0; i < 180; i++) {
            m.runFrame();               // the frames end through the machine this time
        }
        assertEquals(ModemDevice.OFFLINE, m.bus().read(IO + IoRegisters.MDM_STATUS));
    }

    @Test
    void aBrokenNumberIsNoSuchNumberWithoutDialing() {
        machine.bus().write(NUMBER_AT, 7);                  // a control character
        machine.bus().write(NUMBER_AT + 1, 0);
        set(IoRegisters.MDM_ADDR, NUMBER_AT);
        set(IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        frames(1);
        assertEquals(ModemDevice.NO_NUMBER, reg(IoRegisters.MDM_STATUS));
        assertEquals(null, port.dialed);
        for (int i = 0; i < 40; i++) {
            machine.bus().write(NUMBER_AT + i, '1');        // no 0 within 33 words
        }
        set(IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        frames(1);
        assertEquals(ModemDevice.NO_NUMBER, reg(IoRegisters.MDM_STATUS));
        machine.bus().write(NUMBER_AT, 0);                  // the empty string
        set(IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        frames(1);
        assertEquals(ModemDevice.NO_NUMBER, reg(IoRegisters.MDM_STATUS));
    }

    @Test
    void theDialPauseGrowsWithTheDigitsUpToThreeSeconds() {
        assertEquals(60, machine.modem().dialPause("X"));
        assertEquals(120, machine.modem().dialPause("0-00-00"));
        assertEquals(180, machine.modem().dialPause("1234567890123"));
    }

    @Test
    void twoCharactersPerFrameEachWay() {
        connect();
        for (char c : "HELLO".toCharArray()) {
            set(IoRegisters.MDM_TX, c);
        }
        assertEquals(724, reg(IoRegisters.MDM_TXFREE));
        port.receive("ABCDE");
        frames(1);
        assertEquals(List.of((int) 'H', (int) 'E'), port.sent);
        assertEquals(2, reg(IoRegisters.MDM_RXCOUNT));
        frames(2);
        assertEquals(5, port.sent.size());
        assertEquals(5, reg(IoRegisters.MDM_RXCOUNT));
        assertEquals(729, reg(IoRegisters.MDM_TXFREE));
        assertEquals('A', machine.bus().peek(IO + IoRegisters.MDM_RX), "peeking (the debugger) ...");
        assertEquals(5, reg(IoRegisters.MDM_RXCOUNT), "... takes nothing");
        StringBuilder got = new StringBuilder();
        while (reg(IoRegisters.MDM_RXCOUNT) > 0) {
            got.append((char) reg(IoRegisters.MDM_RX));     // reading removes it
        }
        assertEquals("ABCDE", got.toString());
    }

    @Test
    void charactersAreTakenModulo256() {
        connect();
        set(IoRegisters.MDM_TX, 300);
        set(IoRegisters.MDM_TX, -1);
        frames(1);
        assertEquals(List.of(44, 255), port.sent);
    }

    @Test
    void nothingIsSentWhenNotConnected() {
        set(IoRegisters.MDM_TX, 'X');
        assertEquals(729, reg(IoRegisters.MDM_TXFREE));
        dial("1-11-11");
        set(IoRegisters.MDM_TX, 'X');
        assertEquals(729, reg(IoRegisters.MDM_TXFREE), "not while dialing either");
    }

    @Test
    void aFullSendBufferTakesNoMore() {
        connect();
        for (int i = 0; i < 800; i++) {
            set(IoRegisters.MDM_TX, 'A');
        }
        assertEquals(0, reg(IoRegisters.MDM_TXFREE));
        frames(1);
        assertEquals(2, reg(IoRegisters.MDM_TXFREE));
    }

    @Test
    void aFullReceiveBufferDropsAndCounts() {
        machine.modem().setTurbo(true);
        dial("1-11-11");
        frames(1);                                          // turbo: no pause
        assertEquals(ModemDevice.CONNECTED, reg(IoRegisters.MDM_STATUS));
        port.receive("X".repeat(1_000));
        frames(2);
        assertEquals(729, reg(IoRegisters.MDM_RXCOUNT));
        assertEquals(271, machine.modem().droppedCharacters());
    }

    @Test
    void hangingUpFromTheMachine() {
        connect();
        port.receive("AB");
        frames(1);
        set(IoRegisters.MDM_TX, 'Z');
        set(IoRegisters.MDM_CMD, ModemDevice.CMD_HANG_UP);
        assertTrue(port.hungUp);
        assertEquals(ModemDevice.NO_CARRIER, reg(IoRegisters.MDM_STATUS));
        assertEquals(729, reg(IoRegisters.MDM_TXFREE), "unsent characters are lost");
        assertEquals(2, reg(IoRegisters.MDM_RXCOUNT), "received ones can still be read");
        frames(1);
        assertTrue(port.sent.isEmpty());
    }

    @Test
    void hangingUpFromTheOtherSideAfterItsLastWords() {
        connect();
        port.receive("BYE");
        port.answer = ModemDevice.NO_CARRIER;               // ... and it hung up
        frames(1);
        assertEquals(ModemDevice.CONNECTED, reg(IoRegisters.MDM_STATUS), "BYE is still arriving");
        frames(1);
        assertEquals(ModemDevice.NO_CARRIER, reg(IoRegisters.MDM_STATUS));
        assertEquals(3, reg(IoRegisters.MDM_RXCOUNT));
    }

    @Test
    void powerOnHangsUp() {
        connect();
        machine.powerOn();
        assertTrue(port.hungUp);
        assertEquals(ModemDevice.IDLE, reg(IoRegisters.MDM_STATUS));
        assertFalse(machine.modem().turbo());
    }

    @Test
    void addrIsReadBackAndUnknownCommandsDoNothing() {
        set(IoRegisters.MDM_ADDR, 4_321);
        assertEquals(4_321, reg(IoRegisters.MDM_ADDR));
        set(IoRegisters.MDM_CMD, 3);
        assertEquals(ModemDevice.IDLE, reg(IoRegisters.MDM_STATUS));
    }
}
