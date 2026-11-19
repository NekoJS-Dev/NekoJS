package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.stage.StageLootContext;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LootTable.class)
public abstract class MixinLootTableStageGate {

    @ModifyExpressionValue(
            method = "fill",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;")
    )
    private ObjectArrayList<ItemStack> nekojs$filterContainerLoot(
            ObjectArrayList<ItemStack> stacks,
            @Local(argsOnly = true) LootParams params,
            @Local(argsOnly = true) Container container
    ) {
        StageLootContext context = (StageLootContext) (Object) params;
        NekoId tableId = context.nekojs$lootTableId();
        if (tableId != null) {
            StageLootContext.filterStacks(stacks, context.nekojs$lootActor(), tableId,
                    container instanceof Entity entity ? entity : null);
        }
        return stacks;
    }
}
