package com.tkisor.nekojs.wrapper.registry.base;

import net.minecraft.resources.Identifier;

/**
 *
 *
 * @author ZZZank
 */
public abstract class RegistryObjectBuilder<T> {
    protected final RegistryInfo<T> info;
    public final Identifier id;

    public RegistryObjectBuilder(RegistryInfo<T> info, Identifier id) {
        this.info = info;
        this.id = id;
    }

    public abstract T build();
}
