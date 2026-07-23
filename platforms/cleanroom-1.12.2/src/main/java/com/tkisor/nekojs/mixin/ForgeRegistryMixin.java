package com.tkisor.nekojs.mixin;

import com.google.common.collect.BiMap;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.12.2 ForgeRegistryMixin - provides safe registry entry removal by
 * directly manipulating the ForgeRegistry BiMaps.
 *
 * <p>Pattern adapted from GroovyScript's ForgeRegistryMixin.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin {

    @Shadow(remap = false)
    @Final
    private BiMap names;

    @Shadow(remap = false)
    @Final
    private BiMap ids;

    @Shadow(remap = false)
    @Final
    private BiMap owners;

    /**
     * Remove an entry from the ForgeRegistry's internal BiMaps.
     * Called by NekoJS recipe system to remove recipes at runtime.
     */
    public void nekojs$removeEntry(Object name) {
        Object entry = this.names.remove(name);
        if (entry == null) return;
        this.ids.inverse().remove(entry);
        this.owners.inverse().remove(entry);
    }
}
