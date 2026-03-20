package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.event.server.ServerTickEventJS;

public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    EventBusJS<ServerTickEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", ServerTickEventJS.class);

    EventBusJS<ServerTickEventJS, Void> TICK_POST =
            GROUP.server("tickPost", ServerTickEventJS.class);

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
}