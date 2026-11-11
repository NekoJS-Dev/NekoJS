package dev.latvian.mods.kubejs.plugin;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;

/** Only the event-registration hook is admitted by this test-only ABI surface. */
public interface KubeJSPlugin {
    void registerEvents(EventGroupRegistry registry);
}
