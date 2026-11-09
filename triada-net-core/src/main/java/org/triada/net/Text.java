package org.triada.net;

import java.util.ArrayList;
import java.util.List;

/** Small text helpers for a 27-column screen (M18). */
public final class Text {

    private Text() {
    }

    /**
     * Word-wraps {@code text} to lines of at most {@code width} characters.
     * Line breaks in the text are kept; a word longer than a line is cut.
     */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                while (word.length() > width) {             // a very long word: cut it
                    if (line.length() > 0) {
                        out.add(line.toString());
                        line.setLength(0);
                    }
                    out.add(word.substring(0, width));
                    word = word.substring(width);
                }
                if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) {
                    line.append(' ');
                }
                line.append(word);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** {@code s} cut to {@code width} characters. */
    public static String fit(String s, int width) {
        return s.length() <= width ? s : s.substring(0, width);
    }

    /** {@code s} with everything outside 32 … 126 (the TRIADA's font) removed. */
    public static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c <= 126) {
                b.append(c);
            }
        }
        return b.toString();
    }
}
