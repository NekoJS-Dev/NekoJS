package com.tkisor.nekojs.mixin.client;

import com.tkisor.nekojs.client.LegacyStageNotificationClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class StageNotificationFontMixin {
    @Inject(method = "getFontSet", at = @At("HEAD"), cancellable = true)
    private void neko$getStageNotificationFont(ResourceLocation name, CallbackInfoReturnable<FontSet> cir) {
        FontSet font = LegacyStageNotificationClient.findIconFont(name);
        if (font != null) {
            cir.setReturnValue(font);
        }
    }
}
