package org.triada.assembler;

import java.util.List;

/**
 * One parsed source line: an optional label, then at most one statement.
 *
 * @param label        the label defined on this line, or null
 * @param labelColumn  its column
 * @param kind         what the rest of the line is
 * @param name         constant name, mnemonic (upper case) or directive name (lower case); null if EMPTY
 * @param nameColumn   its column
 * @param value        the constant's expression (CONSTANT only)
 * @param operands     the operands (INSTRUCTION and DIRECTIVE)
 */
record Line(String label, int labelColumn, Kind kind, String name, int nameColumn, Expr value,
        List<Operand> operands) {

    enum Kind {
        /** Nothing but (maybe) a label and a comment. */
        EMPTY,
        /** {@code NAME = expr} */
        CONSTANT,
        /** A mnemonic with operands. */
        INSTRUCTION,
        /** A directive such as {@code .org}. */
        DIRECTIVE
    }
}
