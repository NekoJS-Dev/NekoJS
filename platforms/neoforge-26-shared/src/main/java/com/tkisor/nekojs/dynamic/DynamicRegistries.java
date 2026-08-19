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
 * Static runtime-registry state for the {@code DynamicRegistry} binding (B1:
 * sound events + mob effects; B2: items).
 *
 * <p>Lifetime model (v1, honest):
 * <ul>
 *   <li>Entries are registered on first use while a server is running and are
 *       <b>never unregistered mid-session</b> — not by {@code /nekojs reload}
 *       (stale-retain) and not by mode. Real unregistration corrupts numeric ids
 *       and saved stacks (Katton's finding), so both {@link DynamicRegisterMode}
 *       variants currently differ in bookkeeping only.</li>
 *   <li>{@link #beginServerReload()} runs when SERVER scripts start (re)loading
 *       (binding close, i.e. the same blessed reload hook NativeEventsJS uses).
 *       All claims become stale; scripts that re-run re-claim by id.</li>
 *   <li>{@link #clearWorldRegistrations()} performs the WORLD-mode unregister
 *       surgery for the future world-leave cleanup. Nothing calls it in this
 *       batch on purpose — wiring it to a lifecycle event is a later batch.</li>
 * </ul>
 *
 * <p>Client note: on a dedicated/integrated server the entries exist server-side
 * only. Singleplayer works because both sides share the JVM registries; on a
 * multiplayer client the remote server's dynamic entries are NOT synced (future
 * work) — clients must run the same scripts locally.
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
