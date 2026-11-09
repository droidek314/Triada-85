package org.triada.net;

import java.time.LocalDate;

/**
 * The date inside TRIADA-NET (NET-SPEC §4): the real date minus 41 years, so
 * 2026 is 1985 and time passes in step with ours.
 */
public final class InWorld {

    public static final int YEARS_BACK = 41;

    private InWorld() {
    }

    /** The in-world date for a real one, as YYYY-MM-DD: 2026-11-09 is 1985-11-09. */
    public static String date(LocalDate real) {
        return real.minusYears(YEARS_BACK).toString();
    }
}
