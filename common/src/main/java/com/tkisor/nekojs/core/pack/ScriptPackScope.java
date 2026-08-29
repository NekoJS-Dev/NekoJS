package com.tkisor.nekojs.core.pack;

/**
 * 脚本包作用域。
 *
 * <ul>
 *   <li>{@link #GLOBAL}——位于 {@code <gameDir>/nekojs/packs/}，随脚本生命周期常规加载，
 *       不与特定存档绑定。</li>
 *   <li>{@link #WORLD}——位于 {@code <worldDir>/nekojs_packs/}，服务器启动时激活、
 *       停服时整体卸载（其事件监听器按 scriptId 前缀反注册），实现按世界隔离。</li>
 *   <li>{@link #SERVER_CACHE}——位于 {@code <gameDir>/nekojs/server_packs/<bucket>/}，
 *       多人模式下由服务器推送（P2 包分发）落盘，客户端验签 + 信任通过后激活执行，
 *       断线时整体卸载（缓存文件保留，下次连入按哈希复用）。强制启用（状态文件与
 *       manifest 默认值均不适用——是否执行已由验签/信任关口决定）。</li>
 * </ul>
 */
public enum ScriptPackScope {
    GLOBAL("packs"),
    WORLD("worldpacks"),
    SERVER_CACHE("serverpacks");

    /** ScriptId path 中的前缀段（见 {@link ScriptPack#idPathPrefix()}）。 */
    private final String idSegment;

    ScriptPackScope(String idSegment) {
        this.idSegment = idSegment;
    }

    public String idSegment() {
        return idSegment;
    }
}
