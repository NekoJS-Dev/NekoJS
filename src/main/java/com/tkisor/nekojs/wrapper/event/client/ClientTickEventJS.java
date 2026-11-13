package com.tkisor.nekojs.wrapper.event.client;

/**
 * Loader-neutral payload for the client tick event (shared by tickPre / tickPost; it has no
 * payload members).
 */
public class ClientTickEventJS {

    public static final ClientTickEventJS INSTANCE = new ClientTickEventJS();

    private ClientTickEventJS() {
    }
}
