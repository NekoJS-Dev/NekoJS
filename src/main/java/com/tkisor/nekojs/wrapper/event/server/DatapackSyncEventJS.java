package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * 数据包同步事件（{@code ServerEvents.datapackSync}）的**加载器中立**载荷。
 *
 * <p>NeoForge 侧直传原生 {@code OnDatapackSyncEvent}，不经本类；fabric 侧由
 * {@code FabricServerEventBindings} 从 {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS}
 * 转换（签名与语义 javap 实证：player 为 null 表示 reload 后的全体玩家，
 * 与 NF 的 reload 语义一致）。
 *
 * <p>通知型，不可取消。脚本典型用途：数据包重载后给玩家发包/重置客户端缓存。
 */
@Doc("Fired when datapack-synced content should be (re)sent to players.")
@Doc("player is null when syncing to all players after a reload.")
@Getter
public class DatapackSyncEventJS {

    @Doc("The player being synced to, or null when syncing all players (reload).")
    private final ServerPlayer player;

    @Doc("The server's player list.")
    private final PlayerList playerList;

    public DatapackSyncEventJS(ServerPlayer player, PlayerList playerList) {
        this.player = player;
        this.playerList = playerList;
    }
}
