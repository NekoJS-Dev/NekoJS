package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.BlockBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BlockRegistryEventJS {
    /** 待注册 BlockItem 的 builder（含已创建的 Block 与可选的 ItemBuilderJS 配置）。 */
    public static final Map<ResourceLocation, BlockBuilderJS> PENDING_BLOCK_ITEMS = new HashMap<>();

    private final RegisterEvent rawEvent;

    private final List<BlockBuilderJS> builders = new ArrayList<>();

    public BlockRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public BlockBuilderJS create(String id) {
        ResourceLocation location = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("nekojs", id);
        BlockBuilderJS builder = new BlockBuilderJS(location);

        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<BlockBuilderJS> consumer) {
        BlockBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (BlockBuilderJS builder : builders) {
            ResourceLocation location = builder.getLocation();

            rawEvent.register(Registries.BLOCK, location, () -> {
                Block block = builder.createBlock();
                builder.setCreatedBlock(block);

                // 客户端渲染层：专用服务器上 FMLEnvironment.dist 为 DEDICATED_SERVER，
                // 分支不执行 → ClientBlockRenderTypes 不会被加载
                if (builder.getRenderType() != null
                        && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    com.tkisor.nekojs.client.ClientBlockRenderTypes.apply(block, builder.getRenderType());
                }

                if (builder.shouldGenerateItem()) {
                    PENDING_BLOCK_ITEMS.put(location, builder);
                }
                return block;
            });
        }
    }
}