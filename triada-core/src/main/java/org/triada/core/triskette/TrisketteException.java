package org.triada.core.triskette;

/** A {@link TriskettePort} failed; {@link #status()} is what TS_STATUS reports (SPEC §5.1). */
public final class TrisketteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public TrisketteException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** {@link TrisketteDevice#NO_SPACE} or {@link TrisketteDevice#IO_ERROR}. */
    public int status() {
        return status;
    }
}
