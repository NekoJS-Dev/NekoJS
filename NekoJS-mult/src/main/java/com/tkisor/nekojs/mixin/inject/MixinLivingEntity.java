package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.LivingEntityExtension;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author ZZZank
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements LivingEntityExtension {
}
