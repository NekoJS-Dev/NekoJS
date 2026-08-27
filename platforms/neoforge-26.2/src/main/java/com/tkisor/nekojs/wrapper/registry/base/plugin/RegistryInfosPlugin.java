package com.tkisor.nekojs.wrapper.registry.base.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionPoint;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionProvider;
import com.tkisor.nekojs.core.plugin.NekoPluginExtensionRegistry;
import com.tkisor.nekojs.wrapper.registry.base.RegistryInfos;
import com.tkisor.nekojs.wrapper.registry.base.RegistryObjectTypeRegistry;
import com.tkisor.nekojs.wrapper.registry.base.RegistryObjectTypes;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.apache.logging.log4j.util.Lazy;

import java.util.HashMap;
import java.util.Map;

/**
 * 注册 RegistryInfos 扩展点的插件。
 *
 * @author ZZZank
 */
@RegisterNekoJSPlugin
public class RegistryInfosPlugin implements NekoPluginExtensionProvider, NekoJSPlugin {
    private RegistryInfosRegistry.Impl infosRegistry = new RegistryInfosRegistry.Impl();
    private RegistryObjectTypeRegistry.Impl typesRegistry;

    @Override
    public void registerPluginExtensionPoints(NekoPluginExtensionRegistry registry) {

        registry.register(NekoPluginExtensionPoint.of(
            "nekojs:registry_infos",
            RegistrySupportPlugin.class,
            (plugin, cx) -> plugin.registerRegistryInfo(infosRegistry),
            () -> {
                RegistryInfos.setInstance(infosRegistry.build());
                infosRegistry = null;
                typesRegistry = new RegistryObjectTypeRegistry.Impl(RegistryInfos.getInstance());
            }
        ));
        registry.register(NekoPluginExtensionPoint.of(
            "nekojs:registry_object_types",
            RegistrySupportPlugin.class,
            (plugin, cx) -> plugin.registerRegistryObjectType(typesRegistry),
            () -> {
                var registryInfos = typesRegistry.registryInfos;

                var map = new HashMap<ResourceKey<Registry<?>>, RegistryObjectTypes<?>>();
                for (var entry : typesRegistry.view().entrySet()) {
                    ResourceKey<Registry<?>> key = entry.getKey();
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    var types = new RegistryObjectTypes.Impl(registryInfos.get((ResourceKey) key), Map.copyOf(entry.getValue()));
                    map.put(key, types);
                }

                RegistryObjectTypes.Internal.REGISTERED = Map.copyOf(map);
            }
        ));
    }
}
