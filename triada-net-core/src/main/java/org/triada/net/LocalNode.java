package org.triada.net;

import java.time.Clock;
import java.util.function.Supplier;
import org.triada.core.modem.ModemLink;

/**
 * TRIADA-NET inside the emulator itself (M19): the built-in boards with their
 * content, in memory, reached through an in-process line. Anyone can try the
 * network offline: the desktop with {@code --local-node}, the command line
 * with {@code triada run --local-node}. Nothing is kept after the program
 * ends, and nobody else can call in.
 */
public final class LocalNode {

    private LocalNode() {
    }

    /** A fresh network: the built-in boards, seeded, in a store in memory. */
    public static Network network() {
        return network(Clock.systemUTC());
    }

    /** The same, on {@code clock} (pictures and tests fix the date). */
    public static Network network(Clock clock) {
        MemoryNetStore store = new MemoryNetStore();
        var boards = Boards.builtIn();
        Content.seed(store, boards, Boards::file);
        return new Network(boards, store, Limits.DEFAULT, clock, NetLog.NONE);
    }

    /** A line opener for a machine's modem: {@code new WireModemPort(LocalNode.line(), profile)}. */
    public static Supplier<ModemLink> line() {
        return new LoopbackNetwork(network());
    }
}
