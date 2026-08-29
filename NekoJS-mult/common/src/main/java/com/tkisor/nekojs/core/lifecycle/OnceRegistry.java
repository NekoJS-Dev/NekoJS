package com.tkisor.nekojs.core.lifecycle;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * run-once 守卫（{@code once} / {@code clearOnce} 绑定）的核心标记表。
 *
 * <p>标记刻意<b>跨脚本 reload 存活</b>（进程级内存，不持久化）：reload 会重建 Context，
 * 但初始化探针、一次性迁移等操作不应因 reload 而重复执行。需要重跑时用
 * {@code clearOnce(key)}（移除单个）或 {@code clearOnce()}（清空全部）。
 *
 * <p>{@link #runOnce(String)} 基于 {@link ConcurrentHashMap#newKeySet()} 的原子 add 实现
 * check-and-set，多 Context 并发调用同一 key 时恰好一个返回 true。
 */
public final class OnceRegistry {

    /** 进程级共享实例：所有 ScriptType 的 {@code once} 绑定共用，标记随进程存活。 */
    public static final OnceRegistry SHARED = new OnceRegistry();

    private final Set<String> markers = ConcurrentHashMap.newKeySet();

    /**
     * 原子 check-and-set：首次见到该 key 时记录标记并返回 {@code true}（调用方应执行回调），
     * 之后所有调用返回 {@code false}。
     */
    public boolean runOnce(String key) {
        return markers.add(resolveKey(key));
    }

    /** 该 key 的标记是否存在（即 {@link #runOnce} 已返回过 true）。 */
    public boolean has(String key) {
        return markers.contains(resolveKey(key));
    }

    /** 移除单个 key 的标记（下次 {@link #runOnce} 重新返回 true）。返回标记是否原本存在。 */
    public boolean clear(String key) {
        return markers.remove(resolveKey(key));
    }

    /** 清空全部标记（所有 key 下次 {@link #runOnce} 重新返回 true）。 */
    public void clearAll() {
        markers.clear();
    }

    /** 当前标记数量。 */
    public int size() {
        return markers.size();
    }

    /**
     * key 解析缝（seam）：v1 key 全局，直接原样返回。
     *
     * <p>后续按脚本所有者隔离（同 key 不同脚本互不干扰）时，只需在这里加上
     * 当前脚本 id 前缀——绑定层与 {@link #clear}/{@link #has} 无需改动。
     */
    public String resolveKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("once() key must be a non-blank string");
        }
        // TODO(per-script-namespacing): prefix with the owning script id, e.g. scriptId + ':' + key
        return key;
    }
}
