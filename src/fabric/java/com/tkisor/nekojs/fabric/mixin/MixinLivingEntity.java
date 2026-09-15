package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.LivingEntityExtension;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** {@code LivingEntityExtension} 的 fabric 注入（等价 NeoForge 的 interface injection）。 */
@Mixin(LivingEntity.class)
public interface MixinLivingEntity extends LivingEntityExtension {
}
