package org.triada.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.triada.core.Machine;
import org.triada.core.io.IoRegisters;
import org.triada.core.memory.MemoryMap;
import org.triada.core.modem.ModemDevice;
import org.triada.core.modem.WireModemPort;
import org.triada.net.EchoExchange;
import org.triada.net.server.NetServer;

/** The modem over a real TCP connection to a TRIADA-NET echo server on this computer (M17). */
@Timeout(60)
class TcpModemTest {

    private static final int IO = MemoryMap.IO_BASE;

    private static int status(Machine m) {
        return m.bus().read(IO + IoRegisters.MDM_STATUS);
    }

    /** Dials and runs frames (with a pause, as a real screen would) until the call is answered. */
    private static void dial(Machine m, String number) throws InterruptedException {
        for (int i = 0; i < number.length(); i++) {
            m.bus().write(3_000 + i, number.charAt(i));
        }
        m.bus().write(3_000 + number.length(), 0);
        m.bus().write(IO + IoRegisters.MDM_ADDR, 3_000);
        m.bus().write(IO + IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        for (int f = 0; f < 2_000 && status(m) == ModemDevice.DIALING; f++) {
            m.modem().endFrame();
            Thread.sleep(2);
        }
    }

    @Test
    void echoOverTcp() throws Exception {
        try (NetServer server = new NetServer(new EchoExchange(), 0, 0, Set.of())) {
            Machine m = new Machine();
            m.modem().setPort(new WireModemPort(TcpModemLink.opener("localhost:" + server.tcpPort()), m.profile()));
            dial(m, EchoExchange.ECHO);
            assertEquals(ModemDevice.CONNECTED, status(m));
            for (char c : "TCP".toCharArray()) {
                m.bus().write(IO + IoRegisters.MDM_TX, c);
            }
            m.bus().write(IO + IoRegisters.MDM_TX, EchoExchange.HANG_UP);
            StringBuilder got = new StringBuilder();
            for (int f = 0; f < 2_000 && status(m) == ModemDevice.CONNECTED; f++) {
                m.modem().endFrame();
                while (m.bus().read(IO + IoRegisters.MDM_RXCOUNT) > 0) {
                    got.append((char) m.bus().read(IO + IoRegisters.MDM_RX));
                }
                Thread.sleep(2);
            }
            assertEquals("ECHO READY\rTCPBYE\r", got.toString());
            assertEquals(ModemDevice.NO_CARRIER, status(m), "the echo board hung up");
            dial(m, EchoExchange.BUSY);
            assertEquals(ModemDevice.BUSY, status(m));
        }
    }

    @Test
    void nobodyListeningIsOffline() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();                                // free, and closed again
        }
        Machine m = new Machine();
        m.modem().setPort(new WireModemPort(TcpModemLink.opener("localhost:" + port), m.profile()));
        dial(m, EchoExchange.ECHO);
        assertEquals(ModemDevice.OFFLINE, status(m));
    }

    @Test
    void addressesMustHaveAPort(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> TcpModemLink.opener("localhost"));
        assertThrows(IllegalArgumentException.class, () -> TcpModemLink.opener("localhost:abc"));
        assertThrows(IllegalArgumentException.class, () -> TcpModemLink.opener("localhost:70000"));
        String bas = Cli.write(dir, "x.bas", "PRINT 1\n");
        Cli.Result r = Cli.run("run", "--modem", "nowhere", "--type", bas);
        assertEquals(Main.EXIT_USAGE, r.code(), r.toString());
        assertTrue(r.err().contains("--modem: expected host:port"), r.toString());
    }
}
