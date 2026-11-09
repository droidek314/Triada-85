package org.triada.web;

import org.triada.core.modem.ModemLink;

/**
 * A modem line over a WebSocket (M17): the web version's way to TRIADA-NET.
 * The socket lives in the page ({@code triadaModem} in {@code index.html});
 * this class only passes bytes in and out. Browsers send WebSocket data
 * whenever it comes, so nothing here waits. The page is served over HTTPS,
 * so the server's address must be {@code wss://}.
 */
final class WebModemLink implements ModemLink {

    private final int id;

    WebModemLink(String url) {
        id = WebPage.modemOpen(url);
    }

    @Override
    public int read() {
        return WebPage.modemRead(id);
    }

    @Override
    public void send(byte[] bytes) {
        WebPage.modemSend(id, bytes);
    }

    @Override
    public boolean isClosed() {
        return WebPage.modemClosed(id);
    }

    @Override
    public void close() {
        WebPage.modemClose(id);
    }
}
