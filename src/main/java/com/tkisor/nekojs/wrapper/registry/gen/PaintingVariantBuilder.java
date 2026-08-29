package com.tkisor.nekojs.wrapper.registry.gen;

//? if >=26 {
import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import java.util.Optional;
//?}
import net.minecraft.resources.Identifier;
//? if <26 {
/*import net.minecraft.world.entity.decoration.PaintingVariant;
*///?}

/**
 * 画变种 builder：宽高（16 的倍数，单位像素）、纹理资源 id（指向
 * {@code assets/<ns>/textures/painting/<path>.png}），可选标题 / 作者。
 * <pre>
 * event.paintingVariant('mymod:sea', b =&gt; { b.width = 32; b.height = 16; b.title = '大海' })
 * </pre>
 */
//? if >=26 {
public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant>
        implements TaggableBuilder<PaintingVariantBuilder> {
//?} else {
/*public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant> {
*///?}

    public int width = 16;
    public int height = 16;
    /** 纹理资源 id（默认与注册 id 相同）。 */
    public String assetId = null;
//? if >=26 {
    public String title = null;
    public String author = null;
//?}

    public PaintingVariantBuilder(Identifier id) {
        super(id);
//? if >=26 {
    }

    /** {@link TaggableBuilder}：画作 tag（如 {@code minecraft:placeable}）归属 PAINTING_VARIANT 注册表。 */
    @Override
    public ResourceKey<? extends Registry<?>> getTagRegistry() {
        return Registries.PAINTING_VARIANT;
    }

    @Override
    public Identifier getLocation() {
        return id;
//?}
    }

    @Override
    public PaintingVariant build() {
        Identifier asset = assetId == null || assetId.isBlank() ? id : Identifier.parse(assetId);
//? if >=26 {
        return new PaintingVariant(width, height, asset,
                Optional.ofNullable(title).map(net.minecraft.network.chat.Component::literal),
                Optional.ofNullable(author).map(net.minecraft.network.chat.Component::literal));
//?} else {
/*        return new PaintingVariant(width, height, asset);
*///?}
    }
}
