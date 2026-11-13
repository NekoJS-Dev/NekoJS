// Fabric copy of the shared tree's LevelEvents interface, which is guarded file-wide by
// `//? if neoforge` and passes bus calls straight to the native NeoForge event classes. On
// Fabric, FabricLevelEventBindings supplies a subset with neutral payloads. Explosions and saved
// need a mixin (see docs/fabric-port-status.md); this copy grows once they land. The shared tree
// version merges back into a single copy once its event payloads are neutral.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;

/** Level (dimension) event group for server scripts: load, unload and tick (the Fabric subset). */
public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    EventBusJS<LevelEventJS, Void> LOADED =
            GROUP.server("loaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> UNLOADED =
            GROUP.server("unloaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK_POST =
            GROUP.server("tickPost", LevelEventJS.class);
}
