package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.stage.StageLootDrop;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntityLootStageGate implements StageLootDrop {

    @Unique
    private @Nullable NekoId nekojs$lootTableId;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void nekojs$captureLootTable(Level level, double x, double y, double z, ItemStack stack, CallbackInfo ci) {
        this.nekojs$lootTableId = StageLootDrop.identify(stack);
    }

    @Override
    public @Nullable NekoId nekojs$takeLootTableId() {
        NekoId tableId = this.nekojs$lootTableId;
        this.nekojs$lootTableId = null;
        return tableId;
    }
}
