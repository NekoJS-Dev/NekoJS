package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;

import java.util.EnumMap;

/**
 * root 拥有的受管 global/shared 状态域（票 10）：替换进程级 {@code NekoGlobal} 静态 Map。
 *
 * <ul>
 *   <li><b>按类型私有 store</b>：每个 {@code ScriptType} 一个（STARTUP/SERVER/CLIENT/TEST
 *       同名 key 互不覆盖）；同类型多文件与该类型普通 reload 共享同一 store；</li>
 *   <li><b>显式共享 store</b>：跨类型显式共享入口（工作名 {@code shared}，最终公开符号由
 *       MANAGED_SURFACE 冻结），沿用同一窄域 Map 语义；</li>
 *   <li><b>生命周期</b>：同一 root 内跨普通 reload、server stop、切换世界保留；
 *       {@link #closeAll()}（root close）释放；generation close 不清空（只失效该
 *       generation 的 guest 值）；新的独立 root / 测试 runner 从空状态开始——本类无任何
 *       static 状态；</li>
 *   <li><b>内存共享 ≠ 网络同步</b>：shared 只在同一 root 所在进程内共享，不替代
 *       客户端/服务端网络协议。</li>
 * </ul>
 *
 * <p>本类不包含领域语义；领域计划经 {@link CandidateStatePlan} 挂入
 * {@link GenerationGlobals} 的联合预检/联合成败边界，不进入 global owner。
 */
public final class GlobalStateStores {

    /** 整个 root 状态域的共用锁（全部 store 的读写与联合发布都在这把锁内，见 GlobalStore）。 */
    final Object lock = new Object();

    private final EnumMap<ScriptType, GlobalStore> privateStores = new EnumMap<>(ScriptType.class);
    private final GlobalStore sharedStore = new GlobalStore(this, "shared");
    private volatile boolean closed;

    /** 该类型的私有 global store（按需创建；同类型多 generation 共享同一实例）。 */
    public GlobalStore storeFor(ScriptType type) {
        assertOpen();
        synchronized (lock) {
            return privateStores.computeIfAbsent(type, t -> new GlobalStore(this, "global:" + t.name));
        }
    }

    /** 显式跨类型共享 store（工作名 shared）。 */
    public GlobalStore sharedStore() {
        assertOpen();
        return sharedStore;
    }

    /**
     * 创建一个 generation 的 global/shared 视图持有者。
     *
     * @param transactional true = 候选 generation（顶层写进写集，commit 才发布）；
     *                      false = active/非事务 generation（顶层写直接提交）
     */
    public GenerationGlobals newGeneration(ScriptType type, boolean transactional) {
        assertOpen();
        return new GenerationGlobals(this, type, transactional);
    }

    /** root close：释放全部状态（此后本 stores 的一切写入入口明确拒绝）。 */
    public void closeAll() {
        closed = true;
        synchronized (lock) {
            for (GlobalStore store : privateStores.values()) {
                store.release();
            }
            privateStores.clear();
            sharedStore.release();
        }
    }

    /** 测试/诊断观察点：是否已随 root close 释放。 */
    public boolean isClosed() {
        return closed;
    }

    void assertOpen() {
        if (closed) {
            throw new IllegalStateException("GlobalStateStores already released by root close");
        }
    }
}
