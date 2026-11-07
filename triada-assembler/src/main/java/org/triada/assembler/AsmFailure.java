package org.triada.assembler;

/**
 * Internal: thrown inside the assembler to abandon the current line with one
 * error. The assembler catches it, records the error and moves on to the
 * next line. Not part of the public API.
 */
final class AsmFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    final int column;
    /** A follow-on failure whose cause was already reported; it aborts the line silently. */
    final boolean silent;

    AsmFailure(int column, String message) {
        this(column, message, false);
    }

    private AsmFailure(int column, String message, boolean silent) {
        super(message, null, false, false);   // no stack trace: this is ordinary control flow
        this.column = column;
        this.silent = silent;
    }

    /** For a line that uses a constant whose own definition already failed. */
    static AsmFailure silent() {
        return new AsmFailure(0, "(already reported)", true);
    }
}
