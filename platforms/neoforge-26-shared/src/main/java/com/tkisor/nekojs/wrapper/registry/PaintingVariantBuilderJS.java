package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

import java.util.Optional;

/**
 * 画变种注册器（{@code StartupEvents.registry('paintingVariant')}）。
 *
 * <p>脚本指定宽高（16 的倍数，单位像素）、纹理资源 id（指向
 * {@code assets/<ns>/textures/painting/<path>.png}），可选标题 / 作者。
 */
public class PaintingVariantBuilderJS {
    @Getter
    private final Identifier location;

    private int width = 16;
    private int height = 16;
    private Identifier assetId;
    private Component title;
    private Component author;

    public PaintingVariantBuilderJS(Identifier location) {
        this.location = location;
        this.assetId = location;
    }

    public PaintingVariantBuilderJS width(int width) { this.width = width; return this; }
    public PaintingVariantBuilderJS height(int height) { this.height = height; return this; }

    /** 纹理资源 id（默认与注册 id 相同）。 */
    public PaintingVariantBuilderJS assetId(Identifier assetId) { this.assetId = assetId; return this; }
    public PaintingVariantBuilderJS title(String title) { this.title = Component.literal(title); return this; }
    public PaintingVariantBuilderJS author(String author) { this.author = Component.literal(author); return this; }

    public PaintingVariant create() {
        return new PaintingVariant(width, height, assetId, Optional.ofNullable(title), Optional.ofNullable(author));
    }
}
