package com.tkisor.nekojs.api.data;

import java.util.HashMap;

/**
 * 轻量内存状态容器，挂载到 {@code MinecraftServer}/{@code Level}/{@code Player} 等宿主上。
 *
 * <p>对应 KubeJS 的 {@code AttachedData}：纯内存、不持久化（持久化需求请用 NekoJS 现有的
 * {@code pdata}/{@code PersistentDataJS}）。脚本与插件通过 {@code host.data} 访问；
 * 首次访问时由平台 mixin lazy 创建，并遍历所有插件触发对应的 {@code attachXxxData} 钩子。
 *
 * <p>用法为方法式（与 {@code PersistentDataJS} 一致）：{@code data.get("k")} / {@code data.put("k", v)}
 * / {@code data.add("k", v)} / {@code data.containsKey("k")} / {@code data.remove("k")}。
 *
 * @param <T> 宿主类型
 */
public class AttachedData<T> extends HashMap<String, Object> {
    private final T parent;

    public AttachedData(T parent) {
        this.parent = parent;
    }

    /** 返回该容器挂载的宿主对象。 */
    public T getParent() {
        return parent;
    }

    /** {@link #put(Object, Object)} 的语义别名，便于脚本侧链式/直观调用。 */
    public void add(String key, Object data) {
        put(key, data);
    }
}
