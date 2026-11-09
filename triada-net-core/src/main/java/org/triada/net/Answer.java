package org.triada.net;

/**
 * What a number does when dialed (NET-SPEC §2, M17): a board answers with a
 * {@link Call}, or the network says BUSY, NO NUMBER or NO CARRIER.
 */
public record Answer(Kind kind, Call call) {

    public enum Kind { CONNECT, BUSY, NO_NUMBER, NO_CARRIER }

    public static Answer connect(Call call) {
        return new Answer(Kind.CONNECT, call);
    }

    public static Answer busy() {
        return new Answer(Kind.BUSY, null);
    }

    public static Answer noNumber() {
        return new Answer(Kind.NO_NUMBER, null);
    }

    public static Answer noCarrier() {
        return new Answer(Kind.NO_CARRIER, null);
    }
}
