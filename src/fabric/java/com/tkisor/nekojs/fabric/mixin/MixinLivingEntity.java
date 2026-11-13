package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.LivingEntityExtension;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Fabric injection of {@code LivingEntityExtension}, equivalent to NeoForge's interface injection. */
@Mixin(LivingEntity.class)
public interface MixinLivingEntity extends LivingEntityExtension {
}
