package org.triada.net;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Counts events per key in a sliding time window (NET-SPEC §5, M18): "at
 * most 5 posts in 10 minutes by PETYA", "at most 10 wrong passwords in 10
 * minutes from this address". Keys are free text, such as "post:PETYA".
 */
public final class RateLimiter {

    private final Map<String, ArrayDeque<Instant>> events = new HashMap<>();
    private int calls;

    /**
     * Records an event for {@code key} if fewer than {@code max} happened in the
     * last {@code window}; returns false (and records nothing) otherwise.
     */
    public synchronized boolean allow(String key, int max, Duration window, Instant now) {
        ArrayDeque<Instant> q = recent(key, window, now);
        if (q.size() >= max) {
            return false;
        }
        q.add(now);
        return true;
    }

    /** Records an event without asking (e.g. a wrong password). */
    public synchronized void record(String key, Instant now) {
        events.computeIfAbsent(key, k -> new ArrayDeque<>()).add(now);
    }

    /** How many events {@code key} had in the last {@code window}. */
    public synchronized int count(String key, Duration window, Instant now) {
        return recent(key, window, now).size();
    }

    private ArrayDeque<Instant> recent(String key, Duration window, Instant now) {
        if (++calls % 1_000 == 0) {
            prune(now);
        }
        ArrayDeque<Instant> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant from = now.minus(window);
        while (!q.isEmpty() && !q.peek().isAfter(from)) {
            q.poll();
        }
        return q;
    }

    /** Forgets keys with nothing in the last day, so the map doesn't grow forever. */
    private void prune(Instant now) {
        Instant from = now.minus(Duration.ofDays(1));
        for (Iterator<ArrayDeque<Instant>> it = events.values().iterator(); it.hasNext();) {
            ArrayDeque<Instant> q = it.next();
            if (q.isEmpty() || !q.peekLast().isAfter(from)) {
                it.remove();
            }
        }
    }
}
