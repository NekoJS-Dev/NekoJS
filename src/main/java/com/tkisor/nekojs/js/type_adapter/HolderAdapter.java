package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * Input adapter for {@link Holder}: lets vanilla methods that take a {@code Holder}, such as
 * {@code stack.enchant('minecraft:sharpness', 1)}, accept an id string directly.
 *
 * <p>Graal's {@code targetTypeMapping} stops at the raw class, so this adapter never sees the type
 * argument of {@code Holder<Enchantment>} and cannot query the enchantment registry alone. It
 * therefore looks the id up across all registries: a missing namespace defaults to {@code minecraft:};
 * when exactly one registry contains the id, its {@link Holder.Reference} is returned; when several
 * registries match, it reports the ambiguity explicitly and lists them, and the caller should use the
 * binding API of the specific registry instead. This differs from {@code IdentifierAdapter}, which only
 * parses the id: the result here is a registry-bound holder that can be passed to vanilla methods.
 *
 * <p>The 1.21.1 side is a mechanical rename copy of this file (Identifier↔Identifier and similar, see
 * scripts/check-platform-drift).
 */
public final class HolderAdapter extends AbstractJSTypeAdapter<Holder<?>> {

    private static final String EXPECTED = "registry entry id ('minecraft:sharpness', namespace defaults to minecraft)";

    // Lazy init: the static chain in BuiltInRegistries cannot complete on a bare JVM (no FML Loader), and
    // touching it during class loading would make even the pure logic path untestable. The first
    // conversion only happens in game, where FML is always ready.
    private static volatile RegistryAccess registryAccess;

    private static RegistryAccess registryAccess() {
        RegistryAccess access = registryAccess;
        if (access == null) {
            access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            registryAccess = access;
        }
        return access;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<Holder<?>> getTargetClass() {
        return (Class) Holder.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        // No literal union per registry: one raw Holder alias has to cover all registries, and unioning
        // every entry would be thousands of literals. Use a bare string instead: id validity and
        // ambiguity are checked at runtime, and errors carry context.
        return List.of(
                self(),
                string());
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("registry entry id, e.g. 'minecraft:sharpness' (namespace defaults to minecraft)");
    }

    @Override
    protected Holder<?> fromString(String id) {
        String normalized = id.contains(":") ? id : "minecraft:" + id;
//? if >=26 {
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier == null) {
//?} else {
        /*Identifier location = Identifier.tryParse(normalized);
        if (location == null) {
*///?}
            throw new ValueConversionException(Holder.class, EXPECTED, id, "invalid id");
        }
        List<String> registries = new ArrayList<>();
        Holder<?> resolved = null;
//? if >=26 {
        for (ResourceKey<? extends Registry<?>> registryKey : registryAccess().listRegistryKeys().toList()) {
//?} else {
        /*for (ResourceKey<? extends Registry<?>> registryKey : registryAccess().listRegistries().toList()) {
*///?}
            Registry<?> registry = lookup(registryKey);
//? if >=26 {
            if (registry == null || !registry.containsKey(identifier)) continue;
            registries.add(registryKey.identifier().toString());
            if (resolved == null) resolved = holderOf(registry, identifier);
//?} else {
            /*if (registry == null || !registry.containsKey(location)) continue;
            registries.add(registryKey.location().toString());
            if (resolved == null) resolved = holderOf(registry, location);
*///?}
        }
        if (registries.isEmpty()) {
            throw new ValueConversionException(Holder.class, EXPECTED, id,
                    "no registry contains id '" + normalized + "'");
        }
        if (registries.size() > 1) {
            throw new ValueConversionException(Holder.class, EXPECTED, id,
                    "id '" + normalized + "' is ambiguous, present in registries " + registries
                            + "; a Holder parameter cannot pick a registry — use the registry-specific binding API");
        }
        return resolved;
    }

    @Override
    protected Holder<?> fromHostObject(Object host) {
        return host instanceof Holder<?> holder ? holder : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<?> lookup(ResourceKey<? extends Registry<?>> registryKey) {
        // Same raw narrowing as NeoForgeRegistryQueryService.registry: lookup returns
        // Optional<Registry<E>> (E is a captured type), and only the Registry<?> abilities are needed
        //? if >=26 {
        Optional<Registry<?>> result = registryAccess().lookup((ResourceKey) registryKey);
//?} else {
        /*Optional<Registry<?>> result = registryAccess().registry((ResourceKey) registryKey);
*///?}
        return result.orElse(null);
    }

//? if >=26 {
    private static <T> Holder<T> holderOf(Registry<T> registry, Identifier identifier) {
        return registry.get(identifier).orElse(null);
//?} else {
    /*private static <T> Holder<T> holderOf(Registry<T> registry, Identifier location) {
        return registry.getHolder(ResourceKey.create(registry.key(), location)).orElse(null);
*///?}
    }
}
