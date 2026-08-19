package com.tkisor.nekojs.core.pack;

/**
 * 脚本包作用域。
 *
 * <ul>
 *   <li>{@link #GLOBAL}——位于 {@code <gameDir>/nekojs/packs/}，随脚本生命周期常规加载，
 *       不与特定存档绑定。</li>
 *   <li>{@link #WORLD}——位于 {@code <worldDir>/nekojs_packs/}，服务器启动时激活、
 *       停服时整体卸载（其事件监听器按 scriptId 前缀反注册），实现按世界隔离。</li>
 * </ul>
 */
public enum ScriptPackScope {
    GLOBAL("packs"),
    WORLD("worldpacks");

    /** ScriptId path 中的前缀段（见 {@link ScriptPack#idPathPrefix()}）。 */
    private final String idSegment;

    ScriptPackScope(String idSegment) {
        this.idSegment = idSegment;
    }

    public String idSegment() {
        return idSegment;
    }
}
