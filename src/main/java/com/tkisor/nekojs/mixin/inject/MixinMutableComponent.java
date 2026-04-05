package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.MutableComponentExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * @author ZZZank
 */
@Mixin(MutableComponent.class)
public abstract class MixinMutableComponent implements MutableComponentExtension {

    @Shadow
    @Override
    public abstract MutableComponent setStyle(Style format);

    @Shadow
    @Override
    public abstract MutableComponent withStyle(ChatFormatting format);
}
