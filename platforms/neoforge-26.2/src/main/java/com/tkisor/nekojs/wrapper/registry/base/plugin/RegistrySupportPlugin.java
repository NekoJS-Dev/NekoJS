package com.tkisor.nekojs.wrapper.registry.base.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.wrapper.registry.base.RegistryObjectTypeRegistry;

/**
 * @author ZZZank
 */
public interface RegistrySupportPlugin extends NekoJSPlugin {

    default void registerRegistryInfo(RegistryInfosRegistry registry) {
    }

    default void registerRegistryObjectType(RegistryObjectTypeRegistry registry) {
    }
}
