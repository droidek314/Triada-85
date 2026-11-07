package org.triada.core.io;

/**
 * A device that plugs into the I/O registers (SPEC §5): video, input, sound,
 * the debug port. The {@link org.triada.core.memory.Bus} forwards every read
 * and write of an I/O address to the device attached to that register.
 *
 * <p>Registers are identified by their number {@code k} from SPEC §5 (0 … 728),
 * not by their address. Register {@code k} lives at address {@code IO_BASE + k}.
 * Constants for every {@code k} are in {@link IoRegisters}.
 */
public interface IoDevice {

    /**
     * The CPU reads register {@code k}. This may have side effects: reading
     * KEY removes the key from the queue.
     *
     * @return a valid word
     */
    int read(int k);

    /**
     * Looks at register {@code k} <em>without</em> any side effect. The
     * debugger's memory viewer uses this so that looking doesn't change the
     * machine. The default is {@link #read(int)}, which is right for every
     * register whose read has no side effect.
     */
    default int peek(int k) {
        return read(k);
    }

    /** The CPU writes a valid word to register {@code k}. */
    void write(int k, int value);
}
