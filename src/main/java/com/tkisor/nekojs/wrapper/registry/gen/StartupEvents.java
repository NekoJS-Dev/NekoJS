package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Versioned STARTUP event group for registration into explicitly opened static registries. */
public final class StartupEvents {

    public static final EventGroup GROUP = EventGroup.of("StartupEvents");

    public static final EventBusJS<RegistryEventJS, String> REGISTRY = GROUP.startup(
            "registry", RegistryEventJS.class, DispatchKey.of(String.class, RegistryEventJS::registryName),
            api -> api.versioned().requiredSelector(String.class, StartupEvents::normalizeSelector));

    private StartupEvents() {}

    private static String normalizeSelector(String selector) {
        return normalizeSelector(selector, NekoRegistryPointsPlugin.registryInfos());
    }

    static String normalizeSelector(String selector, RegistryInfosPoint.RegistryInfos infos) {
        Identifier id = selector == null || selector.isEmpty() || selector.startsWith(":") || selector.endsWith(":")
                ? null : Identifier.tryParse(selector);
        if (id == null) {
            throw new IllegalArgumentException("[NEKO-4074] Invalid STARTUP registry selector '" + selector + "'");
        }
        if (!infos.startupRegistries().contains(ResourceKey.createRegistryKey(id))) {
            throw new IllegalArgumentException("[NEKO-4074] Registry '" + id + "' is not open for STARTUP registration");
        }
        return id.toString();
    }
}
