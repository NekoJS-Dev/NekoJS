package com.tkisor.nekojs.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * @author ZZZank
 */
public class RegistryInfos {
    private final Map<Identifier, RegistryInfo<?>> infos = new HashMap<>();

    /// @param classesToScan a list of classes, whose element will have its fields scanned and look for public static
    /// final [ResourceKey] fields that represents a [Registry]. The [net.minecraft.core.registries.Registries] class
    /// is a good example.
    /// @param additionalInfos additional [RegistryInfo]s to be registered, usually from manual registration
    public RegistryInfos(List<Class<?>> classesToScan, Collection<RegistryInfo<?>> additionalInfos) {
        for (var c : classesToScan) {
            for (var info : scanFromClass(c)) {
                infos.put(info.resourceKey().identifier(), info);
            }
        }
        for (var info : additionalInfos) {
            infos.put(info.resourceKey().identifier(), info);
        }
    }

    public Map<Identifier, RegistryInfo<?>> view() {
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
                // ResourceKey
                && field.getType() == ResourceKey.class
                // ResourceKey<?>
                && field.getGenericType() instanceof ParameterizedType parameterizedKey
                // ResourceKey<A<B>>
                && parameterizedKey.getActualTypeArguments()[0] instanceof ParameterizedType parameterizedReg
                // ResourceKey<Registry<?>>
                && parameterizedReg.getRawType() == Registry.class
            ) {
                // the ? in ResourceKey<Registry<?>>
                var rawKeyType = parameterizedReg.getActualTypeArguments()[0];

                // there multiple possibilities of Type:
                // - Class: Block, String[], List
                // - GenericArrayType: T[]
                // - ParameterizedType: Codec<? extends Number>
                // - TypeVariable: T, K
                // - WildcardType: ?, ? extends String
                // Only Class and ParameterizedType makes sense for registry key
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
                } catch (Exception _) {
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
