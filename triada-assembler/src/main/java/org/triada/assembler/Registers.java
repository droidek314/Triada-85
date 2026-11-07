package org.triada.assembler;

import java.util.Locale;

/**
 * Register names, case-insensitive (SPEC §9.1): {@code R0} … {@code R8}, plus
 * {@code SP} as another name for R8, the stack pointer (DECISIONS.md D-013).
 * These names are reserved: they can't be labels or constants.
 */
final class Registers {

    private Registers() {
    }

    /** The register number 0 … 8 for an identifier token, or −1 if it isn't a register. */
    static int number(Token t) {
        return t.type() == Token.Type.IDENT ? number(t.text()) : -1;
    }

    static int number(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (n.equals("SP")) {
            return 8;
        }
        if (n.length() == 2 && n.charAt(0) == 'R' && n.charAt(1) >= '0' && n.charAt(1) <= '8') {
            return n.charAt(1) - '0';
        }
        return -1;
    }

    /** The canonical name, {@code R0} … {@code R8}. */
    static String name(int number) {
        return "R" + number;
    }
}
