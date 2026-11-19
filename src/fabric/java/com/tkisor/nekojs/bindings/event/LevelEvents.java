// Fabric copy of LevelEvents (the shared tree's copy is guarded by `//? if neoforge`).
// On Fabric, FabricLevelEventBindings supplies load, unload, and tick events with neutral payloads.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableLevelEvent;
import com.tkisor.nekojs.fabric.event.FabricLevelEventBindings;
import com.tkisor.nekojs.fabric.event.FabricStableEventProjections;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;

/**
 * Level (dimension) event group for server scripts (Fabric subset): load, unload, and tick.
 */
@Doc("Level (dimension) event group for server scripts: level load, unload, and tick.")
public interface LevelEvents {
    /** Event group published as {@code LevelEvents}. */
    EventGroup GROUP = EventGroup.of("LevelEvents");

    /** Fired on the server when a level loads. */
    @Doc("Fired when a server level is loaded. Only visible in server_scripts.")
    EventBusJS<LevelEventJS, Void> LOADED =
            GROUP.server("loaded", LevelEventJS.class, api -> {
                api.versioned().when(event -> !FabricLevelEventBindings.isReadyOverworld(event));
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel())
                                && FabricLevelEventBindings.isStableLoad(event))
                        .view(StableLevelEvent.class, FabricStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired on the server when a level unloads. */
    @Doc("Fired when a server level is unloaded. Only visible in server_scripts.")
    EventBusJS<LevelEventJS, Void> UNLOADED =
            GROUP.server("unloaded", LevelEventJS.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel()))
                        .view(StableLevelEvent.class, FabricStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired before each server level tick. */
    @Doc("Fired before each server level tick. Only visible in server_scripts.")
    EventBusJS<LevelEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", LevelEventJS.class, api -> {
                api.versioned().when(event -> !FabricLevelEventBindings.isStableTick(event));
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel())
                                && FabricLevelEventBindings.isStableTick(event))
                        .view(StableLevelEvent.class, FabricStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired after each server level tick. */
    @Doc("Fired after each server level tick. Only visible in server_scripts.")
    EventBusJS<LevelEventJS, Void> TICK_POST =
            GROUP.server("tickPost", LevelEventJS.class, api -> {
                api.versioned().when(event -> !FabricLevelEventBindings.isStableTick(event));
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getLevel())
                                && FabricLevelEventBindings.isStableTick(event))
                        .view(StableLevelEvent.class, FabricStableEventProjections::levelEvent)
                        .scriptTypes(ScriptType.SERVER);
            });
}
