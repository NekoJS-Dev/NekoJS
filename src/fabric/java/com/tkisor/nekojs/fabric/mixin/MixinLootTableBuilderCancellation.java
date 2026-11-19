package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricServerEventBindings.LootTableCancellation;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.Builder.class)
public abstract class MixinLootTableBuilderCancellation implements LootTableCancellation {
    @Unique
    private boolean nekojs$omitted;

    @Override
    public void nekojs$omitLootTable() {
        nekojs$omitted = true;
    }

    @Inject(method = "build", at = @At("HEAD"), cancellable = true)
    private void nekojs$buildCancelledLootTable(CallbackInfoReturnable<LootTable> cir) {
        if (nekojs$omitted) cir.setReturnValue(LootTableCancellation.omittedTable());
    }
}
