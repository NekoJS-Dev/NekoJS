package com.tkisor.nekojs.bindings.static_access;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本侧跨脚本/跨 ScriptType 共享的 {@code global} 对象，绑定名为 {@code global}。
 *
 * <p>所有 Context 的 {@code global} 绑定指向同一个进程级 {@link Map}，脚本可
 * {@code global.foo = 1} 写入、{@code global.foo} 读取，值在 server / client / startup / test
 * 各 ScriptType 之间共享。使用 {@link ConcurrentHashMap} 保证多 Context 并发访问安全。
 *
 * <p>reload 会重建 Context，但 {@code global} map 是进程级的，reload 后数据保留（与 KubeJS 行为一致）。
 *
 * <pre>
 * // server_scripts
 * global.count = (global.count || 0) + 1
 * // client_scripts 可读到同一个 global.count
 * </pre>
 */
public final class NekoGlobal {
    private static final Map<String, Object> SHARED = new ConcurrentHashMap<>();

    private NekoGlobal() {}

    /** 返回进程级共享 map（绑定到各 Context 的 {@code global}）。 */
    public static Map<String, Object> shared() {
        return SHARED;
    }
}
