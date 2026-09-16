package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 generation 的 global/shared 视图与候选写集持有者（票 10）。
 *
 * <p>每个 Context（候选或 active）创建一份；{@link #globalView()}/{@link #sharedView()}
 * 绑定为该 Context 的 {@code global}/{@code shared}（工作名）成员。视图本身与绑定定义
 * 分离（spec 10「绑定定义与按 generation 创建的视图必须分开」）。
 *
 * <ul>
 *   <li><b>非事务 generation</b>（初始 load / STARTUP reset+load / 单文件 FILE 路径）：
 *       顶层 set/delete/clear 直接提交进 store；</li>
 *   <li><b>事务 generation</b>（候选）：顶层写进写集，read-your-writes；
 *       {@link #preflightJoint()} 联合预检（私有 + 共享写集 + 外部
 *       {@link CandidateStatePlan}），{@link #publishJoint()} 在 commit 点一次性联合发布；
 *       {@link #discard()} 随候选失败丢弃全部写集；</li>
 *   <li><b>generation 生命周期</b>：{@link #close()} 只失效「本 generation 写入的 guest
 *       值」（已销毁 Context 的 guest 函数/Value 不因存入 Map 获得永久保活）；非 guest
 *       值与 store 整体跨 reload 保留（root close 才释放）。</li>
 * </ul>
 */
public final class GenerationGlobals implements AutoCloseable {

    private final GlobalStateStores stores;
    private final ScriptType scriptType;
    private final boolean transactional;
    private final GlobalStore privateStore;
    private final GlobalStore sharedStoreRef;
    private final GlobalStore.MapWriteSet privateWrites;
    private final GlobalStore.MapWriteSet sharedWrites;

    /** 候选的外部联合计划（测试计划 / 后续领域计划）；只在 owner thread（候选构建）上写。 */
    private final List<CandidateStatePlan> plans = new ArrayList<>();

    private GlobalView globalView;
    private GlobalView sharedView;
    private volatile boolean closed;

    GenerationGlobals(GlobalStateStores stores, ScriptType scriptType, boolean transactional) {
        this.stores = stores;
        this.scriptType = scriptType;
        this.transactional = transactional;
        this.privateStore = stores.storeFor(scriptType);
        this.sharedStoreRef = stores.sharedStore();
        this.privateWrites = transactional ? new GlobalStore.MapWriteSet(privateStore) : null;
        this.sharedWrites = transactional ? new GlobalStore.MapWriteSet(sharedStoreRef) : null;
    }

    public ScriptType scriptType() {
        return scriptType;
    }

    public boolean isTransactional() {
        return transactional;
    }

    /** 本 generation 的类型私有 {@code global} 视图（绑定到所属 Context）。 */
    public GlobalView globalView() {
        if (globalView == null) {
            globalView = new GlobalView(this, privateStore, privateWrites);
        }
        return globalView;
    }

    /** 本 generation 的显式共享 {@code shared} 视图（工作名；绑定到所属 Context）。 */
    public GlobalView sharedView() {
        if (sharedView == null) {
            sharedView = new GlobalView(this, sharedStoreRef, sharedWrites);
        }
        return sharedView;
    }

    // ---- 候选外部计划边界（AC5） ----

    /**
     * 候选执行期间挂入联合计划（非候选 generation 上调用得到明确拒绝）。
     * ScriptManager 经 {@code registerCandidatePlan(Context, plan)} 提供 Context 侧 seam。
     */
    public void addPlan(CandidateStatePlan plan) {
        if (plan == null) throw new NullPointerException("plan");
        if (!transactional) {
            throw new IllegalStateException(
                    "generation of " + scriptType.name + " is not a candidate; plans join candidates only");
        }
        if (closed) {
            throw new IllegalStateException("generation already closed");
        }
        plans.add(plan);
    }

    // ---- 联合预检 / 联合发布 / 丢弃（事务 generation） ----

    /**
     * 联合预检（STATE_PLAN 阶段）：私有 + 共享写集冲突校验，随后外部计划 preflight。
     * 抛 {@link GlobalStateException}（domain 结构化）即让 candidate 失败。
     */
    public void preflightJoint() {
        if (!transactional) return;
        if (privateWrites != null) privateWrites.validate();
        if (sharedWrites != null) sharedWrites.validate();
        for (CandidateStatePlan plan : List.copyOf(plans)) {
            try {
                plan.preflight();
            } catch (GlobalStateException e) {
                throw e;
            } catch (Throwable t) {
                throw new GlobalStateException("state-plan-preflight:" + plan.domain(),
                        "candidate state plan '" + plan.domain() + "' failed joint preflight", t);
            }
        }
    }

    /**
     * 联合发布（commit 点，必须尚未发生任何 commit 期变更）：锁内先复验两个写集
     * （复验失败 = 预检与提交之间有其他 writer——在改任何状态之前抛出，active 完整保留），
     * 再执行外部计划 publish（契约：预检通过后不得抛；抛出则 global/shared 写集不发布），
     * 最后一次性应用私有 + 共享写集（同一持锁内完成，无半提交）。
     */
    public void publishJoint() {
        if (!transactional) return;
        synchronized (stores.lock) {
            stores.assertOpen();
            // (a) 复验（任何变更之前）：冲突在此抛出 → discardCandidate，不丢已提交写
            if (privateWrites != null) privateWrites.validate();
            if (sharedWrites != null) sharedWrites.validate();
            // (b) 外部计划发布（在 global/shared 写集之前；契约见 CandidateStatePlan）
            for (CandidateStatePlan plan : List.copyOf(plans)) {
                try {
                    plan.publish();
                } catch (Throwable t) {
                    throw new GlobalStateException("state-plan-publish:" + plan.domain(),
                            "candidate state plan '" + plan.domain() + "' violated its publish contract"
                                    + " (must not throw after preflight); global/shared write sets not published,"
                                    + " plan's own partial effects are its responsibility", t);
                }
            }
            // (c) 一次性应用（锁内；此后不再有失败路径）
            if (privateWrites != null) privateWrites.apply(this);
            if (sharedWrites != null) sharedWrites.apply(this);
        }
    }

    /** 候选失败/取消：丢弃全部写集与计划（不触碰已提交 store，不产生任何变更）。 */
    public void discard() {
        synchronized (stores.lock) {
            if (privateWrites != null) privateWrites.discardOps();
            if (sharedWrites != null) sharedWrites.discardOps();
        }
        plans.clear();
    }

    // ---- generation 生命周期 ----

    /**
     * generation 关闭（Context 销毁路径调用）：失效本 generation 写入的 guest 值
     * （不延长已销毁 Context 的生命周期）；非 guest 值保留在 store 内跨 reload。
     * 幂等。
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        privateStore.evictGuestOwned(this);
        sharedStoreRef.evictGuestOwned(this);
        plans.clear();
    }

    public boolean isClosed() {
        return closed;
    }
}
