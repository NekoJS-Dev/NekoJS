package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.js.type_adapter.ParseIds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * 脚本侧的 Block 助手，绑定为全局 {@code Block}。
 *
 * <p>对齐 KubeJS 语义：{@code Block.id('minecraft:stone')} 按字符串 id 查方块。
 * 用包装对象而非给 MC 的 {@code Block} 类注入静态方法（原因同 ItemJS 注释）。
 */
public class BlockJS {

    /** 按字符串 id 查方块；不存在返回 null。id 缺省 {@code minecraft:} 前缀。 */
    public Block id(String id) {
        Identifier location = ParseIds.parseItemOrBlockId(id);
        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    /** 取方块的注册 id（{@code minecraft:stone} 形式）。 */
    public Identifier idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
