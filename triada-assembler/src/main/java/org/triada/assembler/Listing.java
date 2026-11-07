package org.triada.assembler;

import java.util.List;
import org.triada.core.ternary.Word;

/**
 * Renders the {@code .lst} listing (SPEC §9.5): every source line next to the
 * address and words it produced, in ternary and decimal.
 *
 * <pre>
 *  ADDRESS  TERNARY         DECIMAL   LINE  SOURCE
 *      243  000000+-0000-   …            5  start:  LDI R0, msg
 * </pre>
 *
 * A line that produces several words gets one row per word; very long blocks
 * ({@code .fill}, long strings) are shortened after {@link #MAX_ROWS} rows.
 */
final class Listing {

    /** At most this many rows per source line; the rest is summarised. */
    static final int MAX_ROWS = 8;

    private static final String BLANK_WORD = " ".repeat(8 + 2 + 12 + 2 + 8 + 2);

    private Listing() {
    }

    static String render(List<Assembler.SourceLine> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("; TRIADA-85 assembler listing\n");
        sb.append(";").append(pad("ADDRESS", 7)).append("  ").append(padRight("TERNARY", 12)).append("  ")
                .append(pad("DECIMAL", 8)).append("  ").append(pad("LINE", 5)).append("  SOURCE\n");
        String file = null;
        for (Assembler.SourceLine sl : lines) {
            if (!sl.file.equals(file)) {
                file = sl.file;
                sb.append("; ---- ").append(file).append(" ----\n");
            }
            int[] w = sl.words;
            boolean hasLabel = sl.parsed != null && sl.parsed.label() != null;
            if (w.length == 0) {
                sb.append(hasLabel ? pad(Long.toString(sl.address), 8) + " ".repeat(BLANK_WORD.length() - 8)
                        : BLANK_WORD);
                sb.append(pad(Integer.toString(sl.number), 5)).append("  ").append(sl.text).append('\n');
                continue;
            }
            int rows = w.length <= MAX_ROWS ? w.length : MAX_ROWS - 1;
            for (int i = 0; i < rows; i++) {
                sb.append(pad(Long.toString(sl.address + i), 8)).append("  ")
                        .append(Word.toString(w[i])).append("  ")
                        .append(pad(Integer.toString(w[i]), 8));
                if (i == 0) {
                    sb.append("  ").append(pad(Integer.toString(sl.number), 5)).append("  ").append(sl.text);
                }
                sb.append('\n');
            }
            if (rows < w.length) {
                sb.append(pad("...", 8)).append("  (").append(w.length - rows).append(" more words, up to address ")
                        .append(sl.address + w.length - 1).append(")\n");
            }
        }
        return sb.toString();
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    private static String padRight(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }
}
