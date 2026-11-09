package org.triada.net.server;

import java.io.PrintStream;
import java.time.Instant;
import org.triada.net.NetLog;

/**
 * The server's log (NET-SPEC §5, M18): one JSON object per line, so a log
 * tool can search it, and a person can still read it.
 *
 * <pre>
 *   {"time":"2026-11-09T12:00:00Z","event":"login","board":"1-00-00","handle":"OLGA","ip":"10.0.0.1"}
 * </pre>
 *
 * Nothing that reaches here is a password: the sessions never pass one.
 */
public final class JsonLog implements NetLog {

    private final PrintStream out;

    public JsonLog(PrintStream out) {
        this.out = out;
    }

    @Override
    public void event(String event, Object... fields) {
        StringBuilder b = new StringBuilder("{\"time\":");
        string(b, Instant.now().toString());
        b.append(",\"event\":");
        string(b, event);
        for (int i = 0; i + 1 < fields.length; i += 2) {
            b.append(',');
            string(b, String.valueOf(fields[i]));
            b.append(':');
            Object v = fields[i + 1];
            if (v instanceof Number || v instanceof Boolean) {
                b.append(v);
            } else {
                string(b, String.valueOf(v));
            }
        }
        b.append('}');
        synchronized (out) {
            out.println(b);
        }
    }

    /** {@code s} as a JSON string, with quotes, backslashes and control characters escaped. */
    static void string(StringBuilder b, String s) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }

    /** {@code s} as a JSON string. */
    static String string(String s) {
        StringBuilder b = new StringBuilder();
        string(b, s);
        return b.toString();
    }
}
