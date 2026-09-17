package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.core.state.GlobalStateException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一批 generation-scoped 的 <b>inert</b> 动态注册候选计划（ticket 16，AC1/AC6/AC7）：
 * 候选期只收集定义与错误、preflight 做同 key 冲突检测，commit 点（经票 10
 * {@link CandidateStatePlan} 联合边界）才把批次发布进 {@link DynamicRegistryPlanStore}。
 *
 * <p><b>inert 保证</b>：本类（与整个 plan 包）不持有任何 MC/loader 类型——候选期
 * 不修改 live registry、不挂生产 callback、不提前发布对外 binding；「Adapter 请求」
 * 是 {@link DynamicAdapterRequest} 描述（无执行通道）。候选失败时计划整体丢弃，
 * store 无任何写入（失败清理＝计划对象不可达，无临时资源残留）。
 *
 * <p><b>冲突语义（第一版）</b>：对已暴露 key（active 或 stale/retired）的定义变化
 * （fingerprint 不同）导致<b>整批冲突失败</b>——旧 active 定义继续服务；
 * remove/replace/modify 不存在于本计划（动作集合封闭为 register）。同批同 key
 * 重复声明：fingerprint 相同＝幂等去重，不同＝收集期 fail-fast。完全相同的定义
 * 在重复 reload 中 fingerprint 相同，重声明即重 claim（不冲突）。
 */
public final class DynamicCandidateRegistryPlan implements CandidateStatePlan {

    /** 联合失败结果中的 domain 前缀（state-plan-preflight:<domain> / ReloadFailureReport.domain）。 */
    public static final String DOMAIN = "dynamic-registry";

    private final DynamicRegistryPlanStore store;
    private final long generation;
    private final Map<String, DynamicDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> owners = new LinkedHashMap<>();
    private final List<String> collectionErrors = new ArrayList<>();
    private boolean preflightPassed;
    private boolean published;

    DynamicCandidateRegistryPlan(DynamicRegistryPlanStore store, long generation) {
        this.store = store;
        this.generation = generation;
    }

    // ---- 收集（事件 payload / 候选收集器调用） ----

    /**
     * 收集一条定义。同批同 key：fingerprint 相同 → 幂等返回已收集条目；不同 →
     * 收集期 fail-fast（带两条定义的可定位差异）。调用方（payload）负责把异常记为
     * collection error（毒化整批）后向监听器传播。
     */
    public DynamicDefinition add(
            DynamicDefinitionType type, String rawId, DynamicDefinitionBuilder builder,
            String ownerScriptId, String origin) {
        DynamicDefinition definition = DynamicDefinition.of(type, rawId, builder);
        String key = definition.key();
        DynamicDefinition existing = definitions.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(definition.fingerprint())) {
                throw new IllegalArgumentException(
                        "Duplicate declaration of '" + key + "' in the same batch with a different definition: "
                                + existing.describe() + " (from " + owners.get(key) + ") vs "
                                + definition.describe() + " (from " + safeOwner(ownerScriptId) + ");"
                                + " the whole batch fails — remove/replace/modify is not part of the first version");
            }
            return existing;
        }
        definitions.put(key, definition);
        owners.put(key, safeOwner(ownerScriptId));
        return definition;
    }

    /** 记录一条收集期错误（payload 捕获后毒化整批：preflight 必失败，批次不发布）。 */
    public void noteCollectionError(String scriptId, Throwable error) {
        collectionErrors.add("[" + safeOwner(scriptId) + "] " + error.getClass().getSimpleName() + ": "
                + error.getMessage());
    }

    // ---- CandidateStatePlan（联合边界） ----

    @Override
    public String domain() {
        return DOMAIN;
    }

    /**
     * 联合预检（STATE_PLAN 阶段）：收集错误 → 整批失败；对每条定义，已暴露同 key
     * fingerprint 不同 → 整批冲突失败（旧 active 继续服务）。抛
     * {@link GlobalStateException}（domain 精确归因），不产生对外可见副作用。
     */
    @Override
    public void preflight() {
        if (!collectionErrors.isEmpty()) {
            throw new GlobalStateException("dynamic-registry-collection",
                    "dynamic registry candidate batch " + generation + " failed collection: "
                            + String.join("; ", collectionErrors));
        }
        for (DynamicDefinition definition : definitions.values()) {
            DynamicRegistryPlanStore.ExposedEntry exposed = store.exposedEntry(definition.key());
            if (exposed == null) {
                continue;
            }
            if (!exposed.definition().fingerprint().equals(definition.fingerprint())) {
                boolean retired = store.isRetired(definition.key());
                throw new GlobalStateException("dynamic-registry-conflict",
                        "definition of '" + definition.key() + "' changed" + (retired ? " (stale/retired)" : "")
                                + ": exposed fingerprint " + exposed.definition().fingerprint()
                                + " (" + exposed.definition().describe() + ", owner " + exposed.ownerScriptId()
                                + ", generation " + exposed.committedGeneration()
                                + ") vs batch " + generation + " fingerprint " + definition.fingerprint()
                                + " (" + definition.describe() + "); the whole batch fails and the old active"
                                + " definitions keep serving — replace/update is not part of the first version");
            }
        }
        preflightPassed = true;
    }

    /**
     * 联合发布（commit 点）：把批次提交进 store（claim 刷新 / stale 标记 / exposed 账）。
     * 契约：preflight 通过后不得抛出——本方法只做预检已覆盖的确定性写账；
     * 跳过预检直接 publish 被拒绝（联合边界外无发布路径）。
     */
    @Override
    public void publish() {
        if (published) {
            throw new IllegalStateException("dynamic registry plan " + generation + " already published");
        }
        if (!preflightPassed) {
            throw new IllegalStateException(
                    "dynamic registry plan " + generation + " was never preflighted; publish joins the joint"
                            + " candidate boundary only (STATE_PLAN preflight → commit publish)");
        }
        store.commitBatch(this);
        published = true;
    }

    // ---- 观察面（测试/诊断/Adapter 请求） ----

    /** 批次 generation（generation-scoped 语义锚）。 */
    public long generation() {
        return generation;
    }

    /** 批内定义（提交顺序、防御性副本）。 */
    public List<DynamicDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    /** 批内某 key 的 owner scriptId（无则 {@code <unknown>}）。 */
    public String ownerOf(String key) {
        return owners.getOrDefault(key, "<unknown>");
    }

    /** 是否存在收集期错误（毒化标记）。 */
    public boolean hasCollectionErrors() {
        return !collectionErrors.isEmpty();
    }

    /** 收集期错误副本（诊断）。 */
    public List<String> collectionErrors() {
        return List.copyOf(collectionErrors);
    }

    /** 本计划是否已发布（成功 commit 的观察点）。 */
    public boolean isPublished() {
        return published;
    }

    /**
     * 本批次的 inert Adapter 请求（AC7）：每条定义一条 {@code register} 描述，
     * generation-scoped。这不是执行通道——Adapter 激活由事务/同步 gate（票 21）裁决。
     */
    public List<DynamicAdapterRequest> adapterRequests() {
        List<DynamicAdapterRequest> requests = new ArrayList<>();
        definitions.values().forEach(definition ->
                requests.add(new DynamicAdapterRequest(definition, generation, owners.get(definition.key()))));
        return requests;
    }

    /** 批内是否声明了某个 key（测试/诊断）。 */
    public boolean declaresKey(String key) {
        return definitions.containsKey(key);
    }

    private static String safeOwner(String ownerScriptId) {
        return ownerScriptId == null || ownerScriptId.isBlank() ? "<unknown>" : ownerScriptId;
    }
}
