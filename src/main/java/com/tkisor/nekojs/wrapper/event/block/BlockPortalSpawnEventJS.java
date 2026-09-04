package com.tkisor.nekojs.wrapper.event.block;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;

/**
 * 传送门生成事件（{@code BlockEvents.portalSpawn}）的**加载器中立**载荷。
 *
 * <p>成员名对齐 NeoForge 26.x {@code BlockEvent.PortalSpawnEvent}：
 * {@code event.level}（LevelAccessor）/ {@code event.pos} / {@code event.state} /
 * {@code event.portalSize}（NeoForge 的 getLevel/getPos/getState/getPortalSize，
 * {@link PortalShape} 为原版类，加载器无关）。按 {@code state.getBlock()}
 * 分发（与 NeoForge 侧 {@code dispatchByBlock} 一致，键为 {@code minecraft:nether_portal}）。
 *
 * <p>可取消：监听器返回 {@code true} 时跳过传送门方块的生成（NeoForge
 * {@code PortalSpawnEvent} 的 ICancellableEvent 语义）。fabric 侧挂点：
 * {@code MixinPortalShape} 在 {@code PortalShape#createPortalBlocks} 入口投递并取消。
 *
 * <p>fabric 覆盖范围说明：26.x 中 {@code PortalShape#createPortalBlocks} 全 jar 仅
 * {@code BaseFireBlock#onPlace}（玩家点燃黑曜石框架）一处调用（javap 实证）；
 * {@code PortalForcer#createPortal}（实体跨维时无传送门、程序化生成框架+传送门）
 * 绕开本类直接 {@code setBlock}，不在本事件覆盖范围——见接线清单。
 */
@Doc("Fired when a nether portal is about to be created (BlockEvents.portalSpawn).")
@Doc("Return true to cancel the portal creation.")
@Getter
public class BlockPortalSpawnEventJS {

    @Doc("The level the portal is being created in.")
    private final LevelAccessor level;

    @Doc("Position of the portal (bottom-left corner of the shape).")
    private final BlockPos pos;

    @Doc("Block state of the nether portal blocks about to be placed.")
    private final BlockState state;

    @Doc("The portal shape (vanilla PortalShape; axis, width, height, frame info).")
    private final PortalShape portalSize;

    public BlockPortalSpawnEventJS(LevelAccessor level, BlockPos pos, BlockState state, PortalShape portalSize) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.portalSize = portalSize;
    }

    @Doc("The portal block (minecraft:nether_portal).")
    public Block getBlock() {
        return state.getBlock();
    }
}
