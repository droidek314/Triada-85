package org.triada.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;

/** The web page's entry point (M12): starts {@link WebApp} in the page's canvas. */
public final class WebLauncher {

    private WebLauncher() {
    }

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        config.width = -1;       // the canvas keeps the size the page gives it (index.html)
        config.height = -1;
        new WebApplication(new WebApp(), config);
    }
}
