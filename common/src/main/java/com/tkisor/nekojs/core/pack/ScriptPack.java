package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.Path;

/**
 * 一个已扫描的脚本包：manifest 元数据 + 作用域 + 启用态（状态文件 &gt; manifest 默认）。
 * 包内脚本目录为 {@code <root>/<type.name>_scripts/}，与平铺脚本目录同名规则对齐。
 */
public record ScriptPack(
    String id,
    String name,
    String version,
    ScriptPackScope scope,
    Path root,
    boolean enabled,
    ScriptPackManifest manifest
) {

    /** 本包某脚本类型的脚本目录（可能不存在，调用方自行判空）。 */
    public Path scriptsDirFor(ScriptType type) {
        return root.resolve(type.name + "_scripts");
    }

    /**
     * 包内脚本 ScriptId path 的前缀段：{@code packs/<id>/}（GLOBAL）或
     * {@code worldpacks/<id>/}（WORLD）。完整 ScriptId 由
     * {@code ScriptLocator} 拼接为 {@code nekojs:<type>/<前缀段><包内相对路径>}，
     * 该前缀同时是世界卸载时按 scriptId 前缀反注册监听器的定位键。
     */
    public String idPathPrefix() {
        return scope.idSegment() + "/" + id + "/";
    }

    /** 完整前缀（含 ScriptType 段），用于按包反注册：{@code nekojs:<type>/<idPathPrefix>}。 */
    public String scriptIdPrefix(ScriptType type) {
        return "nekojs:" + type.name + "/" + idPathPrefix();
    }

    @Override
    public String toString() {
        return scope + ":" + id + "@" + root;
    }
}
