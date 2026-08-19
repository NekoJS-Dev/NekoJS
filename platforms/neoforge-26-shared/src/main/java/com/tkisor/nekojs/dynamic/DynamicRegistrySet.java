package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import com.tkisor.nekojs.core.dynamic.DynamicRegistrationBookkeeping;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/**
 * One runtime-mutable built-in registry: owns the freeze-bypassing registration
 * path and the {@link DynamicRegistrationBookkeeping} claim/stale ledger for it.
 *
 * <p>Re-claim semantics: an id that already exists is reused only when it was
 * originally registered through this system (reload re-run). Claiming an id that
 * belongs to vanilla or another mod fails fast with a clear message instead of
 * silently transferring ownership.
 */
final class DynamicRegistrySet<T> {

    private final String label;
    private final Registry<T> registry;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final boolean intrusive;
    private final DynamicRegistrationBookkeeping bookkeeping;

    DynamicRegistrySet(
            String label,
            Registry<T> registry,
            ResourceKey<? extends Registry<T>> registryKey,
            boolean intrusive
    ) {
        this.label = label;
        this.registry = registry;
        this.registryKey = registryKey;
        this.intrusive = intrusive;
        this.bookkeeping = new DynamicRegistrationBookkeeping(label);
    }

    String label() {
        return label;
    }

    DynamicRegistrationBookkeeping bookkeeping() {
        return bookkeeping;
    }

    Registry<T> registry() {
        return registry;
    }

    /**
     * Idempotent registration with claim bookkeeping:
     * <ol>
     *   <li>id absent — unfreeze-bypass register a new value</li>
     *   <li>id present and previously ours — reuse the existing value</li>
     *   <li>id present and foreign — fail with an actionable error</li>
     * </ol>
     * Either successful path re-claims the id for {@code ownerScriptId}.
     */
    T ensureRegistered(Identifier id, DynamicRegisterMode mode, String ownerScriptId, Supplier<T> factory) {
        T value;
        if (registry.containsKey(id)) {
            if (!bookkeeping.isTracked(id.toString())) {
                throw new IllegalArgumentException(
                        "Cannot register '" + id + "' in registry '" + label
                                + "': already registered outside DynamicRegistry (vanilla or another mod)");
            }
            value = registry.getValue(id);
        } else {
            value = registerNew(id, factory);
        }
        bookkeeping.claim(id.toString(), ownerScriptId, mode);
        return value;
    }

    private T registerNew(Identifier id, Supplier<T> factory) {
        MappedRegistry<T> mapped = asMapped();
        if (intrusive) {
            return RegistrySurgery.withUnfrozenAndHolders(mapped, () -> {
                T value = factory.get();
                mapped.createIntrusiveHolder(value);
                return Registry.register(registry, id, value);
            });
        }
        return RegistrySurgery.withUnfrozenRegistry(mapped, () -> Registry.register(registry, id, factory.get()));
    }

    /**
     * World-leave cleanup (not wired to any lifecycle event yet): unregisters all
     * WORLD-mode entries we registered and drops their claims. RELOADABLE entries
     * are left alone.
     */
    void clearWorldRegistrations() {
        var ids = bookkeeping.idsByMode(DynamicRegisterMode.WORLD).stream().map(Identifier::parse).toList();
        if (ids.isEmpty()) return;
        RegistrySurgery.unregisterAll(asMapped(), ids, id -> ResourceKey.create(registryKey, id));
        for (Identifier id : ids) {
            bookkeeping.remove(id.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private MappedRegistry<T> asMapped() {
        return (MappedRegistry<T>) registry;
    }
}
