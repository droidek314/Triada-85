package org.triada.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the tokens of one line into a {@link Line} (SPEC §9.1):
 *
 * <pre>
 *   line       := [label ':'] [statement] [';' comment]
 *   statement  := NAME '=' expr                    (constant)
 *               | mnemonic [operand {',' operand}]
 *               | '.' directive [operand {',' operand}]
 *   operand    := register | '[' register [('+'|'-') expr] ']' | expr | string
 *   expr       := unary {('+'|'-') unary}
 *   unary      := ('+'|'-') unary | number | char | name
 * </pre>
 *
 * The parser only checks the <em>shape</em> of a line. Whether an instruction
 * has the right operands is checked by the {@link Assembler}, which knows each
 * mnemonic's rules.
 */
final class Parser {

    private final List<Token> tokens;
    private int pos;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** Lexes and parses one line. @throws AsmFailure on the first problem */
    static Line parse(String text) {
        return new Parser(Lexer.tokenize(text)).line();
    }

    private Line line() {
        String label = null;
        int labelColumn = 0;
        if (peek().type() == Token.Type.IDENT && peek(1).type() == Token.Type.COLON) {
            Token t = advance();
            advance();
            checkNotRegister(t, "a label");
            label = t.text();
            labelColumn = t.column();
        }

        Token first = peek();
        switch (first.type()) {
            case EOL:
                return new Line(label, labelColumn, Line.Kind.EMPTY, null, 0, null, List.of());
            case DIRECTIVE: {
                advance();
                List<Operand> args = operands();
                return new Line(label, labelColumn, Line.Kind.DIRECTIVE, first.text().toLowerCase(Locale.ROOT),
                        first.column(), null, args);
            }
            case IDENT: {
                if (peek(1).type() == Token.Type.EQUALS) {
                    if (label != null) {
                        throw new AsmFailure(first.column(), "a constant definition can't have a label");
                    }
                    advance();
                    advance();
                    checkNotRegister(first, "a constant");
                    Expr value = expr();
                    expectEnd();
                    return new Line(null, 0, Line.Kind.CONSTANT, first.text(), first.column(), value, List.of());
                }
                advance();
                List<Operand> ops = operands();
                return new Line(label, labelColumn, Line.Kind.INSTRUCTION, first.text().toUpperCase(Locale.ROOT),
                        first.column(), null, ops);
            }
            default:
                throw new AsmFailure(first.column(),
                        "expected a label, instruction, directive or constant, found " + first.describe());
        }
    }

    private List<Operand> operands() {
        List<Operand> ops = new ArrayList<>();
        if (peek().type() == Token.Type.EOL) {
            return ops;
        }
        ops.add(operand());
        while (peek().type() == Token.Type.COMMA) {
            advance();
            ops.add(operand());
        }
        expectEnd();
        return ops;
    }

    private Operand operand() {
        Token t = peek();
        if (t.type() == Token.Type.STRING) {
            advance();
            return new Operand.Str(t.text(), t.column());
        }
        if (t.type() == Token.Type.LBRACKET) {
            advance();
            Token r = advance();
            int base = Registers.number(r);
            if (base < 0) {
                throw new AsmFailure(r.column(), "expected a register (R0 .. R8) after '[', found " + r.describe());
            }
            Expr offset = null;
            if (peek().type() == Token.Type.PLUS || peek().type() == Token.Type.MINUS) {
                offset = expr();            // the sign is part of the expression: [R1-3] is R1 + (−3)
            }
            Token close = advance();
            if (close.type() != Token.Type.RBRACKET) {
                throw new AsmFailure(close.column(), "expected '+', '-' or ']' in memory operand, found "
                        + close.describe());
            }
            return new Operand.Mem(base, offset, t.column());
        }
        int reg = Registers.number(t);
        if (reg >= 0) {
            advance();
            Token after = peek();
            if (after.type() == Token.Type.PLUS || after.type() == Token.Type.MINUS) {
                throw new AsmFailure(after.column(), "register arithmetic needs brackets: write ["
                        + t.text() + after.text() + "...]");
            }
            return new Operand.Reg(reg, t.column());
        }
        return new Operand.Value(expr(), t.column());
    }

    private Expr expr() {
        Expr left = unary();
        while (peek().type() == Token.Type.PLUS || peek().type() == Token.Type.MINUS) {
            Token op = advance();
            Expr right = unary();
            left = new Expr.Bin(left, op.type() == Token.Type.PLUS, right, op.column());
        }
        return left;
    }

    private Expr unary() {
        Token t = advance();
        switch (t.type()) {
            case MINUS:
                return new Expr.Neg(unary(), t.column());
            case PLUS:
                return unary();
            case NUMBER:
            case CHAR:
                return new Expr.Num(t.value(), t.column());
            case IDENT:
                if (Registers.number(t) >= 0) {
                    throw new AsmFailure(t.column(), "a register (" + t.text()
                            + ") can't be used in an expression; expected a number or a name");
                }
                return new Expr.Sym(t.text(), t.column());
            default:
                throw new AsmFailure(t.column(), "expected a number, character or name, found " + t.describe());
        }
    }

    private void expectEnd() {
        Token t = peek();
        if (t.type() != Token.Type.EOL) {
            throw new AsmFailure(t.column(), "expected ',' or end of line, found " + t.describe());
        }
    }

    private void checkNotRegister(Token t, String what) {
        if (Registers.number(t) >= 0) {
            throw new AsmFailure(t.column(), "'" + t.text() + "' is a register name and can't be " + what);
        }
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peek(int ahead) {
        return tokens.get(Math.min(pos + ahead, tokens.size() - 1));
    }

    /** Returns the current token and moves on; never moves past EOL. */
    private Token advance() {
        Token t = tokens.get(pos);
        if (t.type() != Token.Type.EOL) {
            pos++;
        }
        return t;
    }
}
