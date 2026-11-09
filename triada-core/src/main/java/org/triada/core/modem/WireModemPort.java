package org.triada.core.modem;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.triada.core.MachineInfo;
import org.triada.core.MachineProfile;

/**
 * The client side of the TRIADA-NET wire protocol (NET-SPEC §2, M17), as a
 * {@link ModemPort}. Every call opens a fresh {@link ModemLink}, then:
 *
 * <pre>
 *   we send   HELLO TRIADA-NET/1 model=TRIADA-85 cols=27 rows=18 client=1.2
 *   server    WELCOME TRIADA-NET/1 1985-11-09        (or ERROR reason: offline)
 *   we send   DIAL 1-11-11
 *   server    CONNECT 1200 | BUSY | NO NUMBER | NO CARRIER
 * </pre>
 *
 * After CONNECT every byte is a terminal character, both ways, until either
 * side closes the link. Control lines end with a line feed (10); a carriage
 * return (13) before it is ignored (D-061).
 *
 * <p>The port reads the server's answers whenever the device asks for its
 * {@link #status()}, so it needs no thread of its own.
 */
public final class WireModemPort implements ModemPort {

    /** A control line longer than this is a broken server: the call fails. */
    static final int MAX_LINE = 256;

    private enum Phase { NONE, HELLO, DIAL, RAW, DONE }

    private final Supplier<ModemLink> opener;
    private final byte[] hello;

    private ModemLink link;
    private Phase phase = Phase.NONE;
    private int result;
    private String number;
    private String welcome;
    private final StringBuilder line = new StringBuilder();

    /** A port that opens links with {@code opener} and introduces itself as a {@code profile} machine. */
    public WireModemPort(Supplier<ModemLink> opener, MachineProfile profile) {
        this.opener = opener;
        this.hello = ascii("HELLO TRIADA-NET/1 model=" + profile.name() + " cols=" + profile.mapColumns()
                + " rows=" + profile.mapRows() + " client=" + MachineInfo.SPEC_VERSION + "\n");
    }

    /** The in-world date from the server's WELCOME line of the last call, or null. */
    public String welcomeDate() {
        return welcome;
    }

    @Override
    public void dial(String number) {
        hangUp();
        this.number = number;
        welcome = null;
        line.setLength(0);
        try {
            link = opener.get();
        } catch (RuntimeException e) {
            link = null;                    // e.g. a malformed server address
        }
        if (link == null) {
            phase = Phase.DONE;
            result = ModemDevice.OFFLINE;
            return;
        }
        link.send(hello);
        phase = Phase.HELLO;
    }

    @Override
    public int status() {
        switch (phase) {
            case NONE:
                return ModemDevice.IDLE;
            case DONE:
                return result;
            case RAW:
                return link.isClosed() ? ModemDevice.NO_CARRIER : ModemDevice.CONNECTED;
            default:
                readControlLines();
                return phase == Phase.RAW ? ModemDevice.CONNECTED
                        : phase == Phase.DONE ? result : ModemDevice.DIALING;
        }
    }

    /** Reads the server's control lines while the call is being set up. Stops right after CONNECT. */
    private void readControlLines() {
        while (phase == Phase.HELLO || phase == Phase.DIAL) {
            int c = link.read();
            if (c < 0) {
                if (link.isClosed()) {      // closed before saying anything useful
                    finish(phase == Phase.HELLO ? ModemDevice.OFFLINE : ModemDevice.NO_CARRIER);
                }
                return;
            }
            if (c == '\n') {
                handle(line.toString());
                line.setLength(0);
            } else if (c != '\r') {
                if (line.length() >= MAX_LINE) {
                    finish(ModemDevice.OFFLINE);
                    return;
                }
                line.append((char) c);
            }
        }
    }

    private void handle(String reply) {
        if (phase == Phase.HELLO) {
            if (reply.startsWith("WELCOME ")) {
                int date = reply.indexOf(' ', "WELCOME ".length());
                welcome = date < 0 ? "" : reply.substring(date + 1);
                link.send(ascii("DIAL " + number + "\n"));
                phase = Phase.DIAL;
            } else {
                finish(ModemDevice.OFFLINE);    // ERROR <reason>, or not a TRIADA-NET server
            }
            return;
        }
        if (reply.startsWith("CONNECT")) {
            phase = Phase.RAW;
        } else if (reply.equals("BUSY")) {
            finish(ModemDevice.BUSY);
        } else if (reply.equals("NO NUMBER")) {
            finish(ModemDevice.NO_NUMBER);
        } else {
            finish(ModemDevice.NO_CARRIER);     // NO CARRIER, or anything unexpected
        }
    }

    private void finish(int status) {
        link.close();
        phase = Phase.DONE;
        result = status;
    }

    @Override
    public int read() {
        return phase == Phase.RAW ? link.read() : -1;
    }

    @Override
    public void write(int c) {
        if (phase == Phase.RAW && !link.isClosed()) {
            link.send(new byte[] {(byte) c});
        }
    }

    @Override
    public void hangUp() {
        if (link != null) {
            link.close();
            link = null;
        }
        phase = Phase.NONE;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
