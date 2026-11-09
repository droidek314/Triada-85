package org.triada.net;

/**
 * Where TRIADA-NET writes down what happened (NET-SPEC §5, M18): calls,
 * logins, registrations, uploads, reports, moderation. Each entry is an event
 * name and pairs of field names and values. Passwords are never passed here.
 * The server writes JSON lines; tests can collect the events.
 */
public interface NetLog {

    /** {@code fields} are name, value, name, value, … */
    void event(String event, Object... fields);

    /** Writes nothing. */
    NetLog NONE = (event, fields) -> { };
}
