package com.tkisor.nekojs.core.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 绑定到单个 Context 的 {@code global}/{@code shared} 视图（票 10）。
 *
 * <p>脚本侧表面与被替换的宿主 Map 绑定完全一致：{@code global.foo}（成员读）、
 * {@code global.foo = v}（成员写）、{@code delete global.foo}（成员删除）、
 * {@code global.clear()}（清空）——GraalJS 对宿主 {@link Map} 的成员访问就是
 * get/put/remove。本类实现 {@link Map} 以保留该 interop 语义（实证探针：成员键 =
 * Map 接口方法名，与旧 ConcurrentHashMap 绑定行为相同）。
 *
 * <p>事务（候选）generation：顶层 set/delete/clear 进 {@link GlobalStore.MapWriteSet}
 * （read-your-writes、失败丢弃、commit 联合发布）；非事务 generation：直接提交进 store。
 * <b>不承诺深回滚</b>：嵌套对象/列表/已共享 Java 对象的内部修改不在写集事务边界内——
 * 需要事务保护的脚本应构造新值后整体替换顶层 key（spec 10）。
 */
public final class GlobalView implements Map<String, Object> {

    private final GenerationGlobals generation;
    private final GlobalStore store;
    private final GlobalStore.MapWriteSet writes; // null = 非事务（直接提交）

    GlobalView(GenerationGlobals generation, GlobalStore store, GlobalStore.MapWriteSet writes) {
        this.generation = generation;
        this.store = store;
        this.writes = writes;
    }

    /** 视图绑定的命名空间（{@code "global:<type>"} / {@code "shared"}；诊断用）。 */
    public String scope() {
        return store.scope();
    }

    // ---- 顶层写（事务语义的进入点） ----

    @Override
    public Object put(String key, Object value) {
        if (writes != null) {
            return writes.set(key, value);
        }
        synchronized (store.ownerLock()) {
            return store.rawPut(key, value, generation, GuestValues.isGuest(value));
        }
    }

    @Override
    public Object remove(Object key) {
        if (!(key instanceof String stringKey)) return null;
        if (writes != null) {
            return writes.delete(stringKey);
        }
        synchronized (store.ownerLock()) {
            return store.rawRemove(stringKey);
        }
    }

    @Override
    public void clear() {
        if (writes != null) {
            writes.markClear();
            return;
        }
        synchronized (store.ownerLock()) {
            store.rawClear();
        }
    }

    // ---- 读（read-your-writes：候选先看写集再看已提交 store） ----

    @Override
    public Object get(Object key) {
        if (!(key instanceof String stringKey)) return null;
        if (writes != null) {
            return writes.effectiveGet(stringKey);
        }
        return store.get(stringKey);
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String stringKey)) return false;
        if (writes != null) {
            return writes.effectiveContainsKey(stringKey);
        }
        return store.containsKey(stringKey);
    }

    @Override
    public int size() {
        return effectiveContents().size();
    }

    @Override
    public boolean isEmpty() {
        return effectiveContents().isEmpty();
    }

    // ---- 其余 Map 面（快照语义；interop 只用 get/put/remove/clear，这里为 Java 侧完整性） ----

    @Override
    public Set<String> keySet() {
        return new LinkedHashSet<>(effectiveContents().keySet());
    }

    @Override
    public Collection<Object> values() {
        return effectiveContents().values();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        return effectiveContents().entrySet();
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (m == null) return;
        for (Map.Entry<? extends String, ?> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public boolean containsValue(Object value) {
        return effectiveContents().containsValue(value);
    }

    /** 有效内容快照（写集覆盖 + clear 语义归并；复制出锁，避免暴露内部结构）。 */
    private Map<String, Object> effectiveContents() {
        if (writes != null) {
            return writes.effectiveSnapshot();
        }
        synchronized (store.ownerLock()) {
            Map<String, Object> out = new HashMap<>();
            for (String key : store.keysUnlocked()) {
                out.put(key, store.rawGet(key));
            }
            return out;
        }
    }
}
