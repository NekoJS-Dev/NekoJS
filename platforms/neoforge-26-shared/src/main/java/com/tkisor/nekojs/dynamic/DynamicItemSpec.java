package com.tkisor.nekojs.dynamic;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DamageResistant;

/**
 * Resolved option set of a dynamically registered item (B2), kept around so the
 * holder's default components can be rebuilt at any time.
 *
 * <p>26.x component model: {@code Item} no longer stores its component map — it
 * delegates to {@code builtInRegistryHolder().components()}. Vanilla binds those
 * only when {@code BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(...)} runs
 * (server start / datapack reload / client join), which will not re-run for an
 * item registered mid-session, so we bind the same map ourselves right after
 * registration and again from the {@code RegistryDataCollectorMixin} after a
 * client-side {@code collectGameRegistries}.
 *
 * <p>The map mirrors exactly what the initializer the {@code Item} constructor
 * registers would build (COMMON_ITEM_COMPONENTS + explicit options + default
 * ITEM_NAME/ITEM_MODEL), so a later vanilla rebuild cannot diverge from it —
 * except {@code DAMAGE_RESISTANT}, whose tag-backed holder set must be re-bound
 * against fresh registries, which is precisely what the reapply path does.
 */
public final class DynamicItemSpec {

    private final Identifier id;
    private final int stackSize;
    private final Rarity rarity;
    private final boolean fireResistant;

    DynamicItemSpec(Identifier id, int stackSize, Rarity rarity, boolean fireResistant) {
        this.id = id;
        this.stackSize = stackSize;
        this.rarity = rarity;
        this.fireResistant = fireResistant;
    }

    public Identifier id() {
        return id;
    }

    /**
     * Builds the default component map for this item, resolving tag-backed
     * components against {@code registries} (a server {@code registryAccess()}
     * or the frozen access returned by {@code collectGameRegistries}).
     */
    public DataComponentMap buildComponents(net.minecraft.core.HolderLookup.Provider registries) {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        builder.addAll(DataComponents.COMMON_ITEM_COMPONENTS);
        builder.set(DataComponents.MAX_STACK_SIZE, stackSize);
        if (rarity != Rarity.COMMON) {
            builder.set(DataComponents.RARITY, rarity);
        }
        if (fireResistant) {
            builder.set(DataComponents.DAMAGE_RESISTANT, createFireResistance(registries));
        }
        // Mirrors Item.Properties#finalizeInitializer defaults:
        // name = "item.<ns>.<path with '/' -> '.'>", model = the item id itself.
        builder.set(DataComponents.ITEM_NAME, Component.translatable(descriptionId()));
        builder.set(DataComponents.ITEM_MODEL, id);
        return builder.build();
    }

    /** Same as vanilla {@code Util.makeDescriptionId("item", id)}. */
    public String descriptionId() {
        return "item." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    private static DamageResistant createFireResistance(net.minecraft.core.HolderLookup.Provider registries) {
        return new DamageResistant(
                registries.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FIRE));
    }
}
