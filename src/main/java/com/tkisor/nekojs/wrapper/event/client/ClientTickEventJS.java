package com.tkisor.nekojs.wrapper.event.client;

import com.tkisor.nekojs.api.annotation.HideFromJS;

/**
 * Loader-neutral payload for the client tick event (shared by tickPre / tickPost; it has no
 * payload members).
 */
public class ClientTickEventJS {

    public static final ClientTickEventJS INSTANCE = new ClientTickEventJS();

    @HideFromJS
    public static final ClientTickEventJS STABLE_PHASE = new ClientTickEventJS();

    private ClientTickEventJS() {
    }
}
