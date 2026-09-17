// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对（block 半边是 26.x 独有——本节点
// 无 BlockEvents.modification 总线；组件发布走 Item.components 反射字段；
// fireResistant 是 FIRE_RESISTANT:Unit 组件，无需 damage type registry）。
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.modification.ModificationApplier;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Item modification 的 domain owner 与平台 Adapter（ticket 39，1.21.1 成对文件）。
 * 语义契约见 26.x 同名文件；本节点的差异（见 ticket 39 REPORT 五节点差异表）：
 * <ul>
 *   <li>无 block 半边（1.21.1 无 {@code BlockEvents.modification}）；</li>
 *   <li>组件发布走反射写 {@code Item#components}（26.x 是
 *       {@code Holder.Reference#bindComponents}）；</li>
 *   <li>fireResistant 是 {@code FIRE_RESISTANT:Unit} 组件（26.x 是 DAMAGE_RESISTANT
 *       + IS_FIRE HolderSet，需要 server 的 damage type registry）——本节点无需
 *       server 绑定，{@link #bindServer(MinecraftServer)} 只保留对称生命周期。</li>
 * </ul>
 */
public final class ModificationDomainOwner implements CandidateDomainCollector, ModificationApplier, AutoCloseable {

    /** 收集器标识（与 {@code ModificationCandidatePlan.DOMAIN} 对齐）。 */
    public static final String DOMAIN = ModificationCandidatePlan.DOMAIN;

    /** 与 26.x 对齐的组件上限（1.21.1 无 ABSOLUTE_MAX_STACK_SIZE 常量，取同值）。 */
    private static final int MAX_STACK_SIZE_LIMIT = 99;

    /** item 原始组件基线（进程级例外：默认组件注册期冻结；实例字段，root 持有）。 */
    private final Map<String, DataComponentMap> itemBaselines = new java.util.LinkedHashMap<>();
    /** 最近一次结局（诊断公开面：区分 active / blocked / recovery-failed / restored）。 */
    private volatile Diagnostics lastDiagnostics = new Diagnostics(Outcome.INITIAL, "none", 0, 0, List.of(), null);
    /** 最近成功应用的计划指纹（初始 generation 收集的等价跳过依据）。 */
    private volatile String lastAppliedFingerprint;

    public ModificationDomainOwner() {
    }

    /** 对称生命周期占位（本节点 fireResistant 无 registry 依赖）。 */
    public void bindServer(MinecraftServer server) {
    }

    /** 对称生命周期占位。 */
    public void clearServer() {
    }

    // ---- CandidateDomainCollector（DOMAIN_PLAN 阶段） ----

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public ScriptType scriptType() {
        return ScriptType.SERVER;
    }

    @Override
    public void collect(CandidateDomainCollector.Handle handle) {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(this);
        handle.dispatch(ItemEvents.MODIFICATION, new ItemModificationEventJS(plan));
        handle.registerPlan(plan);
    }

    // ---- 初始 generation（服务器启动收集点） ----

    /** 初始 generation 的收集 + 预检 + 应用（与 26.x 同语义；无 block 半边）。 */
    public void applyInitialPlan(MinecraftServer server) {
        bindServer(server);
        ModificationCandidatePlan plan = new ModificationCandidatePlan(this);
        try {
            ItemEvents.MODIFICATION.post(new ItemModificationEventJS(plan));
        } catch (Throwable t) {
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "startup-dispatch",
                    0, 0, List.of(), String.valueOf(t));
            NekoJS.LOGGER.error("NekoJS modification startup dispatch failed; keeping existing values", t);
            return;
        }
        String fingerprint = ModificationCandidatePlan.fingerprintOf(
                adapterId(), plan.declarations(), plan.collectionFailure());
        if (fingerprint.equals(lastAppliedFingerprint)) {
            lastDiagnostics = new Diagnostics(Outcome.SKIPPED_IDENTICAL, "startup",
                    countKind(plan.declarations(), "item"), 0, List.of(), null);
            return;
        }
        try {
            plan.preflight();
        } catch (Throwable t) {
            lastDiagnostics = new Diagnostics(Outcome.BLOCKED, "startup",
                    countKind(plan.declarations(), "item"), 0, List.of(), String.valueOf(t));
            NekoJS.LOGGER.error("NekoJS modification plan blocked at startup (keeping existing values): {}", t.toString());
            return;
        }
        apply(plan.declarations(), "startup");
    }

    // ---- ModificationApplier：联合预检 / 应用 ----

    @Override
    public String adapterId() {
        return "1.21.1-node";
    }

    @Override
    public void preflight(List<ModificationDeclaration> declarations) {
        for (ModificationDeclaration declaration : declarations) {
            if (!"item".equals(declaration.kind())) {
                throw new IllegalArgumentException(
                        "Unknown modification kind '" + declaration.kind() + "': expected item (block is 26.x only)");
            }
            preflightItem(declaration);
        }
    }

    private void preflightItem(ModificationDeclaration declaration) {
        ResourceLocation id = ResourceLocation.tryParse(declaration.targetId());
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + declaration.targetId());
        }
        DataComponentMap base = baselineItemComponents(declaration.targetId(), item);
        Map<String, Object> properties = declaration.properties();
        Integer stack = intProperty(properties, "maxStackSize");
        Integer damage = intProperty(properties, "maxDamage");
        if (stack != null && (stack < 1 || stack > MAX_STACK_SIZE_LIMIT)) {
            throw new IllegalArgumentException("Invalid maxStackSize " + stack
                    + ": must be between 1 and " + MAX_STACK_SIZE_LIMIT);
        }
        if (damage != null && damage < 0) {
            throw new IllegalArgumentException("Invalid maxDamage " + damage + ": must be >= 0");
        }
        int effectiveStack = stack != null ? stack
                : base.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
        int effectiveDamage = damage != null ? damage
                : base.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        if (effectiveStack > 1 && effectiveDamage > 0) {
            throw new IllegalArgumentException("Cannot combine maxStackSize=" + effectiveStack
                    + " with maxDamage=" + effectiveDamage
                    + ": stackable items cannot be damageable (set maxStackSize = 1)");
        }
    }

    /**
     * 应用（commit 点 / 初始 generation）：恢复全部 NekoJS 持有基线 → 按声明顺序应用
     * 完整新计划。意外抛出时尽力恢复、诊断 RECOVERY_FAILED、异常上抛（与 26.x 同）。
     */
    @Override
    public void apply(List<ModificationDeclaration> declarations) {
        apply(declarations, "commit");
    }

    private void apply(List<ModificationDeclaration> declarations, String source) {
        List<String> restored = new ArrayList<>();
        try {
            for (Map.Entry<String, DataComponentMap> entry : itemBaselines.entrySet()) {
                Item item = resolveItem(entry.getKey());
                if (item != null) {
                    ItemModificationEventJS.applyComponents(item, entry.getValue());
                    restored.add("item:" + entry.getKey());
                }
            }
            for (ModificationDeclaration declaration : declarations) {
                applyItem(declaration);
            }
            lastAppliedFingerprint = ModificationCandidatePlan.fingerprintOf(adapterId(), declarations, null);
            boolean restoredOnly = declarations.isEmpty();
            lastDiagnostics = new Diagnostics(restoredOnly ? Outcome.RESTORED : Outcome.APPLIED, source,
                    countKind(declarations, "item"), 0, List.copyOf(restored), null);
            if (!declarations.isEmpty()) {
                NekoJS.LOGGER.info("NekoJS modifications applied at {} ({} item(s))",
                        source, countKind(declarations, "item"));
            }
        } catch (Throwable t) {
            try {
                for (Map.Entry<String, DataComponentMap> entry : itemBaselines.entrySet()) {
                    Item item = resolveItem(entry.getKey());
                    if (item != null) ItemModificationEventJS.applyComponents(item, entry.getValue());
                }
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            // 失败批次不产生「最近成功应用」：复位指纹（与 26.x 同语义，见 AC7 注释）
            lastAppliedFingerprint = null;
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, source,
                    countKind(declarations, "item"), 0, List.copyOf(restored), String.valueOf(t));
            throw t instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("modification apply failed", t);
        }
    }

    private void applyItem(ModificationDeclaration declaration) {
        Item item = resolveItem(declaration.targetId());
        if (item == null) {
            throw new IllegalStateException("Item disappeared between preflight and apply: "
                    + declaration.targetId());
        }
        DataComponentMap base = baselineItemComponents(declaration.targetId(), item);
        DataComponentMap.Builder builder = DataComponentMap.builder().addAll(base);
        applyItemProperties(builder, declaration.properties());
        ItemModificationEventJS.applyComponents(item, builder.build());
    }

    private void applyItemProperties(DataComponentMap.Builder builder, Map<String, Object> properties) {
        if (properties.containsKey("maxStackSize")) {
            builder.set(DataComponents.MAX_STACK_SIZE, (Integer) properties.get("maxStackSize"));
        }
        if (properties.containsKey("maxDamage")) {
            builder.set(DataComponents.MAX_DAMAGE, (Integer) properties.get("maxDamage"));
        }
        if (properties.containsKey("rarity")) {
            builder.set(DataComponents.RARITY,
                    Rarity.valueOf(String.valueOf(properties.get("rarity")).toUpperCase(java.util.Locale.ROOT)));
        }
        if (properties.containsKey("fireResistant")) {
            boolean fireResistant = Boolean.TRUE.equals(properties.get("fireResistant"));
            builder.set(DataComponents.FIRE_RESISTANT, fireResistant ? Unit.INSTANCE : null);
        }
    }

    private DataComponentMap baselineItemComponents(String targetId, Item item) {
        return itemBaselines.computeIfAbsent(targetId, key -> item.components());
    }

    private static Item resolveItem(String targetId) {
        ResourceLocation id = ResourceLocation.tryParse(targetId);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static Integer intProperty(Map<String, Object> properties, String name) {
        Object value = properties.get(name);
        return value == null ? null : ((Number) value).intValue();
    }

    private static int countKind(List<ModificationDeclaration> declarations, String kind) {
        return (int) declarations.stream().filter(d -> d.kind().equals(kind)).count();
    }

    // ---- 诊断（公开观察面） ----

    /** 最近一次收集/应用结局。 */
    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /** root close：恢复全部持有基线并清空（独立测试 root 从干净状态开始）。 */
    @Override
    public void close() {
        try {
            for (Map.Entry<String, DataComponentMap> entry : itemBaselines.entrySet()) {
                Item item = resolveItem(entry.getKey());
                if (item != null) ItemModificationEventJS.applyComponents(item, entry.getValue());
            }
        } finally {
            itemBaselines.clear();
            lastAppliedFingerprint = null;
        }
    }

    /** 修改域结局（与 26.x 同一枚举；block 计数恒 0）。 */
    public enum Outcome {
        INITIAL, APPLIED, RESTORED, SKIPPED_IDENTICAL, BLOCKED, RECOVERY_FAILED
    }

    /** 修改域最近一次结局的观察记录（公开诊断面）。 */
    public record Diagnostics(Outcome outcome, String source, int itemDeclarations, int blockDeclarations,
                              List<String> restoredTargets, String detail) {
    }
}
