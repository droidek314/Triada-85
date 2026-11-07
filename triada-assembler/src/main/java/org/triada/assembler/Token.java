package org.triada.assembler;

/**
 * One "word" of a source line, as cut up by the {@link Lexer}.
 *
 * @param type   what kind of token it is
 * @param text   the exact source text (for error messages); for STRING, the decoded string
 * @param value  the numeric value for NUMBER and CHAR tokens
 * @param column where it starts in the line, from 1
 */
record Token(Type type, String text, long value, int column) {

    enum Type {
        /** A name: label, constant, mnemonic or register, e.g. {@code loop}, {@code ADD}, {@code R1}. */
        IDENT,
        /** A decimal ({@code 42}) or ternary ({@code 0t+-0}) number. */
        NUMBER,
        /** A character literal such as {@code 'A'}; its value is the character code. */
        CHAR,
        /** A string literal such as {@code "HELLO"}; {@code text} holds it decoded. */
        STRING,
        /** A directive such as {@code .org}; {@code text} holds the name without the dot. */
        DIRECTIVE,
        COMMA,
        COLON,
        EQUALS,
        LBRACKET,
        RBRACKET,
        PLUS,
        MINUS,
        /** End of the line (a comment also ends it). */
        EOL
    }

    /** How the token is described in "found ..." error messages. */
    String describe() {
        switch (type) {
            case EOL:
                return "end of line";
            case STRING:
                return "a string";
            case DIRECTIVE:
                return "'." + text + "'";
            default:
                return "'" + text + "'";
        }
    }
}
