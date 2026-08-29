package com.tkisor.nekojs.core.pack.sync;

import java.util.List;

/**
 * 一个待分发的脚本包快照（平台无关的传输单元）：{@code syncId}（{@code <scope段>:<包id>}，
 * 如 {@code packs:demo} / {@code worldpacks:demo}）、作用域枚举名、manifest 原文 JSON、
 * 内容文件列表与包哈希。NeoForge 26.x / 1.21.1 的 payload 与 Cleanroom 1.12.2 的
 * ByteBuf 编解码各自与本记录互转。
 */
public record SyncedPack(
    String syncId,
    String scopeName,
    String hash,
    String manifestJson,
    List<PackContentFile> files
) {

    public SyncedPack {
        files = List.copyOf(files);
    }

    /** syncId 的目录名编码：{@code packs:demo} → {@code packs_demo}。scope 段固定且不含
     * 下划线、包 id 经 sanitize（{@code [a-z0-9_-]}），替换 {@code :} 后编码可逆且无碰撞。 */
    public static String encodeSyncId(String syncId) {
        return syncId.replace(':', '_');
    }

    public static SyncedPack of(String syncId, String scopeName, String hash, String manifestJson, List<PackContentFile> files) {
        return new SyncedPack(syncId, scopeName, hash, manifestJson, files);
    }
}
