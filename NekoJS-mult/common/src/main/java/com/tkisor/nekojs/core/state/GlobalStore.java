package com.tkisor.nekojs.core.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个受管顶层命名空间的已提交 backing store（票 10）。
 *
 * <p>一个 root 内每个 {@code ScriptType} 一个私有 store（{@code global}），外加一个共享
 * store（{@code shared}，工作名；最终公开符号由 MANAGED_SURFACE 冻结，本类不承载命名）。
 * store 由 {@link GlobalStateStores}（root 拥有）创建，跨普通 reload、server stop 与切换
 * 世界保留，root close 时释放；generation close 只清除<b>该 generation 写入的 guest 值</b>
 * （见 {@link #evictGuestOwned}），不清空 store。
 *
 * <p>并发与冲突：每个 key 维护版本号、store 维护 epoch，均在<b>已提交</b>变更时递增。
 * 候选 generation 的顶层 set/delete/clear 不直接写 store，而是记入 {@link MapWriteSet}
 * （版本基线取首写时值）；commit 前联合预检发现「候选首写之后有其他 writer 提交过同一
 * key / clear 之后该 store 有任何提交」即判冲突——不丢写（其他 writer 的已提交值保留）、
 * candidate 失败。跨类型私有 key 天然不冲突（不同 store 各自版本）；shared 的竞争写入由
 * 此机制显式检出。
 *
 * <p>锁模型：本 root 全部 store 共用 {@link GlobalStateStores} 的同一把锁，所有读写都在
 * 该锁内完成——因此「私有 + 共享」两个写集可以在一次持锁内联合校验并联合发布（联合
 * 成败，无半提交），观察者不可能看到半边状态。
 */
public final class GlobalStore {

    /** 已提交条目：值 + 写入它的 generation（Java 侧直接写入为 null）+ 是否 guest 值。 */
    static final class Entry {
        final Object value;
        final GenerationGlobals owner;
        final boolean guest;

        Entry(Object value, GenerationGlobals owner, boolean guest) {
            this.value = value;
            this.owner = owner;
            this.guest = guest;
        }
    }

    /**
     * 候选 generation 的顶层写集（票 10 candidate 写集语义）。
     *
     * <ul>
     *   <li>read-your-writes：{@link #effectiveGet} 先看写集，再看已提交 store；</li>
     *   <li>首写基线：对某 key 的第一次 set/delete 记录当时的已提交版本；clear 记录当时
     *       的 store epoch（clear 会覆盖整个 store，任何后续提交都构成冲突）；</li>
     *   <li>候选内 clear 之后的写集条目视为「clear 后重新写入」，clear 之前的条目作废；</li>
     *   <li>{@link #validate} 联合预检；{@link #apply} 在锁内发布（版本随之 bump）。</li>
     * </ul>
     */
    static final class MapWriteSet {
        static final class PendingOp {
            final Object value;
            final boolean remove;
            final long baseVersion;
            final boolean guest;

            PendingOp(Object value, boolean remove, long baseVersion, boolean guest) {
                this.value = value;
                this.remove = remove;
                this.baseVersion = baseVersion;
                this.guest = guest;
            }
        }

        private final GlobalStore store;
        private final Map<String, PendingOp> ops = new LinkedHashMap<>();
        private boolean cleared;
        private long clearBaseEpoch;

        MapWriteSet(GlobalStore store) {
            this.store = store;
        }

        boolean hasWrites() {
            return cleared || !ops.isEmpty();
        }

        Object effectiveGet(String key) {
            PendingOp op = ops.get(key);
            if (op != null) {
                return op.remove ? null : op.value;
            }
            if (cleared) {
                return null;
            }
            synchronized (store.owner.lock) {
                return store.rawGet(key);
            }
        }

        boolean effectiveContainsKey(String key) {
            PendingOp op = ops.get(key);
            if (op != null) {
                return !op.remove;
            }
            if (cleared) {
                return false;
            }
            synchronized (store.owner.lock) {
                return store.values.containsKey(key);
            }
        }

        /** 有效内容快照（clear 语义 + 写集覆盖归并；复制出锁）。 */
        Map<String, Object> effectiveSnapshot() {
            synchronized (store.owner.lock) {
                Map<String, Object> out = new LinkedHashMap<>();
                if (!cleared) {
                    for (Map.Entry<String, Entry> entry : store.values.entrySet()) {
                        out.put(entry.getKey(), entry.getValue().value);
                    }
                }
                for (Map.Entry<String, PendingOp> op : ops.entrySet()) {
                    if (op.getValue().remove) {
                        out.remove(op.getKey());
                    } else {
                        out.put(op.getKey(), op.getValue().value);
                    }
                }
                return out;
            }
        }

        /** 顶层 set：进写集；首写记录该 key 的已提交版本基线（此后沿用同一基线）。 */
        Object set(String key, Object value) {
            synchronized (store.owner.lock) {
                Object previous = effectiveGet(key);
                long base = ops.containsKey(key) ? ops.get(key).baseVersion : store.keyVersionUnlocked(key);
                ops.put(key, new PendingOp(value, false, base, GuestValues.isGuest(value)));
                return previous;
            }
        }

        /** 顶层 delete：进写集（remove 项）；返回删除前的有效值。 */
        Object delete(String key) {
            synchronized (store.owner.lock) {
                Object previous = effectiveGet(key);
                long base = ops.containsKey(key) ? ops.get(key).baseVersion : store.keyVersionUnlocked(key);
                ops.put(key, new PendingOp(null, true, base, false));
                return previous;
            }
        }

        /** 顶层 clear：标记 cleared（记录 epoch 基线）并作废 clear 前的写集条目。 */
        void markClear() {
            synchronized (store.owner.lock) {
                this.cleared = true;
                this.clearBaseEpoch = store.epochUnlocked();
                this.ops.clear();
            }
        }

        /** 候选失败/取消：丢弃全部写集（幂等；不触碰已提交 store）。 */
        void discardOps() {
            synchronized (store.owner.lock) {
                ops.clear();
                cleared = false;
                clearBaseEpoch = 0L;
            }
        }

        /** 联合预检：基线之后有其他 writer 提交即冲突（不丢写——已提交值原样保留）。 */
        void validate() {
            for (Map.Entry<String, PendingOp> entry : ops.entrySet()) {
                long current = store.keyVersionUnlocked(entry.getKey());
                if (current != entry.getValue().baseVersion) {
                    throw new GlobalStateException("global-write-conflict",
                            store.scope + " key '" + entry.getKey() + "' was committed by another writer"
                                    + " during candidate preparation (base=" + entry.getValue().baseVersion
                                    + ", current=" + current + "); candidate fails, committed value retained");
                }
            }
            if (cleared && store.epochUnlocked() != clearBaseEpoch) {
                throw new GlobalStateException("global-write-conflict",
                        store.scope + " was committed by another writer during candidate preparation"
                                + " (clear base epoch=" + clearBaseEpoch + ", current=" + store.epochUnlocked()
                                + "); candidate fails, committed values retained");
            }
        }

        /** 发布（必须已持 root 锁且 validate 通过）：clear → 逐条 set/delete，版本随之 bump。 */
        void apply(GenerationGlobals generation) {
            if (cleared) {
                store.rawClear();
            }
            for (Map.Entry<String, PendingOp> entry : ops.entrySet()) {
                PendingOp op = entry.getValue();
                if (op.remove) {
                    store.rawRemove(entry.getKey());
                } else {
                    store.rawPut(entry.getKey(), op.value, generation, op.guest);
                }
            }
            // 写集一次性消费：发布完成即清账，此后 hasWrites()==false，
            // 误入的重复 publish/discard 无剩余条目可作用
            ops.clear();
            cleared = false;
            clearBaseEpoch = 0L;
        }
    }

    private final GlobalStateStores owner;
    private final String scope;
    private final Map<String, Entry> values = new LinkedHashMap<>();
    private final Map<String, Long> keyVersions = new HashMap<>();
    private long epoch;

    GlobalStore(GlobalStateStores owner, String scope) {
        this.owner = owner;
        this.scope = scope;
    }

    public String scope() {
        return scope;
    }

    // ---- 已提交状态读取（Java 侧读者 / 视图委托；root 锁内，见类注释） ----

    public Object get(String key) {
        synchronized (owner.lock) {
            return rawGet(key);
        }
    }

    public boolean containsKey(String key) {
        synchronized (owner.lock) {
            return values.containsKey(key);
        }
    }

    public int size() {
        synchronized (owner.lock) {
            return values.size();
        }
    }

    public boolean isEmpty() {
        synchronized (owner.lock) {
            return values.isEmpty();
        }
    }

    public Set<String> keys() {
        synchronized (owner.lock) {
            return new LinkedHashSet<>(values.keySet());
        }
    }

    /** 当前 key 的已提交版本（从未写过的 key 为 0；冲突预检的基线读点）。 */
    public long keyVersion(String key) {
        synchronized (owner.lock) {
            return keyVersions.getOrDefault(key, 0L);
        }
    }

    /** store 的已提交 epoch（每次已提交变更 +1；候选 clear 的冲突基线读点）。 */
    public long epoch() {
        synchronized (owner.lock) {
            return epoch;
        }
    }

    // ---- Java 侧直接写入（「其他 writer」公开 seam：插件/测试经 root.globalState() 写） ----

    public Object put(String key, Object value) {
        synchronized (owner.lock) {
            return rawPut(key, value, null, GuestValues.isGuest(value));
        }
    }

    public Object remove(String key) {
        synchronized (owner.lock) {
            return rawRemove(key);
        }
    }

    public void clear() {
        synchronized (owner.lock) {
            rawClear();
        }
    }

    /** Java 侧批量写入（便捷；逐 key 版本语义与 {@link #put} 一致）。 */
    public void putAll(Map<String, ?> map) {
        if (map == null) return;
        synchronized (owner.lock) {
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                rawPut(entry.getKey(), entry.getValue(), null, GuestValues.isGuest(entry.getValue()));
            }
        }
    }

    // ---- 锁内原语（调用方必须已持 owner.lock；generation 视图/发布路径使用） ----

    Object rawGet(String key) {
        Entry entry = values.get(key);
        return entry == null ? null : entry.value;
    }

    Set<String> keysUnlocked() {
        return new LinkedHashSet<>(values.keySet());
    }

    /** 本 store 所属 root 状态域的共用锁（视图与写集在锁内读已提交状态）。 */
    Object ownerLock() {
        return owner.lock;
    }

    /** active（非事务）generation 视图的直接提交：立即发布、立即 bump 版本。 */
    Object rawPut(String key, Object value, GenerationGlobals generation, boolean guest) {
        owner.assertOpen();
        Entry previous = values.put(key, new Entry(value, generation, guest));
        bump(key);
        return previous == null ? null : previous.value;
    }

    Object rawRemove(String key) {
        owner.assertOpen();
        Entry previous = values.remove(key);
        if (previous != null) {
            bump(key);
            return previous.value;
        }
        return null;
    }

    void rawClear() {
        owner.assertOpen();
        for (String key : values.keySet()) {
            keyVersions.merge(key, 1L, Long::sum);
        }
        values.clear();
        epoch++;
    }

    long keyVersionUnlocked(String key) {
        return keyVersions.getOrDefault(key, 0L);
    }

    long epochUnlocked() {
        return epoch;
    }

    /** generation close：只清除「该 generation 写入的 guest 值」（存入 Map 不获得永久保活）。 */
    void evictGuestOwned(GenerationGlobals generation) {
        if (generation == null) return;
        synchronized (owner.lock) {
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, Entry> entry : values.entrySet()) {
                if (generation.equals(entry.getValue().owner) && entry.getValue().guest) {
                    toRemove.add(entry.getKey());
                }
            }
            for (String key : toRemove) {
                values.remove(key);
                // 按已提交变更 bump（正在构建的候选据此能检测到「基线之后被 generation
                // 失效清除改动」——这与其他 writer 的提交语义一致）。
                bump(key);
            }
        }
    }

    /** root close：释放全部状态（此后该 root 的 store 不再被使用）。 */
    void release() {
        synchronized (owner.lock) {
            values.clear();
            keyVersions.clear();
            epoch = 0L;
        }
    }

    private void bump(String key) {
        keyVersions.merge(key, 1L, Long::sum);
        epoch++;
    }
}
