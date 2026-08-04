package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.EventExtension;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 NeoForge {@link Event} 基类注入统一扩展方法（{@code cancel} / {@code isCancelled}）。
 * <p>用 {@code remap = false}：Event 是 NeoForge 自有类，不在 MC 混淆命名空间。
 */
@Mixin(value = Event.class, remap = false)
public abstract class MixinEvent implements EventExtension {
}
