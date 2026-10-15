//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import com.tkisor.nekojs.core.dynamic.DynamicRegistrationBookkeeping;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry storage shared by the dynamic activation adapters and component replay.
 *
 * <p>Lifetime model:
 * <ul>
 *   <li>Entries are registered on first use while a server is running and are
 *       <b>never unregistered mid-session</b> — not by {@code /nekojs reload}
 *       (stale-retain) and not by mode. Real unregistration corrupts numeric ids
 *       and saved stacks (Katton's finding), so both {@link DynamicRegisterMode}
 *       variants currently differ in bookkeeping only.</li>
 *   <li>Successful activation marks omitted claims stale and reclaims the accepted batch.
 *       A rejected candidate or a released script generation never retires claims.</li>
 *   <li>{@link #clearWorldRegistrations()} performs the WORLD-mode unregister
 *       surgery for the future world-leave cleanup. Nothing calls it in this
 *       batch on purpose — wiring it to a lifecycle event is a later batch.</li>
 * </ul>
 *
 * <p>Multiplayer activation is coordinated by the prepare/ack/commit protocol; clients use
 * the same storage through their activation adapter. No script binding mutates it directly.
 */
public final class DynamicRegistries {

    private static final DynamicRegistrySet<SoundEvent> SOUND_EVENTS =
            new DynamicRegistrySet<>("sound_event", BuiltInRegistries.SOUND_EVENT, Registries.SOUND_EVENT, false);
    private static final DynamicRegistrySet<MobEffect> MOB_EFFECTS =
            new DynamicRegistrySet<>("mob_effect", BuiltInRegistries.MOB_EFFECT, Registries.MOB_EFFECT, false);
    private static final DynamicRegistrySet<Item> ITEMS =
            new DynamicRegistrySet<>("item", BuiltInRegistries.ITEM, Registries.ITEM, true);

    /**
     * Component specs of dynamically registered items, for immediate binding and
     * the RegistryDataCollectorMixin reapply. Keyed by id string; written on the
     * script thread during load, read on the client thread during reapply.
     */
    private static final Map<String, DynamicItemSpec> ITEM_SPECS = new ConcurrentHashMap<>();

    private DynamicRegistries() {
    }

    /** Registers (or re-claims) a sound event. */
    public static SoundEvent soundEvent(Identifier id, DynamicRegisterMode mode, String ownerScriptId, Float fixedRange) {
        return SOUND_EVENTS.ensureRegistered(id, mode, ownerScriptId,
                () -> new SoundEvent(id, Optional.ofNullable(fixedRange)));
    }

    /** Registers (or re-claims) a mob effect. */
    public static MobEffect mobEffect(
            Identifier id, DynamicRegisterMode mode, String ownerScriptId, MobEffectCategory category, int color) {
        return MOB_EFFECTS.ensureRegistered(id, mode, ownerScriptId, () -> new MobEffect(category, color) {});
    }

    /**
     * Registers (or re-claims) an item and immediately binds its default holder
     * components (26.x: components live on the holder, and the vanilla rebuild
     * pass will not re-run for mid-session registrations).
     */
    @SuppressWarnings("deprecation")
    public static Item item(
            Identifier id,
            DynamicRegisterMode mode,
            String ownerScriptId,
            HolderLookup.Provider registries,
            int stackSize,
            Rarity rarity,
            boolean fireResistant) {
        DynamicItemSpec spec = new DynamicItemSpec(id, stackSize, rarity, fireResistant);
        Item item = ITEMS.ensureRegistered(id, mode, ownerScriptId, () -> {
            Item.Properties properties = new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, id))
                    .stacksTo(stackSize);
            if (rarity != Rarity.COMMON) {
                properties.rarity(rarity);
            }
            if (fireResistant) {
                properties.fireResistant();
            }
            // The constructor registers the matching component initializer into
            // BuiltInRegistries.DATA_COMPONENT_INITIALIZERS, so any later vanilla
            // rebuild (datapack reload / client join) reproduces the same map.
            return new Item(properties);
        });
        item.builtInRegistryHolder().bindComponents(spec.buildComponents(registries));
        RegistrySurgery.clearHolderTags(item.builtInRegistryHolder());
        ITEM_SPECS.put(id.toString(), spec);
        return item;
    }

    /**
     * Reload boundary: stale-mark every claim in every registry. Entries stay
     * registered; scripts currently re-loading will re-claim their ids.
     */
    public static void beginServerReload() {
        SOUND_EVENTS.bookkeeping().beginReload();
        MOB_EFFECTS.bookkeeping().beginReload();
        ITEMS.bookkeeping().beginReload();
    }

    /** Publishes claim retirement only after the complete activation batch succeeds, including an empty batch. */
    static void publishActivationClaims(List<com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest> requests) {
        beginServerReload();
        for (var request : requests) {
            DynamicRegistrationBookkeeping claims = switch (request.registryKey()) {
                case "minecraft:item" -> ITEMS.bookkeeping();
                case "minecraft:sound_event" -> SOUND_EVENTS.bookkeeping();
                case "minecraft:mob_effect" -> MOB_EFFECTS.bookkeeping();
                default -> throw new AssertionError("[NEKO-4040] Activation claim publication rejected: unsupported registry "
                        + request.registryKey());
            };
            claims.claim(request.id(), request.ownerScriptId(), request.mode());
        }
    }

    /**
     * Future world-leave cleanup (WORLD mode only). Implemented and unit-tested
     * down to the surgery call, but deliberately not wired to any lifecycle
     * event in this batch.
     */
    public static void clearWorldRegistrations() {
        SOUND_EVENTS.clearWorldRegistrations();
        MOB_EFFECTS.clearWorldRegistrations();
        ITEMS.clearWorldRegistrations();
    }

    /**
     * Rollback surgery for the ticket 21 activation adapter: unregisters exactly the
     * entries a degraded activation attempt newly registered (keyed by registry key
     * like {@code minecraft:item}) and drops their claims and item specs. Entries the
     * attempt merely re-claimed keep serving — they were legitimately registered by an
     * earlier batch. Must be called on the transaction owner thread.
     */
    static void rollbackEntries(Map<String, List<Identifier>> newlyRegisteredByRegistryKey, List<?> initializerCheckpoint) {
        RuntimeException failure = null;
        for (Map.Entry<String, List<Identifier>> entry : newlyRegisteredByRegistryKey.entrySet()) {
            try {
                switch (entry.getKey()) {
                    case "minecraft:item" -> {
                        ITEMS.unregisterTrusted(entry.getValue());
                        entry.getValue().forEach(id -> ITEM_SPECS.remove(id.toString()));
                    }
                    case "minecraft:sound_event" -> SOUND_EVENTS.unregisterTrusted(entry.getValue());
                    case "minecraft:mob_effect" -> MOB_EFFECTS.unregisterTrusted(entry.getValue());
                    default -> NekoJS.LOGGER.warn(
                            "DynamicRegistry rollback asked for an unknown registry key '{}'; {} id(s) left untouched",
                            entry.getKey(), entry.getValue().size());
                }
            } catch (RuntimeException rejected) {
                if (failure == null) {
                    failure = rejected;
                } else {
                    failure.addSuppressed(rejected);
                }
            }
        }
        try {
            var itemKeys = newlyRegisteredByRegistryKey.getOrDefault("minecraft:item", List.of()).stream()
                    .map(id -> net.minecraft.resources.ResourceKey.create(Registries.ITEM, id))
                    .collect(java.util.stream.Collectors.toSet());
            RegistrySurgery.rollbackInitializers(BuiltInRegistries.DATA_COMPONENT_INITIALIZERS, initializerCheckpoint, itemKeys);
        } catch (RuntimeException rejected) {
            if (failure == null) failure = rejected;
            else failure.addSuppressed(rejected);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Reapplies default components to all dynamic items against fresh registries.
     * Called from {@code RegistryDataCollectorMixin} at RETURN of
     * {@code collectGameRegistries}: the vanilla component rebuild bound each
     * holder from its initializer, but initializer output cannot carry the
     * tag-backed DAMAGE_RESISTANT holder set bound to the *new* registry access —
     * rebuilding from the spec does.
     */
    @SuppressWarnings("deprecation")
    public static void reapplyDynamicItemComponents(HolderLookup.Provider registries) {
        if (ITEM_SPECS.isEmpty()) return;
        for (DynamicItemSpec spec : ITEM_SPECS.values()) {
            Item item = BuiltInRegistries.ITEM.getValue(spec.id());
            if (item == null) continue;
            try {
                item.builtInRegistryHolder().bindComponents(spec.buildComponents(registries));
            } catch (Exception e) {
                NekoJS.LOGGER.warn("DynamicRegistry: failed to reapply components of item '{}'", spec.id(), e);
            }
        }
    }

    static List<DynamicRegistrationBookkeeping> bookkeepings() {
        return List.of(SOUND_EVENTS.bookkeeping(), MOB_EFFECTS.bookkeeping(), ITEMS.bookkeeping());
    }

    /** Item component specs by id string (debug / test view). */
    static Map<String, DynamicItemSpec> itemSpecs() {
        return new LinkedHashMap<>(ITEM_SPECS);
    }
}
//?}
