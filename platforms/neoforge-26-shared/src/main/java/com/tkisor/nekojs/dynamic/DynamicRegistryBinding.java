package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;

/**
 * Binding entry for the {@code DynamicRegistry} global (SERVER scripts only).
 *
 * <p>Implements {@link Binding#close(ScriptType)} as the reload boundary — the
 * same blessed hook {@code NativeEventsJS} uses: ScriptManager invokes it for
 * every SERVER binding when a reload starts, before scripts re-run. Marks all
 * dynamic claims stale (stale-retain: nothing is unregistered); registrations
 * that re-run re-claim their ids and clear the flag. Failed reloads leave the
 * stale flags in place until the next successful reload — debug-only fallout.
 */
final class DynamicRegistryBinding implements Binding {

    @Override
    public String name() {
        return "DynamicRegistry";
    }

    @Override
    public Object value() {
        return DynamicRegistryJS.INSTANCE;
    }

    @Override
    public Class<?> valueType() {
        return DynamicRegistryJS.class;
    }

    @Override
    public void close(ScriptType scriptType) {
        if (scriptType == ScriptType.SERVER) {
            DynamicRegistries.beginServerReload();
        }
    }
}
