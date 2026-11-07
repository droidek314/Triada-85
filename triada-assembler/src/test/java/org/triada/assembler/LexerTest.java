package org.triada.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

    private static List<Token> lex(String s) {
        return Lexer.tokenize(s);
    }

    private static AsmFailure fails(String s) {
        return assertThrows(AsmFailure.class, () -> Lexer.tokenize(s));
    }

    @Test
    void aTypicalLine() {
        List<Token> t = lex("start:  LDI R0, msg   ; comment");
        assertEquals(List.of(Token.Type.IDENT, Token.Type.COLON, Token.Type.IDENT, Token.Type.IDENT,
                Token.Type.COMMA, Token.Type.IDENT, Token.Type.EOL), t.stream().map(Token::type).toList());
        assertEquals(1, t.get(0).column());
        assertEquals(9, t.get(2).column());
        assertEquals(13, t.get(3).column());
    }

    @Test
    void numbersInAllThreeNotations() {
        assertEquals(123, lex("123").get(0).value());
        assertEquals(6, lex("0t+-0").get(0).value());
        assertEquals(-2, lex("0T-+").get(0).value());
        assertEquals(65, lex("'A'").get(0).value());
        assertEquals(Token.Type.CHAR, lex("'A'").get(0).type());
        assertEquals(265_720, lex("0t++++++++++++").get(0).value());
    }

    @Test
    void minusIsASeparateToken() {
        List<Token> t = lex("-7");
        assertEquals(Token.Type.MINUS, t.get(0).type());
        assertEquals(7, t.get(1).value());
    }

    @Test
    void escapesInCharactersAndStrings() {
        assertEquals(10, lex("'\\n'").get(0).value());
        assertEquals(0, lex("'\\0'").get(0).value());
        assertEquals('\'', lex("'\\''").get(0).value());
        assertEquals("A\"B\\C", lex("\"A\\\"B\\\\C\"").get(0).text());
    }

    @Test
    void semicolonsInsideQuotesAreNotComments() {
        List<Token> t = lex(".str \"A;B\" ; real comment");
        assertEquals("A;B", t.get(1).text());
        assertEquals(';', lex("';'").get(0).value());
    }

    @Test
    void directivesLoseTheirDot() {
        Token t = lex(".org 243").get(0);
        assertEquals(Token.Type.DIRECTIVE, t.type());
        assertEquals("org", t.text());
    }

    @Test
    void errorsPointAtTheRightColumn() {
        AsmFailure e = fails("LDI R0, 12x");
        assertEquals(9, e.column);
        assertTrue(e.getMessage().contains("malformed number"), e.getMessage());

        assertEquals(9, fails("LDI R0, 0t").column);
        assertTrue(fails("LDI R0, 0t+++++++++++++").getMessage().contains("13 trits"));
        assertTrue(fails("x 9999999999").getMessage().contains("far too large"));
        assertTrue(fails(".str \"abc").getMessage().contains("closing \""));
        assertTrue(fails("'AB'").getMessage().contains("closing '"));
        assertTrue(fails("''").getMessage().contains("empty character"));
        assertTrue(fails("'\\q'").getMessage().contains("unknown escape"));
        assertTrue(fails("NOP @").getMessage().contains("unexpected character '@'"));
        assertEquals(5, fails("NOP @").column);
        assertTrue(fails("'é'").getMessage().contains("not printable ASCII"));
        assertTrue(fails(". org").getMessage().contains("directive name"));
    }
}
