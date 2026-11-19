package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.tkisor.nekojs.stage.StageLootContext;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.class)
public abstract class MixinBlockLootStageGate {

    @ModifyExpressionValue(
            method = "getDrops",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;")
    )
    private ObjectArrayList<ItemStack> nekojs$filterBlockLoot(
            ObjectArrayList<ItemStack> stacks,
            @Local(argsOnly = true) LootParams.Builder params
    ) {
        ResourceKey<LootTable> key = ((BlockBehaviour) (Object) this).getLootTable();
        Entity breaker = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        StageLootContext.filterStacks(stacks, breaker instanceof Player player ? player : null,
                StageLootContext.tableId(key), null);
        return stacks;
    }
}
