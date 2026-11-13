// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// Its content equals that file evaluated at this node. Keep this file in sync when the 26.x
// side changes behavior.
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;

/**
 * Builds a painting variant on the 1.21.1 API: width and height in pixels (multiples of 16) and a
 * texture resource id pointing at {@code assets/<ns>/textures/painting/<path>.png}. The title and
 * author members of 26.x do not exist on this node; each node freezes its own member list in a
 * golden file.
 * <pre>
 * event.paintingVariant('mymod:sea', b =&gt; { b.width = 32; b.height = 16 })
 * </pre>
 */
public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant> {

    private int width = 16;
    private int height = 16;
    /** Texture resource id; defaults to the registration id. */
    private String assetId = null;

    public PaintingVariantBuilder(ResourceLocation id) {
        super(id);
    }

    // ---- Data properties: explicit setters and JavaBean properties share one write path ----

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

    /** Texture resource id, normalized on write: blank means undeclared, the same branch the build path
     * takes. */
    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId == null || assetId.isBlank() ? null : assetId;
    }

    private static void requirePositive(String what, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(what + " must be >= 1 but got " + value);
        }
    }

    @Override
    public PaintingVariant build() {
        ResourceLocation asset = assetId == null || assetId.isBlank() ? id : ResourceLocation.parse(assetId);
        return new PaintingVariant(width, height, asset);
    }
}
