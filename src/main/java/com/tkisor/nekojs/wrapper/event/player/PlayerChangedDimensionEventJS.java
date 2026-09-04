package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 玩家切换维度事件（{@code PlayerEvents.changedDimension}）的加载器中立载荷。
 *
 * <p>成员对齐 NeoForge {@code PlayerEvent.PlayerChangedDimensionEvent} 的 getter 形态：
 * {@code event.player} / {@code event.from}（getFrom，旧维度）/{@code event.to}（getTo，
 * 新维度），类型为 {@code ResourceKey<Level>}（Level 的注册名）。
 *
 * <p>26.1.2 平台事实：vanilla {@code ServerPlayer#changeDimension(...)} 已不存在，维度切换
 * 统一在 {@code ServerPlayer#teleport(TeleportTransition)} 内完成——NeoForge 侧
 * 26.1.2 的 PlayerChangedDimensionEvent 也改在该方法末尾触发；fabric 挂点与之对齐。
 */
@Doc("Fired when a player changes dimension (PlayerEvents.changedDimension).")
@Doc("event.from / event.to are ResourceKey<Level> of the old and new dimensions.")
@Getter
public class PlayerChangedDimensionEventJS {

    @Doc("The player who changed dimension.")
    private final ServerPlayer player;

    @Doc("The dimension the player left (ResourceKey<Level>).")
    private final ResourceKey<Level> from;

    @Doc("The dimension the player entered (ResourceKey<Level>).")
    private final ResourceKey<Level> to;

    public PlayerChangedDimensionEventJS(ServerPlayer player, ResourceKey<Level> from, ResourceKey<Level> to) {
        this.player = player;
        this.from = from;
        this.to = to;
    }
}
