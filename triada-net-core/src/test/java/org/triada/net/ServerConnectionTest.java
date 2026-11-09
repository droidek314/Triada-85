package org.triada.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.triada.core.Machine;
import org.triada.core.MachineProfiles;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;
import org.triada.core.modem.ModemDevice;
import org.triada.core.modem.WireModemPort;

/** The server side of the wire protocol (NET-SPEC §2), and a machine calling the echo exchange through it. */
class ServerConnectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    /** A wire that records what the server sends. */
    private static final class Recorder implements Wire {
        final StringBuilder sent = new StringBuilder();
        boolean closed;

        @Override
        public void send(byte[] bytes) {
            sent.append(new String(bytes, StandardCharsets.ISO_8859_1));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static void say(ServerConnection c, String text) {
        c.receive(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void theWholeConversation() {
        Recorder wire = new Recorder();
        ServerConnection c = new ServerConnection(new EchoExchange(), wire, TODAY);
        say(c, "HELLO TRIADA-NET/1 model=TRIADA-88 cols=40 rows=25 client=2.0 future=yes\r\n");
        assertEquals("WELCOME TRIADA-NET/1 1985-11-09\n", wire.sent.toString());
        assertEquals(new Caller("TRIADA-88", 40, 25, "2.0"), c.caller());
        say(c, "DIAL 1-11-11\nAB");
        assertEquals("WELCOME TRIADA-NET/1 1985-11-09\nCONNECT 1200\nECHO READY\rAB", wire.sent.toString());
        say(c, "\u0004");
        assertTrue(wire.sent.toString().endsWith("BYE\r"));
        assertTrue(wire.closed);
        assertTrue(c.closed());
    }

    @Test
    void refusals() {
        String[][] cases = {
            {"GET / HTTP/1.1\n", "ERROR expected HELLO TRIADA-NET/1\n"},
            {"HELLO TRIADA-NET/1\nHANG ON\n", "ERROR expected DIAL\n"},
            {"HELLO TRIADA-NET/1\nDIAL 2-22-22\n", "BUSY\n"},
            {"HELLO TRIADA-NET/1\nDIAL 3-33-33\n", "NO CARRIER\n"},
            {"HELLO TRIADA-NET/1\nDIAL 9-99-99\n", "NO NUMBER\n"},
            {"X".repeat(300), "ERROR line too long\n"},
        };
        for (String[] k : cases) {
            Recorder wire = new Recorder();
            say(new ServerConnection(new EchoExchange(), wire, TODAY), k[0]);
            assertTrue(wire.sent.toString().endsWith(k[1]), k[0] + " -> " + wire.sent);
            assertTrue(wire.closed, k[0]);
        }
    }

    @Test
    void oddScreenSizesFallBackToTheTriada85s() {
        Recorder wire = new Recorder();
        ServerConnection c = new ServerConnection(new EchoExchange(), wire, TODAY);
        say(c, "HELLO TRIADA-NET/1 cols=0 rows=many\n");
        assertEquals(new Caller("?", 27, 18, "?"), c.caller());
    }

    @Test
    void theInWorldDateIs41YearsAgo() {
        assertEquals("1985-01-01", InWorld.date(LocalDate.of(2026, 1, 1)));
        assertEquals("1987-02-28", InWorld.date(LocalDate.of(2028, 2, 29)), "1987 has no 29 February");
    }

    /** A TRIADA-85 dialing the echo exchange over an in-process loopback, through its registers. */
    @Test
    void aMachineCallsTheEchoBoard() {
        Machine m = new Machine();
        m.modem().setPort(new WireModemPort(new LoopbackNetwork(new EchoExchange(), () -> TODAY),
                MachineProfiles.TRIADA_85));
        int io = MemoryMap.IO_BASE;
        assertEquals(ModemDevice.CONNECTED, call(m, "1-11-11"));
        for (char ch : "HI".toCharArray()) {
            m.bus().write(io + IoRegisters.MDM_TX, ch);
        }
        m.bus().write(io + IoRegisters.MDM_TX, EchoExchange.HANG_UP);
        StringBuilder got = new StringBuilder();
        for (int f = 0; f < 30; f++) {
            m.modem().endFrame();
            while (m.bus().read(io + IoRegisters.MDM_RXCOUNT) > 0) {
                got.append((char) m.bus().read(io + IoRegisters.MDM_RX));
            }
        }
        assertEquals("ECHO READY\rHIBYE\r", got.toString());
        assertEquals(ModemDevice.NO_CARRIER, m.bus().read(io + IoRegisters.MDM_STATUS));
        assertEquals(ModemDevice.BUSY, call(m, "2-22-22"));
        assertEquals(ModemDevice.NO_CARRIER, call(m, "3-33-33"));
        assertEquals(ModemDevice.NO_NUMBER, call(m, "0-00-00"));
    }

    private static int call(Machine m, String number) {
        int io = MemoryMap.IO_BASE;
        for (int i = 0; i < number.length(); i++) {
            m.bus().write(2_000 + i, number.charAt(i));
        }
        m.bus().write(2_000 + number.length(), 0);
        m.bus().write(io + IoRegisters.MDM_ADDR, 2_000);
        m.bus().write(io + IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        for (int f = 0; f < 200 && m.bus().read(io + IoRegisters.MDM_STATUS) == ModemDevice.DIALING; f++) {
            m.modem().endFrame();
        }
        return m.bus().read(io + IoRegisters.MDM_STATUS);
    }
}
