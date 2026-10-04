//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** 26.1 owner-managed texture upload using the pre-26.2 texture format API. */
final class UiTextureUploaded extends AbstractTexture {
    void upload(Identifier resource, NativeImage image) {
        var device = RenderSystem.getDevice();
        sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
        texture = device.createTexture(resource::toString, 5, TextureFormat.RGBA8,
                image.getWidth(), image.getHeight(), 1, 1);
        textureView = device.createTextureView(texture);
        device.createCommandEncoder().writeToTexture(texture, image);
    }
}
//?}
