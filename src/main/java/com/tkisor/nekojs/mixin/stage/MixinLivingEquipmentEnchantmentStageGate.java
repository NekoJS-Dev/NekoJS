package com.tkisor.nekojs.mixin.stage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.stage.StageDamageActors;
import com.tkisor.nekojs.stage.StageEnchantmentUse;
import com.tkisor.nekojs.stage.StageEquipmentEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEquipmentEnchantmentStageGate {
    @Unique
    private @Nullable StageEquipmentEnchantments nekojs$enchantmentAttributes;

    @WrapOperation(
            method = "collectEquipmentChanges",
            slice = @Slice(from = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V")
    )
    private void nekojs$bindAttributeActor(ItemStack stack, EquipmentSlot slot,
            BiConsumer<Holder<Attribute>, AttributeModifier> consumer, Operation<Void> original) {
        LivingEntity wearer = (LivingEntity) (Object) this;
        Player actor = StageDamageActors.fromEntity(wearer);
        if (actor == null || actor.level().isClientSide()) {
            original.call(stack, slot, consumer);
            return;
        }
        if (nekojs$enchantmentAttributes == null) {
            nekojs$enchantmentAttributes = new StageEquipmentEnchantments();
        }
        nekojs$enchantmentAttributes.collect(wearer, actor, slot, consumer, capture ->
                StageEnchantmentUse.withActor(actor, stack, () -> original.call(stack, slot, capture)));
    }

    @Inject(method = "collectEquipmentChanges", at = @At("RETURN"))
    private void nekojs$refreshEnchantmentAttributes(CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
        if (nekojs$enchantmentAttributes != null) {
            Map<EquipmentSlot, ItemStack> changes = cir.getReturnValue();
            nekojs$enchantmentAttributes.refresh((LivingEntity) (Object) this,
                    changes == null ? List.of() : changes.keySet());
        }
    }
}
