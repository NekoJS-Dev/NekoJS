//~ mc_legacy_api
// This file enables the file-level mc_legacy_api flag: the live 26.x form has no .identifier() and the
// evaluated 1.21.1 form has none either, so the threshold is verified. The variable names
// identifier/location differ only by rename and do not change behavior.
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * Generic adapter backed by a {@link Registry}, for registry types that need <b>only id lookup and no
 * cross-type conversion</b> ({@code MobEffect}, {@code Potion}, {@code SoundEvent} and similar).
 *
 * <p>Constructor: {@code new SimpleRegistryBasedAdapter<>(registry, type, registryName)}.
 * {@code registryName} feeds the probe {@code registry(...)} shape, which generates the
 * {@code $MobEffect_} alias.
 *
 * <p>Accepts a string id (with the {@code minecraft:} prefix added when missing), {@link NekoId},
 * {@link Identifier}, or a host of the target type. It does no cross-type conversion and has no object
 * or array syntax; types that need those (Item/Block conversion, Ingredient prefix dispatch) belong in
 * a hand-written {@link AbstractJSTypeAdapter} subclass rather than this class.
 *
 * @author ZZZank
 */
public class SimpleRegistryBasedAdapter<T> extends AbstractJSTypeAdapter<T> {
    private final Registry<T> registry;
    private final Class<T> targetType;
    private final String registryName;

    public SimpleRegistryBasedAdapter(Registry<T> registry, Class<T> targetType, String registryName) {
        this.registry = registry;
        this.targetType = targetType;
        this.registryName = registryName;
    }

    @Override
    public Class<T> getTargetClass() {
        return targetType;
    }

    Registry<T> sourceRegistry() {
        return registry;
    }

    String registryTypeName() {
        return registryName;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry(registryName),
                host(NekoId.class),
                string());
    }

    @Override
    protected T fromString(String rawId) {
        return getFromRegistry(parseId(rawId));
    }

    @Override
    protected T fromHostObject(Object host) {
        if (host instanceof NekoId(String namespace, String path)) {
            return getFromRegistry(Identifier.fromNamespaceAndPath(namespace, path));
        }
        if (host instanceof Identifier identifier) {
            return getFromRegistry(identifier);
        }
        if (targetType.isInstance(host)) {
            return targetType.cast(host);
        }
        return null; // Not recognized
    }

    private T getFromRegistry(Identifier id) {
        return registry.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(targetType, "registered " + targetType.getSimpleName() + " id",
                id, "no object with id '" + id + "' in registry '" + registry.key().identifier() + "'"));
    }

    private Identifier parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ValueConversionException(targetType, "registry id string", rawId,
                "empty " + targetType.getSimpleName() + " id");
        }
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new ValueConversionException(targetType, "valid registry id", rawId,
                "invalid id syntax: " + rawId);
        }
        return location;
    }
}
