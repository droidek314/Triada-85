package org.triada.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * The few things the web version needs from the page around it (M12). Each
 * method is one line of JavaScript; the page itself ({@code index.html})
 * holds the larger pieces: the sound player and the file drop.
 */
final class WebPage {

    private WebPage() {
    }

    /** The {@code ?program=NAME} part of the address, or "" if there is none. */
    @JSBody(script = "return new URLSearchParams(location.search).get('program') || '';")
    static native String programParameter();

    /** Hands one frame of samples (16-bit, 44.1 kHz) to the page's sound player. */
    @JSBody(params = "samples", script = "triadaSound.play(samples);")
    static native void playSamples(@JSByRef short[] samples);

    /** The name of a file dropped on the page since the last call, or null. */
    @JSBody(script = "return triadaDrop.name;")
    static native String droppedName();

    /** The bytes of that file; also forgets it, so it is taken only once. */
    @JSBody(script = "var b = triadaDrop.bytes; triadaDrop.name = null; triadaDrop.bytes = null; return b;")
    static native Int8Array takeDroppedBytes();

    /** Tells the page that the triskette's files changed, so its file list is redrawn (M13). */
    @JSBody(script = "if (window.triadaFilesChanged) triadaFilesChanged();")
    static native void trisketteChanged();

    /** Text pasted into the page since the last call (and forgets it), or null (M15). */
    @JSBody(script = "var t = triadaPaste; triadaPaste = null; return t;")
    static native String takePaste();

    /** The TRIADA-NET server address (M17): {@code ?net=} in the page address, else the page's setting; "" for none. */
    @JSBody(script = "return new URLSearchParams(location.search).get('net') || TRIADA_NET_URL || '';")
    static native String netAddress();

    /** Opens a WebSocket line to {@code url}; returns its number. */
    @JSBody(params = "url", script = "return triadaModem.open(url);")
    static native int modemOpen(String url);

    /** The next byte received on line {@code id}, or −1. */
    @JSBody(params = "id", script = "return triadaModem.read(id);")
    static native int modemRead(int id);

    /** Sends bytes on line {@code id}. */
    @JSBody(params = {"id", "bytes"}, script = "triadaModem.send(id, bytes);")
    static native void modemSend(int id, @JSByRef byte[] bytes);

    /** True if line {@code id} was closed or could not be opened. */
    @JSBody(params = "id", script = "return triadaModem.closed(id);")
    static native boolean modemClosed(int id);

    /** Hangs up line {@code id}. */
    @JSBody(params = "id", script = "triadaModem.close(id);")
    static native void modemClose(int id);

    /** Shows a line of text under the screen (what is running, or what went wrong). */
    @JSBody(params = "text", script = "document.getElementById('status').textContent = text;")
    static native void status(String text);
}
