//~ mc_legacy_api
package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ListIterator;

public interface StageLootContext {

    void nekojs$setLootOrigin(NekoId lootTableId, @Nullable Player actor);

    @Nullable NekoId nekojs$lootTableId();

    @Nullable Player nekojs$lootActor();

    static NekoId tableId(ResourceKey<LootTable> key) {
        var id = key.identifier();
        return NekoId.resource(id.getNamespace(), id.getPath());
    }

    static void filterStacks(
            List<ItemStack> stacks,
            @Nullable Player actor,
            NekoId tableId,
            @Nullable Entity sourceEntity
    ) {
        ListIterator<ItemStack> iterator = stacks.listIterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            ItemStack filtered = StageGateEnforcer.filterLootStack(actor, tableId, sourceEntity, stack);
            if (filtered.isEmpty()) {
                iterator.remove();
            } else if (filtered != stack) {
                iterator.set(filtered);
            }
        }
    }
}
