// 26.x implementation. No version guards belong in this file. The 1.21.1 implementation is the file
// with the same name under versions/1.21.1/src; whenever you change behavior here, change that file too.
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Painting variant builder: width and height in pixels, a multiple of 16, and a texture resource id
 * pointing at {@code assets/<ns>/textures/painting/<path>.png}, plus an optional title and author.
 * <pre>
 * event.paintingVariant('mymod:sea', b =&gt; { b.width = 32; b.height = 16; b.title = 'Ocean' })
 * </pre>
 */
public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant>
        implements TaggableBuilder<PaintingVariantBuilder> {

    private int width = 16;
    private int height = 16;
    /** Texture resource id. Defaults to the registration id. */
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

    /** Texture resource id, already normalized: a blank value counts as not set, which takes the same
     * branch at build time. */
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

    /** {@link TaggableBuilder}: painting tags (such as {@code minecraft:placeable}) belong to the
     * PAINTING_VARIANT registry. */
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
