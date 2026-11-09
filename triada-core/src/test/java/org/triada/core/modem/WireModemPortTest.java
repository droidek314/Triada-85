package org.triada.core.modem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.triada.core.MachineProfiles;

/** The client side of the wire protocol (NET-SPEC §2), against a scripted server on a loopback link. */
class WireModemPortTest {

    /** The server end: records each line it gets and answers it with {@code script}. */
    private final List<String> heard = new ArrayList<>();
    private LoopbackLink serverEnd;

    private WireModemPort port(Function<String, String> script) {
        return new WireModemPort(() -> {
            LoopbackLink[] ends = LoopbackLink.pair();
            serverEnd = ends[1];
            serverEnd.listen(new LoopbackLink.Listener() {
                private final StringBuilder line = new StringBuilder();

                @Override
                public void received(byte[] bytes) {
                    for (byte b : bytes) {
                        if (b == '\n') {
                            heard.add(line.toString());
                            String answer = script.apply(line.toString());
                            line.setLength(0);
                            if (answer == null) {
                                serverEnd.close();
                            } else if (!answer.isEmpty()) {
                                serverEnd.send(answer.getBytes(StandardCharsets.UTF_8));
                            }
                        } else {
                            line.append((char) b);
                        }
                    }
                }

                @Override
                public void closed() {
                    heard.add("<hung up>");
                }
            });
            return ends[0];
        }, MachineProfiles.TRIADA_85);
    }

    private static String normal(String line) {
        return line.startsWith("HELLO") ? "WELCOME TRIADA-NET/1 1985-11-09\r\n" : "CONNECT 1200\n";
    }

    @Test
    void handshakeThenRawBytes() {
        WireModemPort p = port(WireModemPortTest::normal);
        assertEquals(ModemDevice.IDLE, p.status());
        p.dial("1-11-11");
        assertEquals(ModemDevice.CONNECTED, p.status());
        assertEquals(List.of("HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=1.1", "DIAL 1-11-11"),
                heard);
        assertEquals("1985-11-09", p.welcomeDate());
        serverEnd.send("HI\n".getBytes(StandardCharsets.UTF_8));
        assertEquals('H', p.read());
        assertEquals('I', p.read());
        assertEquals('\n', p.read(), "after CONNECT, a line feed is just a character");
        assertEquals(-1, p.read());
        p.write(200);
        p.hangUp();
        assertEquals("<hung up>", heard.get(heard.size() - 1));
        assertEquals(ModemDevice.IDLE, p.status());
    }

    @Test
    void bytesInTheSameChunkAsConnectAreRaw() {
        WireModemPort p = port(line -> line.startsWith("HELLO") ? "WELCOME TRIADA-NET/1 1985-11-09\n"
                : "CONNECT 1200\nAB");
        p.dial("1-11-11");
        assertEquals(ModemDevice.CONNECTED, p.status());
        assertEquals('A', p.read());
        assertEquals('B', p.read());
    }

    @Test
    void theServersAnswers() {
        String[][] cases = {{"BUSY", "-2"}, {"NO NUMBER", "-3"}, {"NO CARRIER", "-1"}, {"WHAT?", "-1"}};
        for (String[] c : cases) {
            WireModemPort p = port(line -> line.startsWith("HELLO") ? "WELCOME TRIADA-NET/1 1985-11-09\n"
                    : c[0] + "\n");
            p.dial("2-22-22");
            assertEquals(Integer.parseInt(c[1]), p.status(), c[0]);
            assertTrue(serverEnd.isClosed(), "the port hangs up after a failed call");
        }
    }

    @Test
    void anErrorOrSilenceIsOffline() {
        WireModemPort p = port(line -> "ERROR server full\n");
        p.dial("1-11-11");
        assertEquals(ModemDevice.OFFLINE, p.status());
        p = port(line -> null);                             // hangs up without a word
        p.dial("1-11-11");
        assertEquals(ModemDevice.OFFLINE, p.status());
        p = port(line -> "X".repeat(300));                  // never ends its line
        p.dial("1-11-11");
        assertEquals(ModemDevice.OFFLINE, p.status());
    }

    @Test
    void closedAfterTheDialIsNoCarrier() {
        WireModemPort p = port(line -> line.startsWith("HELLO") ? "WELCOME TRIADA-NET/1 1985-11-09\n" : null);
        p.dial("1-11-11");
        assertEquals(ModemDevice.NO_CARRIER, p.status());
    }

    @Test
    void aSlowServerKeepsUsDialing() {
        WireModemPort p = port(line -> "");                 // says nothing yet
        p.dial("1-11-11");
        assertEquals(ModemDevice.DIALING, p.status());
        serverEnd.send("WELCOME TRIADA-NET/1 1985-11-09\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(ModemDevice.DIALING, p.status());
        serverEnd.send("CONNECT 1200\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(ModemDevice.CONNECTED, p.status());
    }

    @Test
    void theOtherSideHangsUp() {
        WireModemPort p = port(WireModemPortTest::normal);
        p.dial("1-11-11");
        assertEquals(ModemDevice.CONNECTED, p.status());
        serverEnd.send(new byte[] {'Z'});
        serverEnd.close();
        assertEquals(ModemDevice.NO_CARRIER, p.status());
        assertEquals('Z', p.read(), "what came before the hang-up can still be read");
    }

    @Test
    void noLinkMeansOffline() {
        WireModemPort none = new WireModemPort(() -> null, MachineProfiles.TRIADA_85);
        none.dial("1-11-11");
        assertEquals(ModemDevice.OFFLINE, none.status());
        WireModemPort broken = new WireModemPort(() -> {
            throw new IllegalArgumentException("bad address");
        }, MachineProfiles.TRIADA_85);
        broken.dial("1-11-11");
        assertEquals(ModemDevice.OFFLINE, broken.status());
    }

    @Test
    void theDeviceThroughTheWire() {
        org.triada.core.Machine m = new org.triada.core.Machine();
        m.modem().setPort(port(line -> line.startsWith("HELLO") ? "WELCOME TRIADA-NET/1 1985-11-09\n"
                : "CONNECT 1200\nOK"));
        m.modem().setTurbo(true);
        int[] number = {'1', '-', '1', '1', '-', '1', '1', 0};
        for (int i = 0; i < number.length; i++) {
            m.bus().write(500 + i, number[i]);
        }
        int io = org.triada.core.memory.MemoryMap.IO_BASE;
        m.bus().write(io + org.triada.core.io.IoRegisters.MDM_ADDR, 500);
        m.bus().write(io + org.triada.core.io.IoRegisters.MDM_CMD, ModemDevice.CMD_DIAL);
        m.modem().endFrame();
        m.modem().endFrame();
        assertEquals(ModemDevice.CONNECTED, m.bus().read(io + org.triada.core.io.IoRegisters.MDM_STATUS));
        assertEquals(2, m.bus().read(io + org.triada.core.io.IoRegisters.MDM_RXCOUNT));
    }
}
