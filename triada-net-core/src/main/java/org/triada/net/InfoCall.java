package org.triada.net;

import java.util.ArrayList;
import java.util.List;

/**
 * TRIADA-NET INFO, number 0-00-00 (NET-SPEC §4, M18): the directory of public
 * numbers. No login: it lists every board that isn't unlisted, waits for a
 * key and hangs up.
 */
final class InfoCall extends TextCall {

    InfoCall(Network net, Caller caller) {
        super(net, caller);
    }

    @Override
    protected void begin() {
        out.clear().color(4, -12);
        out.line("TRIADA-NET INFO");
        out.line("PUBLIC NUMBERS, " + net.inWorldDate(net.now()));
        out.line("");
        List<String> lines = new ArrayList<>();
        lines.add(Network.INFO_NUMBER + " TRIADA-NET INFO");
        for (BoardDef b : net.directory()) {
            lines.add(b.number() + " " + b.name());
        }
        page(lines, () -> {
            out.line("");
            out.text("PRESS ANY KEY.");
            anyKey(() -> bye(""));
        });
    }
}
