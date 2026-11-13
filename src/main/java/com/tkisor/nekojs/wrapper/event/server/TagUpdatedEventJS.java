package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.RegistryAccess;

/**
 * **Loader-neutral** payload of the tag update event ({@code ServerEvents.tagsUpdated}).
 *
 * <p>NeoForge passes its native {@code TagsUpdatedEvent} straight through and never uses this class; on fabric,
 * {@code FabricServerEventBindings} converts it from {@code CommonLifecycleEvents.TAGS_LOADED} (triggered through
 * that implementation's {@code ReloadableServerResourcesMixin} once the server resources, tags included, finish
 * loading, which lines up with the NF timing).
 *
 * <p><b>Semantic difference (recorded)</b>: the native NF event carries an {@code UpdateCause} (server data load,
 * client sync, or static data), while the fabric callback has no cause parameter -- this payload carries fabric's
 * {@code updated} boolean in {@code shouldUpdateStaticData}, and the cause information does not cross platforms.
 * It cannot be cancelled. It fires often (every reload), so listeners should stay light.
 */
@Doc("Fired after tags have been (re)loaded on the server.")
@Getter
public class TagUpdatedEventJS {

    @Doc("The registry access holding the updated tags.")
    private final RegistryAccess registries;

    @Doc("Whether the tag contents actually changed (fabric semantics).")
    private final boolean shouldUpdateStaticData;

    public TagUpdatedEventJS(RegistryAccess registries, boolean shouldUpdateStaticData) {
        this.registries = registries;
        this.shouldUpdateStaticData = shouldUpdateStaticData;
    }
}
