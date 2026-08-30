// 26.x 基准主干（DEVEX-ROADMAP 档 1 整文件拆分）：内联版本守卫已清零，1.21.1 孪生住在
// versions/1.21.1/src 同名文件（构造性变换）；改本文件行为时须同步孪生文件。
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * 画变种 builder：宽高（16 的倍数，单位像素）、纹理资源 id（指向
 * {@code assets/<ns>/textures/painting/<path>.png}），可选标题 / 作者。
 * <pre>
 * event.paintingVariant('mymod:sea', b =&gt; { b.width = 32; b.height = 16; b.title = '大海' })
 * </pre>
 */
public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant>
        implements TaggableBuilder<PaintingVariantBuilder> {

    public int width = 16;
    public int height = 16;
    /** 纹理资源 id（默认与注册 id 相同）。 */
    public String assetId = null;
    public String title = null;
    public String author = null;

    public PaintingVariantBuilder(Identifier id) {
        super(id);
    }

    /** {@link TaggableBuilder}：画作 tag（如 {@code minecraft:placeable}）归属 PAINTING_VARIANT 注册表。 */
    @Override
    public ResourceKey<? extends Registry<?>> getTagRegistry() {
        return Registries.PAINTING_VARIANT;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    @Override
    public PaintingVariant build() {
        Identifier asset = assetId == null || assetId.isBlank() ? id : Identifier.parse(assetId);
        return new PaintingVariant(width, height, asset,
                Optional.ofNullable(title).map(net.minecraft.network.chat.Component::literal),
                Optional.ofNullable(author).map(net.minecraft.network.chat.Component::literal));
    }
}
