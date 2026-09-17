package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegistrationBookkeeping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态注册计划的已提交状态（Registry Runtime 侧账本，ticket 16，AC7/AC8）：
 * <b>每实例一份、零 static 可变状态</b>——生产由平台 Adapter 持有单例，测试/harness
 * 自由构造隔离实例。
 *
 * <p>持有两套互辅的账本：
 * <ul>
 *   <li><b>exposed 定义账</b>（{@code key → ExposedEntry}）：每条已提交定义的
 *       fingerprint 与 owner。<b>普通 reload 不移除</b>——脚本不再声明的项保留在账上
 *       （stale/retired），其 fingerprint 继续参与冲突检测（对已暴露 key 的定义变化
 *       永远是第一版冲突，不因 stale 而放开 replace）。显式清理是后续语义，不在本票；</li>
 *   <li><b>claim/stale/mode 账</b>（每类型一个 {@link DynamicRegistrationBookkeeping}，
 *       与旧 {@code DynamicRegistries} 同 label 的既有 prior-art 类）：成功 commit 时
 *       beginReload 全部标记 stale、随后逐条 claim 刷新——claim、stale、mode 由此
 *       可观察（AC8）。</li>
 * </ul>
 *
 * <p>写路径只有一个：{@link DynamicCandidateRegistryPlan#publish()}（commit 点联合发布）
 * 调用的 {@link #commitBatch}。候选失败/取消不会走到这里——账本不被触碰，旧 active
 * 定义继续服务（AC6/AC7）。本类不持有任何 MC 类型——结构上不可能修改 live registry。
 */
public final class DynamicRegistryPlanStore {

    /** 一条已提交（exposed）定义的账目。 */
    public record ExposedEntry(
            DynamicDefinition definition,
            String ownerScriptId,
            long committedGeneration) {}

    /** 与旧 DynamicRegistries 相同的 registry label（claim/stale/mode 账本键）。 */
    public static final String LABEL_ITEM = "item";
    public static final String LABEL_SOUND_EVENT = "sound_event";
    public static final String LABEL_MOB_EFFECT = "mob_effect";

    private final Map<String, ExposedEntry> exposed = new LinkedHashMap<>();
    private final DynamicRegistrationBookkeeping itemBookkeeping = new DynamicRegistrationBookkeeping(LABEL_ITEM);
    private final DynamicRegistrationBookkeeping soundEventBookkeeping =
            new DynamicRegistrationBookkeeping(LABEL_SOUND_EVENT);
    private final DynamicRegistrationBookkeeping mobEffectBookkeeping =
            new DynamicRegistrationBookkeeping(LABEL_MOB_EFFECT);
    private long generation;
    private long committedGeneration = -1;
    private Map<String, ExposedEntry> lastCommittedBatch = Map.of();

    /** 开启一批候选计划（generation 递增；generation-scoped 语义的锚）。 */
    public synchronized DynamicCandidateRegistryPlan beginBatch() {
        return new DynamicCandidateRegistryPlan(this, ++generation);
    }

    /** 是否从未提交过任何定义（reload 集成用它判断「空候选计划是否需要挂入」）。 */
    public synchronized boolean isEmpty() {
        return exposed.isEmpty();
    }

    /** 已提交 generation 序号（从未提交为 -1；成功 commit 才发布计划的观察点）。 */
    public synchronized long committedGeneration() {
        return committedGeneration;
    }

    /** 某个计划键的已暴露定义（冲突检测输入）；未暴露返回 null。 */
    public synchronized ExposedEntry exposedEntry(String key) {
        return exposed.get(key);
    }

    /** 全部已暴露键（stale 含）的防御性快照（诊断/测试）。 */
    public synchronized Map<String, ExposedEntry> exposedSnapshot() {
        return new LinkedHashMap<>(exposed);
    }

    /** 上一批成功提交的定义快照（发布计划的观察面）。 */
    public synchronized List<DynamicDefinition> lastCommittedBatch() {
        return List.copyOf(lastCommittedBatch.values().stream().map(ExposedEntry::definition).toList());
    }

    /** 指定类型的 claim/stale/mode 账本（Registry Runtime/Adapter Interface 的观察点，AC8）。 */
    public DynamicRegistrationBookkeeping bookkeeping(DynamicDefinitionType type) {
        return switch (type) {
            case ITEM -> itemBookkeeping;
            case SOUND_EVENT -> soundEventBookkeeping;
            case MOB_EFFECT -> mobEffectBookkeeping;
        };
    }

    /** 指定类型的 stale id 列表（普通 reload 不物理删除，仅标记）。 */
    public List<String> staleIds(DynamicDefinitionType type) {
        return bookkeeping(type).staleIds();
    }

    /** 全部类型的 stale/retired 查询：{@code minecraft:item|mymod:old} 形式的键列表。 */
    public synchronized List<String> retiredKeys() {
        List<String> retired = new ArrayList<>();
        for (Map.Entry<String, ExposedEntry> entry : exposed.entrySet()) {
            DynamicDefinition definition = entry.getValue().definition();
            if (bookkeeping(definition.type()).staleIds().contains(definition.id())) {
                retired.add(entry.getKey());
            }
        }
        return retired;
    }

    /** 全部 tracked id（stale 含）的 {@code label:id} 快照（诊断）。 */
    public synchronized Set<String> trackedClaims() {
        Set<String> claims = new LinkedHashSet<>();
        for (DynamicDefinitionType type : DynamicDefinitionType.values()) {
            DynamicRegistrationBookkeeping bookkeeping = bookkeeping(type);
            for (String id : bookkeeping.trackedIds()) {
                claims.add(bookkeeping.registry() + ":" + id);
            }
        }
        return claims;
    }

    /** 某条 exposed 定义的当前 claim owner/mode（null = 无账）。 */
    public synchronized DynamicRegistrationBookkeeping.Entry claimOf(DynamicDefinitionType type, String id) {
        return bookkeeping(type).entry(id);
    }

    /**
     * 提交一批计划（唯一写路径；由 {@link DynamicCandidateRegistryPlan#publish()} 在
     * commit 点调用）。契约：preflight 已通过，本方法不得抛出。
     * 顺序：先全部账本 beginReload（stale 标记），再逐条 claim（重声明刷新 stale）——
     * 未重新声明的已暴露项因此停留 stale/retired，账目与定义都不物理删除。
     */
    synchronized void commitBatch(DynamicCandidateRegistryPlan plan) {
        long batchGeneration = plan.generation();
        itemBookkeeping.beginReload();
        soundEventBookkeeping.beginReload();
        mobEffectBookkeeping.beginReload();
        Map<String, ExposedEntry> batch = new LinkedHashMap<>();
        for (DynamicDefinition definition : plan.definitions()) {
            String owner = plan.ownerOf(definition.key());
            bookkeeping(definition.type()).claim(definition.id(), owner, definition.mode());
            ExposedEntry entry = new ExposedEntry(definition, owner, batchGeneration);
            exposed.put(definition.key(), entry);
            batch.put(definition.key(), entry);
        }
        this.lastCommittedBatch = batch;
        this.committedGeneration = batchGeneration;
    }

    /** 供冲突信息使用：key 当前是否处于 stale（定义被声明过但本批未重声明）。 */
    synchronized boolean isRetired(String key) {
        ExposedEntry entry = exposed.get(key);
        if (entry == null) {
            return false;
        }
        DynamicDefinition definition = entry.definition();
        DynamicRegistrationBookkeeping.Entry claim = bookkeeping(definition.type()).entry(definition.id());
        return claim != null && claim.stale();
    }

}
