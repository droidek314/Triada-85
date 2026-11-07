package org.triada.assembler;

import java.util.List;

/**
 * Thrown when a program has errors. It carries all of them (up to a limit),
 * so a person can fix several mistakes per attempt instead of one.
 */
public final class AssemblyException extends Exception {

    private static final long serialVersionUID = 1L;

    // transient: Java serialization of exceptions is never used here, and the list isn't serializable.
    private final transient List<AssemblyError> errors;

    public AssemblyException(List<AssemblyError> errors) {
        super(format(errors));
        this.errors = List.copyOf(errors);
    }

    /** The errors, in source order. */
    public List<AssemblyError> errors() {
        return errors;
    }

    private static String format(List<AssemblyError> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append(errors.size()).append(errors.size() == 1 ? " error" : " errors");
        for (AssemblyError e : errors) {
            sb.append('\n').append(e);
        }
        return sb.toString();
    }
}
