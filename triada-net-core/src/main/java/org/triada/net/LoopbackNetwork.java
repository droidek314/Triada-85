package org.triada.net;

import java.time.LocalDate;
import java.util.function.Supplier;
import org.triada.core.modem.LoopbackLink;
import org.triada.core.modem.ModemLink;

/**
 * A TRIADA-NET server inside the same program (M17), for tests and offline
 * use: every call made through it gets a {@link LoopbackLink} whose far end
 * is a {@link ServerConnection} to {@code exchange}. Plug it into a machine
 * with {@code new WireModemPort(new LoopbackNetwork(exchange), profile)}.
 * No sockets and no threads: the server answers within the call that sends to it.
 */
public final class LoopbackNetwork implements Supplier<ModemLink> {

    private final Exchange exchange;
    private final Supplier<LocalDate> today;

    public LoopbackNetwork(Exchange exchange) {
        this(exchange, LocalDate::now);
    }

    /** A network whose in-world date comes from {@code today} (tests fix it). */
    public LoopbackNetwork(Exchange exchange, Supplier<LocalDate> today) {
        this.exchange = exchange;
        this.today = today;
    }

    @Override
    public ModemLink get() {
        LoopbackLink[] ends = LoopbackLink.pair();
        LoopbackLink server = ends[1];
        ServerConnection connection = new ServerConnection(exchange, new Wire() {
            @Override
            public void send(byte[] bytes) {
                server.send(bytes);
            }

            @Override
            public void close() {
                server.close();
            }
        }, today.get());
        server.listen(new LoopbackLink.Listener() {
            @Override
            public void received(byte[] bytes) {
                connection.receive(bytes);
            }

            @Override
            public void closed() {
                connection.hungUp();
            }
        });
        return ends[0];
    }
}
