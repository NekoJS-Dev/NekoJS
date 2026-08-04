package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.ItemExtension;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 1.12.2 {@link Item} 注入 {@code getId()} 扩展方法。
 * <p>{@link Item} 是 MC 类，使用默认 remap（{@code remap = true}）。
 *
 * @author ZZZank
 */
@Mixin(Item.class)
public abstract class MixinItem implements ItemExtension {
}
