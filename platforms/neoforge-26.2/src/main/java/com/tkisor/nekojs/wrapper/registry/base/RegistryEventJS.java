package com.tkisor.nekojs.wrapper.registry.base;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author ZZZank
 */
public class RegistryEventJS<T> {
    private final RegistryInfo<T> info;
    private final Map<String, RegistryObjectType<T>> types;
    private final Map<Identifier, Supplier<T>> providers = new LinkedHashMap<>();

    public RegistryEventJS(
        RegistryInfo<T> info,
        Map<String, RegistryObjectType<T>> types
    ) {
        this.info = info;
        this.types = types;
    }

    public RegistryObjectBuilder<T> custom(String id, String type) {
        var objectType = Objects.requireNonNull(types.get(type), "No registry object type matching " + type);
        var builder = objectType.createBuilder(createNewId(id));
        providers.put(builder.id, builder::build);
        return builder;
    }

    public RegistryObjectBuilder<T> custom(String id, String type, Consumer<RegistryObjectBuilder<T>> modifner) {
        var builder = custom(id, type);
        modifner.accept(builder);
        return builder;
    }

    private @NonNull Identifier createNewId(String id) {
        var identifier = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath(NekoJS.MODID, id);
        if (providers.containsKey(identifier)) {
            throw new IllegalArgumentException("id already present: " + identifier);
        }
        return identifier;
    }

    public void register(String id, Supplier<T> provider) {
        var identifier = createNewId(id);
        providers.put(identifier, provider);
    }

    public Map<Identifier, Supplier<T>> viewProviders() {
        return Collections.unmodifiableMap(providers);
    }
}
