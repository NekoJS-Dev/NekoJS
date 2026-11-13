package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;

/**
 * Duck interface for an entity's persistent data container. On fabric {@code NekoEntityPDataMixin}
 * implements it on {@code Entity}; NeoForge does not need it, because
 * {@code Entity#getPersistentData()} exists natively. The platform bridge uses it so both sides
 * share one implementation of the persistent reads and writes.
 */
public interface NekoEntityPData {

    /** The NekoJS persistent data root of an entity: mutable, writes apply immediately, saved with the entity. */
    CompoundTag neko$getPDataRoot();
}
