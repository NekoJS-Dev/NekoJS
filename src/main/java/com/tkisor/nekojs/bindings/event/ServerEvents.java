package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.EventGroup;
import com.tkisor.nekojs.api.event.EventHandler;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;

public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    EventHandler<ServerTickEventJS> TICK_PRE =
            GROUP.server("tickPre", () -> ServerTickEventJS.class);

    EventHandler<ServerTickEventJS> TICK_POST =
            GROUP.server("tickPost", () -> ServerTickEventJS.class);

    EventHandler<RecipeEventJS> RECIPES = GROUP.server("recipes", () -> RecipeEventJS.class);
}