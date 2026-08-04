package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.BlockEventExtension;
import net.minecraftforge.event.world.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link BlockEvent} 注入 {@code getLevel()} alias（映射 {@code getWorld()}）。
 * <p>{@code remap = false}：BlockEvent 是 Forge 自有类。
 */
@Mixin(value = BlockEvent.class, remap = false)
public abstract class MixinBlockEvent implements BlockEventExtension {
}
