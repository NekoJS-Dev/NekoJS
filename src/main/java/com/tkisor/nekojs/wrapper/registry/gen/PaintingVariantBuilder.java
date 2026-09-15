// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
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

    private int width = 16;
    private int height = 16;
    /** 纹理资源 id（默认与注册 id 相同）。 */
    private String assetId = null;
    private String title = null;
    private String author = null;

    public PaintingVariantBuilder(Identifier id) {
        super(id);
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        requirePositive("width", width);
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        requirePositive("height", height);
        this.height = height;
    }

    /** 纹理资源 id（已归一化：空白视为未声明——build 期本来就走同一分支）。 */
    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId == null || assetId.isBlank() ? null : assetId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    private static void requirePositive(String what, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(what + " must be >= 1 but got " + value);
        }
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
