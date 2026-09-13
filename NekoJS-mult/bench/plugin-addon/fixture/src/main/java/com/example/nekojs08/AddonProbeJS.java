package com.example.nekojs08;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Script-visible helper bound by the test-only external addon (see {@link AddonPlugin}).
 *
 * <p>Only depends on the platform fat jar's public surface plus slf4j/graal, i.e. the same
 * dependency face a future third-party NekoJS addon has. It deliberately imports nothing from
 * {@code com.tkisor.nekojs.core.*} or any test seam.
 */
public final class AddonProbeJS {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Addon08");

    /** Marker prefix emitted to the game log; the runtime smoke greps for it. */
    public static final String MARKER = "NEKOJS-ADDON08";

    /** Called from a script: proves the addon's frozen binding product reached the script side. */
    public String hit(String tag) {
        String line = MARKER + " binding-consumed tag=" + tag;
        LOGGER.info(line);
        return line;
    }

    /** Called from a script through the addon's own node module. */
    public static String moduleMarker(String tag) {
        return MARKER + " module-consumed tag=" + tag;
    }
}
