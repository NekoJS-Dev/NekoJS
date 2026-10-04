package com.tkisor.nekojs.client.ui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

final class UiTextureUploaded extends AbstractTexture {
    void upload(Identifier resource, NativeImage image) {
        var device = RenderSystem.getDevice();
        sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
        texture = device.createTexture(resource::toString, 5, GpuFormat.RGBA8_UNORM,
                image.getWidth(), image.getHeight(), 1, 1);
        textureView = device.createTextureView(texture);
        device.createCommandEncoder().writeToTexture(texture, image);
    }
}
