package com.tkisor.nekojs.core.modification;

import com.tkisor.nekojs.core.state.CandidateStatePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * generation-scoped 的 Item/Block 修改候选计划（票 39 AC2）：
 * 候选收集期只累积声明（inert），对生产路由、其他 generation 与外部节点不可见；
 * 经 {@link CandidateStatePlan} 挂入同一 candidate 的联合预检/联合发布边界
 * （票 10 AC5）——修改计划与 global/shared 顶层写集联合成功或失败，不半提交。
 *
 * <p>生命周期：候选构建期由收集事件产出并注册；候选失败/被抢占/被 watchdog 终止时
 * 随 {@code GenerationGlobals.discard()} 丢弃（从不 publish）；commit 点由
 * {@link ModificationApplier} 恢复基线并应用一次。
 *
 * <p>收集失败标记：收集事件内的声明错误（未知目标、非法值、回调抛出）记录在计划上
 * 而不是当场失败——与旧路径「监听器错误不中断 post」的可观察顺序一致；但标记了失败的
 * 计划在 preflight 必然失败（整批失败，无部分修改），静默 stale 不算成功。
 */
public final class ModificationCandidatePlan implements CandidateStatePlan {

    /** 计划标识（进入失败结果的 domain）。 */
    public static final String DOMAIN = "item-block-modification";

    private final ModificationApplier applier;
    private final List<ModificationDeclaration> declarations = new ArrayList<>();
    private volatile String collectionFailure;
    private volatile Throwable collectionError;

    public ModificationCandidatePlan(ModificationApplier applier) {
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** 追加一条声明（收集事件按可观察顺序调用；同目标后声明整体替换前声明由 apply 语义承载）。 */
    public synchronized void add(ModificationDeclaration declaration) {
        if (collectionFailure != null) {
            return; // 已失败的批次只保留首个错误，不再累积声明
        }
        declarations.add(Objects.requireNonNull(declaration, "declaration"));
    }

    /**
     * 标记收集失败（首个错误生效；计划进入「preflight 必失败」状态）。
     *
     * <p><b>收集器可选 API</b>：给「收集期捕获错误、但仍要求整批失败」的领域收集器使用
     * （把错误记进计划而不是当场抛出，从而保留 post 不中断的可观察顺序）。
     * <b>Item/Block modification 的生产路径不使用它</b>——真实收集器
     * （{@code ModificationDomainOwner#collect}）让 {@code ItemModificationEventJS#modify}
     * 的异常直接向上传播，由 {@code ScriptManager} 归因
     * {@code domain-collect:<domain>} 并在 DOMAIN_PLAN 阶段整批失败。
     */
    public synchronized void fail(String reason, Throwable error) {
        if (collectionFailure == null) {
            collectionFailure = reason;
            collectionError = error;
        }
    }

    public synchronized List<ModificationDeclaration> declarations() {
        return List.copyOf(declarations);
    }

    public synchronized String collectionFailure() {
        return collectionFailure;
    }

    /** 是否已有声明（无声明且未失败的计划 publish 时只做基线恢复——声明移除语义）。 */
    public synchronized boolean isEmpty() {
        return declarations.isEmpty() && collectionFailure == null;
    }

    /**
     * 计划指纹（AC8）：规范化声明（声明顺序 + 每声明属性按名排序的规范化值）的确定性
     * 文本。显式 setter 与 JavaBean-style property assignment 两种写法经同一 setter /
     * 同一规范化后必须产生相同指纹；用于等价跳过与 parity 断言，不是持久化格式。
     */
    public synchronized String fingerprint() {
        return fingerprintOf(applier.adapterId(), declarations, collectionFailure);
    }

    /**
     * 指纹的静态形态（Adapter 侧等价跳过与诊断用）：同一算法对任意声明序列求值。
     */
    public static String fingerprintOf(String adapterId, List<ModificationDeclaration> declarations,
            String collectionFailure) {
        StringBuilder sb = new StringBuilder("modification-plan[v1]:");
        sb.append(adapterId).append(';');
        for (ModificationDeclaration declaration : declarations) {
            sb.append(declaration.kind()).append('|').append(declaration.targetId()).append('|');
            // 单声明内属性按名排序：同一声明的写入顺序不改变语义（同属性后写覆盖）
            new TreeMap<>(declaration.properties()).forEach((name, value) ->
                    sb.append(name).append('=').append(canonicalValue(value)).append(','));
            sb.append(';');
        }
        if (collectionFailure != null) {
            sb.append("failed(").append(collectionFailure).append(')');
        }
        return sb.toString();
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            TreeMap<String, String> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalValue(entry.getValue()));
            }
            sorted.forEach((k, v) -> sb.append(k).append(':').append(v).append(','));
            return sb.append('}').toString();
        }
        if (value instanceof Double || value instanceof Float) {
            // 数值形态归一：5 与 5.0 同值同指纹（setter 侧 int/double 收窄差异不产生不同计划）
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(value);
    }

    // ---- CandidateStatePlan：联合预检 / 联合发布 ----

    @Override
    public void preflight() {
        String failure = collectionFailure;
        if (failure != null) {
            throw new IllegalStateException("modification collection failed: " + failure,
                    collectionError);
        }
        applier.preflight(declarations());
    }

    @Override
    public void publish() {
        applier.apply(declarations());
    }
}
