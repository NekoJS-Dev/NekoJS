package com.tkisor.nekojs.wrapper.registry.base.plugin;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.wrapper.registry.base.RegistryObjectTypeRegistry;
import com.tkisor.nekojs.wrapper.registry.base.impl.ItemBuilderJS;
import net.minecraft.core.registries.Registries;

/**
 * @author ZZZank
 */
@RegisterNekoJSPlugin
public class BuiltinRegistrySupport implements RegistrySupportPlugin {

    @Override
    public void registerRegistryInfo(RegistryInfosRegistry registry) {
        registry.addClassesToScan(Registries.class);
    }

    @Override
    public void registerRegistryObjectType(RegistryObjectTypeRegistry registry) {
        try (var scope = registry.scope(Registries.ITEM)) {
            scope.register("basic", ItemBuilderJS::new);
        }
    }
}
