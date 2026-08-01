package com.tkisor.nekojs.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;

/**
 * 1.21.1 客户端渲染层应用（专用服务器上不加载本类）。
 *
 * <p>1.21.1 的 chunk 渲染层来自 {@link ItemBlockRenderTypes} 的静态查找表，NeoForge
 * patch 了 {@code setRenderLayer}（须在 {@code ClientModLoader.isLoading()} 期间调用）。
 * 注册事件（RegisterEvent）在客户端 mod loading 阶段触发，此时 blocks 已创建且
 * {@code ClientModLoader.isLoading()} 为 true，故在 BLOCK 分支内直接应用。
 */
public final class ClientBlockRenderTypes {
    private ClientBlockRenderTypes() {}

    /** 按名称应用方块渲染层；未知名称回退 solid。 */
    public static void apply(Block block, String renderType) {
        RenderType layer = parse(renderType);
        ItemBlockRenderTypes.setRenderLayer(block, ChunkRenderTypeSet.of(layer));
    }

    /** 应用流体渲染层（液体方块/流体本体默认 translucent）。 */
    public static void applyFluid(Fluid fluid, String renderType) {
        RenderType layer = parse(renderType);
        ItemBlockRenderTypes.setRenderLayer(fluid, layer);
    }

    private static RenderType parse(String renderType) {
        return switch (renderType == null ? "solid" : renderType) {
            case "cutout" -> RenderType.cutout();
            case "cutout_mipped", "cutoutmipped" -> RenderType.cutoutMipped();
            case "translucent" -> RenderType.translucent();
            default -> RenderType.solid();
        };
    }
}
