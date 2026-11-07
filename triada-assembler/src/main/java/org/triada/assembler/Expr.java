package org.triada.assembler;

/**
 * An expression from the source, kept as a small tree until its value can be
 * worked out. Labels may be used before they are defined, so most values are
 * only known in the assembler's second pass.
 *
 * <p>Expressions (SPEC §9.1) combine numbers, characters, labels, constants
 * and built-in symbols with {@code +} and {@code -}. Every node remembers its
 * column, so an error can point at the exact symbol that is wrong.
 */
sealed interface Expr {

    int column();

    /** A number or character literal. */
    record Num(long value, int column) implements Expr {
    }

    /** A name: label, constant or built-in symbol. */
    record Sym(String name, int column) implements Expr {
    }

    /** {@code -x} */
    record Neg(Expr inner, int column) implements Expr {
    }

    /** {@code a + b} or {@code a - b}. */
    record Bin(Expr left, boolean plus, Expr right, int column) implements Expr {
    }
}
