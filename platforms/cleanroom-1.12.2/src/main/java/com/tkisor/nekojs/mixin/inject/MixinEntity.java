package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.EntityExtension;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link Entity} 基类注入统一扩展方法（id / kill / teleport / getLevel 等）。
 * <p>{@link Entity} 是 MC 类，使用默认 remap（{@code remap = true}）。
 *
 * @author ZZZank
 */
@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtension {
}
