package org.triada.assembler;

import java.util.ArrayList;
import java.util.List;
import org.triada.core.ternary.Ternary;
import org.triada.core.ternary.Trit;

/**
 * Cuts one source line into {@link Token}s: names, numbers, strings and
 * punctuation. Everything after a {@code ;} (outside quotes) is a comment.
 *
 * <p>Numbers (SPEC §9.1): decimal {@code 123}, ternary {@code 0t+-0}, and
 * characters {@code 'A'}. A minus sign is its own token; {@code -7} is
 * "minus, then 7", which the parser turns into a negative number.
 */
final class Lexer {

    /** Decimal numbers larger than this can never be useful, and would overflow. */
    private static final long MAX_DECIMAL = 999_999_999L;

    private final String line;
    private int pos;

    private Lexer(String line) {
        this.line = line;
    }

    /**
     * @throws AsmFailure for anything that isn't a valid token, with its column
     */
    static List<Token> tokenize(String line) {
        return new Lexer(line).run();
    }

    private List<Token> run() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipSpaces();
            if (pos >= line.length() || line.charAt(pos) == ';') {
                tokens.add(new Token(Token.Type.EOL, "", 0, pos + 1));
                return tokens;
            }
            tokens.add(next());
        }
    }

    private void skipSpaces() {
        while (pos < line.length() && (line.charAt(pos) == ' ' || line.charAt(pos) == '\t')) {
            pos++;
        }
    }

    private Token next() {
        int start = pos;
        char c = line.charAt(pos);
        switch (c) {
            case ',':
                pos++;
                return new Token(Token.Type.COMMA, ",", 0, start + 1);
            case ':':
                pos++;
                return new Token(Token.Type.COLON, ":", 0, start + 1);
            case '=':
                pos++;
                return new Token(Token.Type.EQUALS, "=", 0, start + 1);
            case '[':
                pos++;
                return new Token(Token.Type.LBRACKET, "[", 0, start + 1);
            case ']':
                pos++;
                return new Token(Token.Type.RBRACKET, "]", 0, start + 1);
            case '+':
                pos++;
                return new Token(Token.Type.PLUS, "+", 0, start + 1);
            case '-':
                pos++;
                return new Token(Token.Type.MINUS, "-", 0, start + 1);
            case '\'':
                return charLiteral();
            case '"':
                return stringLiteral();
            case '.':
                pos++;
                if (pos >= line.length() || !isIdentStart(line.charAt(pos))) {
                    throw new AsmFailure(start + 1, "expected a directive name after '.', found "
                            + describeAt(pos));
                }
                String name = identifier();
                return new Token(Token.Type.DIRECTIVE, name, 0, start + 1);
            default:
                break;
        }
        if (Character.isDigit(c)) {
            return number();
        }
        if (isIdentStart(c)) {
            return new Token(Token.Type.IDENT, identifier(), 0, start + 1);
        }
        throw new AsmFailure(start + 1, "unexpected character '" + c + "'");
    }

    private String identifier() {
        int start = pos;
        while (pos < line.length() && isIdentPart(line.charAt(pos))) {
            pos++;
        }
        return line.substring(start, pos);
    }

    private Token number() {
        int start = pos;
        if (line.startsWith("0t", pos) || line.startsWith("0T", pos)) {
            pos += 2;
            int tritsStart = pos;
            while (pos < line.length() && Trit.isTritChar(line.charAt(pos))) {
                pos++;
            }
            String trits = line.substring(tritsStart, pos);
            if (trits.isEmpty()) {
                throw new AsmFailure(start + 1, "expected trits ('-', '0', '+') after '0t', found "
                        + describeAt(pos));
            }
            if (trits.length() > Ternary.MAX_WIDTH) {
                throw new AsmFailure(start + 1, "ternary number 0t" + trits + " has " + trits.length()
                        + " trits, but a word has only 12");
            }
            rejectGlued(start);
            return new Token(Token.Type.NUMBER, line.substring(start, pos), Ternary.parse(trits), start + 1);
        }
        long value = 0;
        while (pos < line.length() && Character.isDigit(line.charAt(pos))) {
            value = value * 10 + (line.charAt(pos) - '0');
            if (value > MAX_DECIMAL) {
                throw new AsmFailure(start + 1, "number " + line.substring(start, pos + 1)
                        + "... is far too large for a word (at most 265720)");
            }
            pos++;
        }
        rejectGlued(start);
        return new Token(Token.Type.NUMBER, line.substring(start, pos), value, start + 1);
    }

    /** "12abc" or "0t+-1" are mistakes, not a number followed by a name. */
    private void rejectGlued(int start) {
        if (pos < line.length() && isIdentPart(line.charAt(pos))) {
            throw new AsmFailure(start + 1, "malformed number '" + line.substring(start, pos)
                    + line.charAt(pos) + "...'");
        }
    }

    private Token charLiteral() {
        int start = pos;
        pos++;                                          // opening quote
        int code = literalChar('\'', start);
        if (pos >= line.length() || line.charAt(pos) != '\'') {
            throw new AsmFailure(start + 1, "expected closing ' of character literal, found "
                    + describeAt(pos));
        }
        pos++;
        return new Token(Token.Type.CHAR, line.substring(start, pos), code, start + 1);
    }

    private Token stringLiteral() {
        int start = pos;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= line.length()) {
                throw new AsmFailure(start + 1, "expected closing \" of string, found end of line");
            }
            if (line.charAt(pos) == '"') {
                pos++;
                return new Token(Token.Type.STRING, sb.toString(), 0, start + 1);
            }
            sb.append((char) literalChar('"', start));
        }
    }

    /**
     * Reads one character inside quotes, handling escapes. Only printable
     * ASCII is allowed, because the font has exactly those characters
     * (SPEC §4.8, DECISIONS.md D-013).
     */
    private int literalChar(char quote, int literalStart) {
        if (pos >= line.length()) {
            throw new AsmFailure(literalStart + 1, "unfinished " + (quote == '"' ? "string" : "character literal"));
        }
        char c = line.charAt(pos);
        if (c == '\\') {
            if (pos + 1 >= line.length()) {
                throw new AsmFailure(pos + 1, "expected an escape character after '\\', found end of line");
            }
            char e = line.charAt(pos + 1);
            pos += 2;
            switch (e) {
                case 'n':
                    return 10;
                case 't':
                    return 9;
                case '0':
                    return 0;
                case '\\':
                    return '\\';
                case '\'':
                    return '\'';
                case '"':
                    return '"';
                default:
                    throw new AsmFailure(pos - 1, "unknown escape '\\" + e
                            + "' (known: \\n \\t \\0 \\\\ \\' \\\")");
            }
        }
        if (c == quote) {
            throw new AsmFailure(pos + 1, "empty character literal ''");
        }
        if (c < 32 || c > 126) {
            throw new AsmFailure(pos + 1, "character '" + c + "' (code " + (int) c
                    + ") is not printable ASCII (32 .. 126); the TRIADA font has no such character");
        }
        pos++;
        return c;
    }

    private String describeAt(int p) {
        return p >= line.length() ? "end of line" : "'" + line.charAt(p) + "'";
    }

    static boolean isIdentStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }
}
