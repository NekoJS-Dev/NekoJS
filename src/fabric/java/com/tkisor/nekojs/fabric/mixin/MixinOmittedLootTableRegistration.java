package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricServerEventBindings.LootTableCancellation;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.Validatable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReloadableServerRegistries.class)
public abstract class MixinOmittedLootTableRegistration {
    @Inject(method = "lambda$scheduleRegistryLoad$1(Lnet/minecraft/core/WritableRegistry;Lnet/minecraft/world/level/storage/loot/LootDataType;Lnet/minecraft/resources/Identifier;Lnet/minecraft/world/level/storage/loot/Validatable;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void nekojs$omitCancelledLootTable(WritableRegistry<?> registry, LootDataType<?> dataType,
                                                      Identifier id, Validatable value, CallbackInfo ci) {
        if (LootTableCancellation.isOmitted(value)) ci.cancel();
    }
}
