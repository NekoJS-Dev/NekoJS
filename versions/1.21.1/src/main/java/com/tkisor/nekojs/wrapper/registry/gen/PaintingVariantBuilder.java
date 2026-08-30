// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;

/**
 * 画变种 builder：宽高（16 的倍数，单位像素）、纹理资源 id（指向
 * {@code assets/<ns>/textures/painting/<path>.png}），可选标题 / 作者。
 * <pre>
 * event.paintingVariant('mymod:sea', b =&gt; { b.width = 32; b.height = 16; b.title = '大海' })
 * </pre>
 */
public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant> {

    public int width = 16;
    public int height = 16;
    /** 纹理资源 id（默认与注册 id 相同）。 */
    public String assetId = null;

    public PaintingVariantBuilder(ResourceLocation id) {
        super(id);
    }

    @Override
    public PaintingVariant build() {
        ResourceLocation asset = assetId == null || assetId.isBlank() ? id : ResourceLocation.parse(assetId);
        return new PaintingVariant(width, height, asset);
    }
}
