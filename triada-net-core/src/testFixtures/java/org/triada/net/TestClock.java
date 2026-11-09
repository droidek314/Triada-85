package org.triada.net;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand (M18). It starts at 2026-11-09 12:00 UTC, which is 1985-11-09 in the network. */
public final class TestClock extends Clock {

    private Instant now = Instant.parse("2026-11-09T12:00:00Z");

    public void advance(Duration d) {
        now = now.plus(d);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
