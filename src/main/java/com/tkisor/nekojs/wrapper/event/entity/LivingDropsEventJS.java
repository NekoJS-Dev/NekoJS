package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Loader-neutral payload for the living entity death drops event ({@code EntityEvents.drops}).
 *
 * <p>The meaning follows NeoForge {@code LivingDropsEvent}. In 26.1.2 its getDrops returns
 * {@code Collection<ItemEntity>}, while this payload exposes {@link #drops} as
 * {@code List<ItemStack>}, because script operations such as adding items or
 * {@code drops.clear()} are more common on a {@code List<ItemStack>}. On fabric,
 * {@code FabricEntityEventBindingsV2} + {@code MixinLivingEntityDeathDrops} convert the
 * {@code LivingEntity#dropAllDeathLoot} entry (fabric-api's
 * {@code ServerLivingEntityEvents} has no drop callback, as javap shows).
 *
 * <p><b>Known difference on fabric</b>: the native drop logic (equipment, death loot table,
 * experience orbs) generates each item inside {@code dropAllDeathLoot} and cannot be handed to
 * a script as a collection, so {@code drops} is always an empty list when a listener receives
 * the event. The listener can push its own items. Cancel semantics: a listener
 * {@code return true} skips the vanilla drops (the NeoForge setCanceled equivalent, which
 * clears the drops); the bridge then spawns the items the script pushed into {@code drops}
 * through {@code LivingEntity#spawnAtLocation}.
 *
 * <p>Data difference: on NeoForge a script can read the vanilla drops (the generated
 * ItemEntity collection). fabric has no such data, only the entity, the source and the custom
 * drops provided after cancellation.
 */
@Doc("Fired when a living entity's death drops are generated (EntityEvents.drops). Dispatched by entity type.")
@Doc("Cancellable on fabric: return true to skip vanilla death drops (script-provided event.drops are then spawned).")
@Getter
public class LivingDropsEventJS {

    @Doc("The dying entity.")
    private final LivingEntity entity;

    @Doc("The damage source of the death.")
    private final DamageSource source;

    /**
     * The death drops. Empty at the moment of the event on fabric; a listener may push items, and
     * they are spawned when the event is cancelled.
     */
    @Doc("The drops. Empty when fired on fabric (vanilla generates them internally); push items here and cancel the event to spawn them instead.")
    private final List<ItemStack> drops;

    public LivingDropsEventJS(LivingEntity entity, DamageSource source, List<ItemStack> drops) {
        this.entity = entity;
        this.source = source;
        this.drops = new ArrayList<>(drops);
    }
}
