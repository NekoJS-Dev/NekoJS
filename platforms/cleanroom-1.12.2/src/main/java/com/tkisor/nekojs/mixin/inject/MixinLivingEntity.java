package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.LivingEntityExtension;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link EntityLivingBase} 注入统一扩展方法（health / heal / damage / 装备 / 药水）。
 * <p>{@link EntityLivingBase} 是 MC 类，使用默认 remap（{@code remap = true}）。
 *
 * @author ZZZank
 */
@Mixin(EntityLivingBase.class)
public abstract class MixinLivingEntity implements LivingEntityExtension {
}
