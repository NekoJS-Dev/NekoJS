package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 脚本侧的 Block 助手，绑定为全局 {@code Block}。
 *
 * <p>对齐 KubeJS / NeoForge 语义：{@code Block.id('minecraft:stone')} 按字符串 id 查方块。
 * 用包装对象而非给 MC 的 {@code Block} 类注入静态方法（原因同 {@link ItemJS}）。
 */
public class BlockJS {

    /** 按字符串 id 查方块；不存在返回 null。 */
    public Block id(String id) {
        ResourceLocation location = tryParse(id);
        if (location == null) {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(location);
    }

    /** 取方块的注册 id（{@code minecraft:stone} 形式）；未注册返回 null。 */
    public ResourceLocation idOf(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }

    private static ResourceLocation tryParse(String value) {
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
