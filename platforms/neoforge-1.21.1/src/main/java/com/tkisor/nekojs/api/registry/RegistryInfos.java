package com.tkisor.nekojs.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ZZZank
 */
public class RegistryInfos {
    private final Map<ResourceLocation, RegistryInfo<?>> infos = new HashMap<>();

    /// @param classesToScan a list of classes, whose element will have its fields scanned and look for public static
    /// final [ResourceKey] fields that represents a [Registry]. The [net.minecraft.core.registries.Registries] class
    /// is a good example.
    /// @param additionalInfos additional [RegistryInfo]s to be registered, usually from manual registration
    public RegistryInfos(List<Class<?>> classesToScan, Collection<RegistryInfo<?>> additionalInfos) {
        for (var c : classesToScan) {
            for (var info : scanFromClass(c)) {
                infos.put(info.resourceKey().location(), info);
            }
        }
        for (var info : additionalInfos) {
            infos.put(info.resourceKey().location(), info);
        }
    }

    public Map<ResourceLocation, RegistryInfo<?>> view() {
        return Collections.unmodifiableMap(infos);
    }

    // example:
    // public static final ResourceKey<Registry<Block>> BLOCK = createRegistryKey("block");
    private ArrayList<RegistryInfo<?>> scanFromClass(Class<?> c) {
        var result = new ArrayList<RegistryInfo<?>>();
        for (var field : c.getDeclaredFields()) {
            var modifiers = field.getModifiers();
            if (
                Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && field.getType() == ResourceKey.class
                    && field.getGenericType() instanceof ParameterizedType parameterizedKey
                    && parameterizedKey.getActualTypeArguments()[0] instanceof ParameterizedType parameterizedReg
                    && parameterizedReg.getRawType() == Registry.class
            ) {
                var rawKeyType = parameterizedReg.getActualTypeArguments()[0];

                Class<?> keyType;
                if (rawKeyType instanceof Class<?> keyTypeClass) {
                    keyType = keyTypeClass;
                } else if (rawKeyType instanceof ParameterizedType keyTypeParameterized) {
                    keyType = (Class<?>) keyTypeParameterized.getRawType();
                } else {
                    continue;
                }

                ResourceKey<?> key;
                try {
                    key = (ResourceKey<?>) field.get(null);
                } catch (Exception ex) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                var built = new RegistryInfo<>((Class<Object>) keyType, rawKeyType, (ResourceKey<Registry<Object>>) key);
                result.add(built);
            }
        }

        return result;
    }
}
