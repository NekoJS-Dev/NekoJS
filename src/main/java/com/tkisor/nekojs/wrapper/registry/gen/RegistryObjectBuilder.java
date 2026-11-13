package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

/**
 * Base class for builders of registry objects. A setter is the single write path:
 *
 * <ul>
 *   <li><b>Data properties are private fields plus JavaBean setters.</b> On the script side
 *       {@code builder.maxStackSize = 16} (through the putMember path of {@code BuilderSurface})
 *       and an explicit {@code builder.setMaxStackSize(16)} call both hit the same setter, so they
 *       share validation, normalization, definition fingerprint and registry collection. No public
 *       field of the same name may bypass the setter; {@code id} is the read-only exception because
 *       it is the final identity;</li>
 *   <li><b>implements Supplier</b> for lazy cross-references: derived objects in other registries
 *       (BlockItem, fluid block, bucket) refer to each other through {@link #get()}, so creation is
 *       deferred until the platform registration event drains the repository;</li>
 *   <li><b>Additional registration</b>: a subclass overrides
 *       {@link #handleAdditionalObjects(AdditionalObjectRegistry)} when it needs to. The subclass
 *       pre-creates the child builders, suppresses a derived object with a {@code noXxx()} setter
 *       that nulls it out, and the callback runs as one batch while the main object is drained.</li>
 * </ul>
 *
 * @param <T> the object type the registry holds
 */
public abstract class RegistryObjectBuilder<T> implements Supplier<T> {

    /** Object id, including the namespace. */
    public final Identifier id;

    private T built;

    /** Freezes definition state before the repository accepts it. */
    void freezeDefinition() {}

    protected RegistryObjectBuilder(Identifier id) {
        this.id = id;
    }

    /** Builds the target object; the platform registration event calls it during the drain, exactly once. */
    public abstract T build();

    /** {@link Supplier} view: builds on the first call and caches the result; this is where lazy
     * cross-references resolve. */
    @Override
    public final T get() {
        if (built == null) {
            built = build();
        }
        return built;
    }

    /** Additional registration callback, run in one batch in the after-all phase; a builder without
     * derived objects leaves it alone. */
    public void handleAdditionalObjects(AdditionalObjectRegistry registry) {
    }

    /** Target for additional registrations: appends derived objects to <b>other</b> registries. The event
     * repository implements it. */
    public interface AdditionalObjectRegistry {

        /**
         * Adds one derived registration. The target is a {@link net.minecraft.resources.ResourceKey}
         * rather than a live {@link net.minecraft.core.Registry} instance, because the derived entry
         * is delivered during the target registry's <b>own</b> platform pass and the target registry
         * may not exist yet while entries are being collected.
         */
        void additional(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> registry,
                Identifier id, Supplier<?> supplier);
    }
}
