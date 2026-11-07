package org.triada.core.cpu;

/**
 * Why the CPU stopped with an error (SPEC §3.9). A fault halts the CPU; the
 * debugger shows the reason and the address of the instruction that caused it.
 *
 * <p>The spec defines one fault today. New ones get the next free code, so
 * codes never change meaning.
 */
public enum Fault {

    /** JIF with a condition other than −2 … +3 (SPEC §3.8). */
    INVALID_CONDITION(1, "invalid JIF condition");

    private final int code;
    private final String description;

    Fault(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** The fault code: a small number that stays stable across versions. */
    public int code() {
        return code;
    }

    /** A short human-readable description, e.g. for the debugger. */
    public String description() {
        return description;
    }
}
