package org.triada.web;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.teavm.jso.JSBody;
import org.triada.core.TriImage;
import org.triada.core.triskette.TrisketteDevice;
import org.triada.core.triskette.TrisketteException;
import org.triada.core.triskette.TrisketteNames;
import org.triada.core.triskette.TriskettePort;

/**
 * The web version's triskette (M13): files in the browser's local storage,
 * which keeps them between visits. Each file is one entry, its key
 * {@link #PREFIX} + the file name, its value the bytes of a {@code .tri}
 * file in Base64 (text, because local storage only holds text).
 *
 * <p>Every access is wrapped in try/catch on the JavaScript side: a browser
 * may refuse storage (private windows, blocked site data) or run out of room.
 * A refusal becomes {@link TrisketteDevice#IO_ERROR}, a full storage
 * {@link TrisketteDevice#NO_SPACE}. The page's Import and Export buttons
 * ({@code index.html}) use the same keys.
 */
final class WebTriskette implements TriskettePort {

    /** Key prefix in local storage; {@code index.html} uses the same one. */
    static final String PREFIX = "triada85.triskette.";

    @Override
    public List<String> names() {
        String joined = keys(PREFIX);
        if (joined == null) {
            throw new TrisketteException(TrisketteDevice.IO_ERROR, "browser storage is not available");
        }
        List<String> names = new ArrayList<>();
        for (String key : joined.isEmpty() ? new String[0] : joined.split("/")) {
            if (key.equals(TrisketteNames.normalize(key))) {
                names.add(key);
            }
        }
        Collections.sort(names);
        return names;
    }

    @Override
    public TriImage read(String name) {
        String value = get(PREFIX + name);
        if (value == null) {
            throw new TrisketteException(TrisketteDevice.IO_ERROR, "browser storage is not available");
        }
        return value.isEmpty() ? null : TriImage.fromBytes(Base64.getDecoder().decode(value));
    }

    @Override
    public void write(String name, TriImage file) {
        int status = set(PREFIX + name, Base64.getEncoder().encodeToString(file.toBytes()));
        if (status != 0) {
            throw new TrisketteException(status, "browser storage refused " + name);
        }
    }

    @Override
    public boolean delete(String name) {
        int status = remove(PREFIX + name);
        if (status == TrisketteDevice.IO_ERROR) {
            throw new TrisketteException(status, "browser storage is not available");
        }
        return status == 0;
    }

    /** The value, "" if there is none, or null if storage can't be used. */
    @JSBody(params = "k", script = "try { var v = localStorage.getItem(k); return v === null ? '' : v; }"
            + " catch (e) { return null; }")
    private static native String get(String k);

    /** 0, or -2 if storage is full, or -4 if it can't be used. */
    @JSBody(params = {"k", "v"}, script = "try { localStorage.setItem(k, v); return 0; }"
            + " catch (e) { return e && (e.name === 'QuotaExceededError' || e.code === 22) ? -2 : -4; }")
    private static native int set(String k, String v);

    /** 0, or -1 if there was no such entry, or -4 if storage can't be used. */
    @JSBody(params = "k", script = "try { if (localStorage.getItem(k) === null) return -1;"
            + " localStorage.removeItem(k); return 0; } catch (e) { return -4; }")
    private static native int remove(String k);

    /** The keys starting with {@code p}, without it, joined by "/" (never part of a name); null if unavailable. */
    @JSBody(params = "p", script = "try { var r = []; for (var i = 0; i < localStorage.length; i++) {"
            + " var k = localStorage.key(i); if (k && k.indexOf(p) === 0) r.push(k.substring(p.length)); }"
            + " return r.join('/'); } catch (e) { return null; }")
    private static native String keys(String p);
}
