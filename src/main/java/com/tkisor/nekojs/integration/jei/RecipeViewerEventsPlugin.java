package com.tkisor.nekojs.integration.jei;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.RecipeViewerEvents;

/**
 * Registers the {@link RecipeViewerEvents} group only when the client runs and JEI is
 * present, so scripts only see {@code RecipeViewerEvents} when it can actually fire. The
 * events are posted by {@code RecipeViewerJeiPlugin} (the JEI-side plugin) when the JEI
 * runtime rebuilds.
 *
 * <p>This class references no JEI type, so it loads safely without JEI; {@code requiredMods}
 * filters it out then, the group stays unregistered, and scripts never see an event group
 * that would never fire.
 */
@RegisterNekoJSPlugin(clientOnly = true, requiredMods = "jei")
public class RecipeViewerEventsPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.EventsPoint.Contributor, com.tkisor.nekojs.core.plugin.ClientEventsPoint.Contributor {

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        registry.register(RecipeViewerEvents.GROUP);
    }
}
