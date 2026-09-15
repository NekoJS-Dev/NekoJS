// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;

/**
 * 画变种 builder（1.21.1 面）：宽高（16 的倍数，单位像素）与纹理资源 id（指向
 * {@code assets/<ns>/textures/painting/<path>.png}）。26.x 的 title/author 成员在
 * 本节点不存在（成员面差异按版本各冻一份 golden，见 ticket 15 REPORT 五节点差异表）。
 * <pre>
 * event.paintingVariant('mymod:sea', b =&gt; { b.width = 32; b.height = 16 })
 * </pre>
 */
public class PaintingVariantBuilder extends RegistryObjectBuilder<PaintingVariant> {

    private int width = 16;
    private int height = 16;
    /** 纹理资源 id（默认与注册 id 相同）。 */
    private String assetId = null;

    public PaintingVariantBuilder(ResourceLocation id) {
        super(id);
    }

    // ---- 数据属性：显式 setter 与 JavaBean property 同一写入点（ticket 15） ----

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
