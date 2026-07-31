package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;

/**
 * 画变种注册器（{@code StartupEvents.registry('paintingVariant')}）。
 *
 * <p>脚本指定宽高（16 的倍数，单位像素）、纹理资源 id（指向
 * {@code assets/<ns>/textures/painting/<path>.png}）。
 * 1.21.1 的 {@code PaintingVariant} 不含 title/author 字段（26.x 才加入），
 * 故本平台版不暴露它们。
 */
public class PaintingVariantBuilderJS {
    @Getter
    private final ResourceLocation location;

    private int width = 16;
    private int height = 16;
    private ResourceLocation assetId;

    public PaintingVariantBuilderJS(ResourceLocation location) {
        this.location = location;
        this.assetId = location;
    }

    public PaintingVariantBuilderJS width(int width) { this.width = width; return this; }
    public PaintingVariantBuilderJS height(int height) { this.height = height; return this; }

    /** 纹理资源 id（默认与注册 id 相同）。 */
    public PaintingVariantBuilderJS assetId(ResourceLocation assetId) { this.assetId = assetId; return this; }

    public PaintingVariant create() {
        // 1.21.1: PaintingVariant(int width, int height, ResourceLocation assetId)
        return new PaintingVariant(width, height, assetId);
    }
}
