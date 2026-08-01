package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.BlockBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlockRegistryEventJS {
    /** 待注册 BlockItem 的 builder（含已创建的 Block 与可选的 ItemBuilderJS 配置）。 */
    public static final Map<Identifier, BlockBuilderJS> PENDING_BLOCK_ITEMS = new HashMap<>();

    /**
     * 声明了 renderType 的方块：id → renderType。
     * 由客户端资产生成（generateAssets）消费：生成默认方块模型时，
     * {@code 'translucent'} 用 {@code force_translucent} 贴图引用形式（26.x 模型驱动渲染）。
     */
    public static final Map<Identifier, String> RENDER_TYPES = new HashMap<>();

    private final RegisterEvent rawEvent;

    private final List<BlockBuilderJS> builders = new ArrayList<>();

    public BlockRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public BlockBuilderJS create(String id) {
        Identifier location = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("nekojs", id);
        BlockBuilderJS builder = new BlockBuilderJS(location);

        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<BlockBuilderJS> consumer) {
        BlockBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        // 防御性清空：startup 脚本可经 /nekojs reload startup 重跑（注册表虽冻结）
        RENDER_TYPES.clear();
        for (BlockBuilderJS builder : builders) {
            Identifier location = builder.getLocation();
            if (builder.getRenderType() != null) {
                RENDER_TYPES.put(location, builder.getRenderType());
            }

            rawEvent.register(Registries.BLOCK, location, () -> {
                Block block = builder.createBlock();
                builder.setCreatedBlock(block);

                if (builder.shouldGenerateItem()) {
                    PENDING_BLOCK_ITEMS.put(location, builder);
                }
                return block;
            });
        }
    }
}