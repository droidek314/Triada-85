package org.triada.assembler;

/** One operand of an instruction or directive, as written in the source. */
sealed interface Operand {

    int column();

    /** A register: {@code R0} … {@code R8} (or {@code SP} for R8). */
    record Reg(int number, int column) implements Operand {
    }

    /** A memory operand {@code [Rn]}, {@code [Rn+expr]} or {@code [Rn-expr]}; {@code offset} may be null. */
    record Mem(int base, Expr offset, int column) implements Operand {
    }

    /** An expression: a number, a label, a sum … */
    record Value(Expr expr, int column) implements Operand {
    }

    /** A string literal, only valid for {@code .str} and {@code .include}. */
    record Str(String text, int column) implements Operand {
    }
}
