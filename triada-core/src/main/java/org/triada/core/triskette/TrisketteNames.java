package org.triada.core.triskette;

import java.util.Locale;

/**
 * The rules for triskette file names (SPEC §5.1): 1 to 12 characters, the
 * letters A–Z, the digits 0–9 and at most one dot. Lower case is turned into
 * upper case. By convention {@code .TB} is a TRIBASIC program and
 * {@code .TRI} is machine code.
 */
public final class TrisketteNames {

    /** The longest name, in characters. */
    public static final int MAX_LENGTH = 12;

    private TrisketteNames() {
    }

    /** {@code name} in upper case if it is a valid triskette name, otherwise null. */
    public static String normalize(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return null;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        int dots = 0;
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '.') {
                dots++;
            } else if (!(c >= 'A' && c <= 'Z') && !(c >= '0' && c <= '9')) {
                return null;
            }
        }
        return dots <= 1 ? upper : null;
    }

    /**
     * The name a host file gets when it is imported onto the triskette: upper
     * case, only letters and digits, machine code ({@code .tri}, or
     * {@code .tasm} assembled on the way) as {@code .TRI}, and the base
     * shortened so that the whole name fits in 12 characters.
     * {@code "trizmeyka.tasm"} becomes {@code "TRIZMEYK.TRI"}.
     */
    public static String forHostFile(String fileName) {
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String ext = "";
        int dot = base.lastIndexOf('.');
        if (dot >= 0) {
            ext = base.substring(dot + 1).toUpperCase(Locale.ROOT);
            base = base.substring(0, dot);
            if (ext.equals("TASM")) {
                ext = "TRI";
            }
            ext = lettersAndDigits(ext);
            if (ext.length() > 3) {
                ext = ext.substring(0, 3);
            }
        }
        base = lettersAndDigits(base.toUpperCase(Locale.ROOT));
        if (base.isEmpty()) {
            base = "FILE";
        }
        int room = MAX_LENGTH - (ext.isEmpty() ? 0 : ext.length() + 1);
        if (base.length() > room) {
            base = base.substring(0, room);
        }
        return ext.isEmpty() ? base : base + "." + ext;
    }

    private static String lettersAndDigits(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                b.append(c);
            }
        }
        return b.toString();
    }
}
