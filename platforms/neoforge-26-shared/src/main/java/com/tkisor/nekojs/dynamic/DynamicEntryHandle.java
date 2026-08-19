package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;

/**
 * Handle returned by {@code DynamicRegistry.*} registration calls. Purely a
 * script-facing view over the bookkeeping entry — the registered value itself
 * stays inside the vanilla registry.
 */
@Doc("Handle for a DynamicRegistry registration; the entry itself lives in the vanilla registry.")
public final class DynamicEntryHandle {

    private final String id;
    private final DynamicRegisterMode mode;
    private final String ownerScriptId;
    private final Object value;

    DynamicEntryHandle(String id, DynamicRegisterMode mode, String ownerScriptId, Object value) {
        this.id = id;
        this.mode = mode;
        this.ownerScriptId = ownerScriptId == null || ownerScriptId.isBlank() ? "<unknown>" : ownerScriptId;
        this.value = value;
    }

    @Doc("The registered entry id, e.g. 'mymod:ruby'.")
    @Return("entry id string")
    public String id() {
        return id;
    }

    @Doc("The registration mode: 'world' or 'reloadable'.")
    @Return("mode name string")
    public String mode() {
        return mode.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Doc("The script id that claimed this entry, e.g. 'nekojs:server/packs/foo/main.js'.")
    @Return("owning script id string")
    public String owner() {
        return ownerScriptId;
    }

    /** The registered vanilla object (SoundEvent / MobEffect / Item); Java-side callers only. */
    Object rawValue() {
        return value;
    }

    @Override
    public String toString() {
        return "DynamicEntry[" + id + " mode=" + mode() + " owner=" + ownerScriptId + "]";
    }
}
