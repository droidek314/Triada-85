package org.triada.core.modem;

/**
 * The telephone line behind the modem (SPEC §5.2, M17), as the
 * {@link ModemDevice} sees it. The host plugs one in: a {@link WireModemPort}
 * that talks to a TRIADA-NET server over TCP, WebSocket or an in-process
 * loopback, or {@link #OFFLINE} when there is no network.
 *
 * <p>Every method returns at once; nothing here waits. The device asks
 * {@link #status()} once per frame until the call is answered.
 */
public interface ModemPort {

    /** Starts a call to {@code number}; an earlier call is hung up first. */
    void dial(String number);

    /**
     * Where the call stands: {@link ModemDevice#DIALING} until the network
     * answers, then {@link ModemDevice#CONNECTED} or one of the failures
     * ({@link ModemDevice#NO_CARRIER}, {@link ModemDevice#BUSY},
     * {@link ModemDevice#NO_NUMBER}, {@link ModemDevice#OFFLINE}). After a
     * connection, NO_CARRIER means the other side hung up; characters it sent
     * before that may still be waiting in {@link #read()}.
     */
    int status();

    /** The next character received, 0 … 255, or −1 if none is waiting. */
    int read();

    /** Sends one character, 0 … 255. Ignored when not connected. */
    void write(int c);

    /** Ends the call (or does nothing if there is none). */
    void hangUp();

    /** No network at all: every call ends with {@link ModemDevice#OFFLINE}. It keeps no state, so machines can share it. */
    ModemPort OFFLINE = new ModemPort() {
        @Override
        public void dial(String number) {
        }

        @Override
        public int status() {
            return ModemDevice.OFFLINE;
        }

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void write(int c) {
        }

        @Override
        public void hangUp() {
        }
    };
}
