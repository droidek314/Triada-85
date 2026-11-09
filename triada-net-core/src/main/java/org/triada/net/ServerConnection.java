package org.triada.net;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * The server side of the TRIADA-NET wire protocol for one connection
 * (NET-SPEC §2, M17), with no network code: a transport feeds it the bytes it
 * receives ({@link #receive(byte[])}) and gives it a {@link Wire} to answer
 * on. It reads the caller's HELLO and DIAL lines, asks the {@link Exchange}
 * what the number does, and after CONNECT passes every byte to the board's
 * {@link Call}.
 *
 * <p>The public methods are synchronized: a transport may feed bytes from one
 * thread while a timer calls {@link #tick()} from another (M18).
 */
public final class ServerConnection {

    /** A control line longer than this ends the connection. */
    public static final int MAX_LINE = 256;

    private enum Phase { HELLO, DIAL, RAW, CLOSED }

    private final Exchange exchange;
    private final Wire wire;
    private final String date;
    private final String address;
    private final StringBuilder line = new StringBuilder();
    private Phase phase = Phase.HELLO;
    private Caller caller;
    private Call call;

    /** A connection whose in-world date is taken from {@code today} (NET-SPEC §4), from an unnamed address. */
    public ServerConnection(Exchange exchange, Wire wire, LocalDate today) {
        this(exchange, wire, today, "local");
    }

    /** A connection from the network address {@code address} (for limits and bans, M18). */
    public ServerConnection(Exchange exchange, Wire wire, LocalDate today, String address) {
        this.exchange = exchange;
        this.wire = wire;
        this.date = InWorld.date(today);
        this.address = address;
    }

    /** Bytes from the caller. */
    public synchronized void receive(byte[] bytes) {
        receive(bytes, 0, bytes.length);
    }

    /** {@code length} bytes from the caller, starting at {@code offset}. */
    public synchronized void receive(byte[] bytes, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            if (phase == Phase.CLOSED) {
                return;
            }
            receive(bytes[i] & 0xFF);
        }
    }

    private void receive(int c) {
        if (phase == Phase.RAW) {
            call.receive(c);
            return;
        }
        if (c == '\n') {
            String text = line.toString();
            line.setLength(0);
            if (phase == Phase.HELLO) {
                hello(text);
            } else {
                dial(text);
            }
        } else if (c != '\r') {
            if (line.length() >= MAX_LINE) {
                refuse("ERROR line too long");
                return;
            }
            line.append((char) c);
        }
    }

    private void hello(String text) {
        if (!text.startsWith("HELLO TRIADA-NET/1")) {
            refuse("ERROR expected HELLO TRIADA-NET/1");
            return;
        }
        String model = "?";
        String client = "?";
        int cols = 27;
        int rows = 18;
        for (String field : text.split(" ")) {
            int eq = field.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = field.substring(0, eq);
            String value = field.substring(eq + 1);
            switch (key) {
                case "model" -> model = value;
                case "client" -> client = value;
                case "cols" -> cols = number(value, cols);
                case "rows" -> rows = number(value, rows);
                default -> {
                    // unknown fields are for future versions
                }
            }
        }
        caller = new Caller(model, cols, rows, client, address);
        send("WELCOME TRIADA-NET/1 " + date + "\n");
        phase = Phase.DIAL;
    }

    /** A screen size from 1 to 243, or the default. */
    private static int number(String value, int otherwise) {
        try {
            int n = Integer.parseInt(value);
            return n >= 1 && n <= 243 ? n : otherwise;
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    private void dial(String text) {
        if (!text.startsWith("DIAL ")) {
            refuse("ERROR expected DIAL");
            return;
        }
        Answer answer = exchange.dial(text.substring(5).trim(), caller);
        switch (answer.kind()) {
            case CONNECT -> connect(answer.call());
            case BUSY -> refuse("BUSY");
            case NO_NUMBER -> refuse("NO NUMBER");
            default -> refuse("NO CARRIER");
        }
    }

    private void connect(Call answered) {
        send("CONNECT 1200\n");
        phase = Phase.RAW;
        call = answered;
        call.start(new Wire() {
            @Override
            public void send(byte[] bytes) {
                if (phase == Phase.RAW) {
                    wire.send(bytes);
                }
            }

            @Override
            public void close() {
                hangUp();
            }
        });
    }

    /** About once a second: passed on to the board, for its timeouts (M18). */
    public synchronized void tick() {
        if (phase == Phase.RAW) {
            call.tick();
        }
    }

    /** The caller hung up, or the connection was lost. */
    public synchronized void hungUp() {
        if (phase == Phase.CLOSED) {
            return;
        }
        boolean answered = phase == Phase.RAW;
        phase = Phase.CLOSED;
        if (answered) {
            call.hungUp();
        }
    }

    /** True once either side hung up. */
    public synchronized boolean closed() {
        return phase == Phase.CLOSED;
    }

    /** Who called, once their HELLO arrived. */
    public Caller caller() {
        return caller;
    }

    private void refuse(String reply) {
        send(reply + "\n");
        hangUp();
    }

    private void hangUp() {
        if (phase != Phase.CLOSED) {
            phase = Phase.CLOSED;
            wire.close();
        }
    }

    private void send(String text) {
        wire.send(text.getBytes(StandardCharsets.UTF_8));
    }
}
