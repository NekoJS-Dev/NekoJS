//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.modification.ModificationApplier;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Item/Block modification 的 domain owner 与平台 Adapter（ticket 39）：
 * <b>root 授权的既有 domain owner</b>——实例由平台装配创建并经
 * {@code NekoRuntimeRoot#registerDomainCollector} 注册（root 持有生命周期，进程级归类：
 * item 默认组件与 block Properties 在注册期冻结、从不下变，基线是进程级例外但按 root
 * 生命周期持有，root close 恢复并清空——独立测试 root 不互相污染）。不是新的公开
 * Modification Runtime / 第二框架：只实现 common 的
 * {@link CandidateDomainCollector}（收集挂载点）与 {@link ModificationApplier}（应用接缝）。
 *
 * <p>两个合法收集点：
 * <ul>
 *   <li><b>事务 reload（DOMAIN_PLAN）</b>：{@link #collect} 把收集事件派发进<b>候选</b>的
 *       挂起监听器，产出 inert {@link ModificationCandidatePlan} 挂入联合边界——与
 *       global/shared 顶层写集联合成功或失败（AC9）；</li>
 *   <li><b>初始 generation（服务器 about-to-start）</b>：{@link #applyInitialPlan} 把收集
 *       事件派发给 active 总线，preflight 通过后在同一 owner 内应用（无旧 active 时
 *       「旧值」= vanilla 基线；不通过 → 整批 blocked，保持 vanilla，无部分修改）。</li>
 * </ul>
 *
 * <p>应用契约（AC7）：先恢复全部 NekoJS 持有基线，再按声明顺序应用完整新计划——
 * 声明移除的修改在成功 reload 后回到基线（旧 item 路径的静默 stale 不再出现）。
 * 恢复/apply 只承诺 NekoJS 拥有且已证明可恢复的字段（item 默认组件 Map、block 六属性
 * 的三处副本）；不承诺回滚其他 mod、世界、网络或文件副作用（spec 09 外部副作用边界）。
 * 26.x 与 1.21.1 的组件发布机制差异（bindComponents vs 反射字段）在各自
 * {@code ItemModificationEventJS} 成对文件；block 半边是 26.x 独有（1.21.1 无此总线）。
 */
public final class ModificationDomainOwner implements CandidateDomainCollector, ModificationApplier, AutoCloseable {

    /** 收集器标识（与 {@code ModificationCandidatePlan.DOMAIN} 对齐）。 */
    public static final String DOMAIN = ModificationCandidatePlan.DOMAIN;

    /** 26.x 组件上限：{@code Item#ABSOLUTE_MAX_STACK_SIZE}。 */
    private static final int MAX_STACK_SIZE_LIMIT = 99;

    /** item 原始组件基线（进程级例外：默认组件注册期冻结；实例字段，root 持有）。 */
    private final Map<String, DataComponentMap> itemBaselines = new java.util.LinkedHashMap<>();
    /** block 原始属性基线（26.x 独有；含原始光照函数）。 */
    private final Map<String, BlockModificationJS.PropertySnapshot> blockBaselines = new java.util.LinkedHashMap<>();
    /** fireResistant 应用需要的 damage type registry（26.x DAMAGE_RESISTANT 面）；about-to-start 绑定。 */
    private volatile MinecraftServer boundServer;
    /** 最近一次结局（诊断公开面：区分 active / blocked / recovery-failed / restored）。 */
    private volatile Diagnostics lastDiagnostics = new Diagnostics(Outcome.INITIAL, "none", 0, 0, List.of(), null);
    /** 最近成功应用的计划指纹（初始 generation 收集的等价跳过依据）。 */
    private volatile String lastAppliedFingerprint;

    public ModificationDomainOwner() {
    }

    // ---- server 绑定（fireResistant 的 registry access 来源） ----

    /** 服务器 about-to-start 绑定（早于任何事务 reload 的 DOMAIN_PLAN 可能需要它）。 */
    public void bindServer(MinecraftServer server) {
        this.boundServer = server;
    }

    /** 服务器停止解除绑定。 */
    public void clearServer() {
        this.boundServer = null;
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
        handle.dispatch(BlockEvents.MODIFICATION, new BlockModificationEventJS(plan));
        handle.registerPlan(plan);
    }

    // ---- 初始 generation（服务器启动收集点） ----

    /**
     * 初始 generation 的收集 + 预检 + 应用（旧 {@code ItemModificationEventJS.fire} /
     * {@code BlockModificationEventJS.fire} 的收口替代）：收集事件派发给 active 总线
     * 监听器；preflight 失败 → 整批 blocked（保持 vanilla/既有值，无部分修改），诊断
     * 记录首个错误；与上一次成功应用指纹相同则跳过重放（等价幂等，不产生第二次应用）。
     */
    public void applyInitialPlan(MinecraftServer server) {
        bindServer(server);
        ModificationCandidatePlan plan = new ModificationCandidatePlan(this);
        try {
            ItemEvents.MODIFICATION.post(new ItemModificationEventJS(plan));
            BlockEvents.MODIFICATION.post(new BlockModificationEventJS(plan));
        } catch (Throwable t) {
            // EventBusJS.post 吞掉监听器异常；这里只兜派发自身的意外（总线状态等）
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "startup-dispatch",
                    0, 0, List.of(), String.valueOf(t));
            NekoJS.LOGGER.error("NekoJS modification startup dispatch failed; keeping existing values", t);
            return;
        }
        String fingerprint = ModificationCandidatePlan.fingerprintOf(
                adapterId(), plan.declarations(), plan.collectionFailure());
        if (fingerprint.equals(lastAppliedFingerprint)) {
            lastDiagnostics = new Diagnostics(Outcome.SKIPPED_IDENTICAL, "startup",
                    countKind(plan.declarations(), "item"), countKind(plan.declarations(), "block"), List.of(), null);
            return;
        }
        try {
            plan.preflight();
        } catch (Throwable t) {
            lastDiagnostics = new Diagnostics(Outcome.BLOCKED, "startup",
                    countKind(plan.declarations(), "item"), countKind(plan.declarations(), "block"), List.of(), String.valueOf(t));
            NekoJS.LOGGER.error("NekoJS modification plan blocked at startup (keeping existing values): {}", t.toString());
            return;
        }
        apply(plan.declarations(), "startup");
    }

    // ---- ModificationApplier：联合预检 / 应用 ----

    @Override
    public String adapterId() {
        return "26.x-shared";
    }

    @Override
    public void preflight(List<ModificationDeclaration> declarations) {
        for (ModificationDeclaration declaration : declarations) {
            switch (declaration.kind()) {
                case "item" -> preflightItem(declaration);
                case "block" -> preflightBlock(declaration);
                default -> throw new IllegalArgumentException(
                        "Unknown modification kind '" + declaration.kind() + "': expected item or block");
            }
        }
    }

    private void preflightItem(ModificationDeclaration declaration) {
        Identifier id = Identifier.tryParse(declaration.targetId());
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
        if (Boolean.TRUE.equals(properties.get("fireResistant")) && boundServer == null) {
            // Adapter 拒绝：26.x DAMAGE_RESISTANT 需要 damage type registry（server 未绑定）
            throw new IllegalArgumentException(
                    "fireResistant = true needs a bound server (damage type registry); blocked at: "
                            + declaration.targetId());
        }
    }

    private void preflightBlock(ModificationDeclaration declaration) {
        Identifier id = Identifier.tryParse(declaration.targetId());
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Unknown block: " + declaration.targetId());
        }
        Map<String, Object> properties = declaration.properties();
        Float hardness = floatProperty(properties, "hardness");
        Float resistance = floatProperty(properties, "resistance");
        Integer lightLevel = intProperty(properties, "lightLevel");
        Float friction = floatProperty(properties, "friction");
        if (hardness != null && hardness < 0) {
            throw new IllegalArgumentException("Invalid hardness " + hardness + ": must be >= 0");
        }
        if (resistance != null && resistance < 0) {
            throw new IllegalArgumentException("Invalid resistance " + resistance + ": must be >= 0");
        }
        if (lightLevel != null && (lightLevel < 0 || lightLevel > 15)) {
            throw new IllegalArgumentException("Invalid lightLevel " + lightLevel + ": must be between 0 and 15");
        }
        if (friction != null && (friction < 0 || friction > 1)) {
            throw new IllegalArgumentException("Invalid friction " + friction + ": must be between 0 and 1");
        }
    }

    /**
     * 应用（commit 点 / 初始 generation）：恢复全部 NekoJS 持有基线 → 按声明顺序应用
     * 完整新计划（同目标后声明整体替换前声明——从基线叠加，保持旧可观察顺序）。
     * preflight 通过后仍意外抛出时：尽力恢复基线、诊断 RECOVERY_FAILED、异常上抛
     * （{@code CandidateStatePlan#publish} 契约路径：计划部分副作用不承诺深回滚）。
     */
    @Override
    public void apply(List<ModificationDeclaration> declarations) {
        apply(declarations, "commit");
    }

    private void apply(List<ModificationDeclaration> declarations, String source) {
        List<String> restored = new ArrayList<>();
        try {
            // (1) 恢复 NekoJS 持有基线（声明移除语义：不再声明的目标回到原值）
            for (Map.Entry<String, DataComponentMap> entry : itemBaselines.entrySet()) {
                Item item = resolveItem(entry.getKey());
                if (item != null) {
                    ItemModificationEventJS.applyComponents(item, entry.getValue());
                    restored.add("item:" + entry.getKey());
                }
            }
            for (Map.Entry<String, BlockModificationJS.PropertySnapshot> entry : blockBaselines.entrySet()) {
                Block block = resolveBlock(entry.getKey());
                if (block != null) {
                    entry.getValue().applyTo(block);
                    restored.add("block:" + entry.getKey());
                }
            }
            // (2) 按声明顺序应用（每条从基线叠加）
            for (ModificationDeclaration declaration : declarations) {
                switch (declaration.kind()) {
                    case "item" -> applyItem(declaration);
                    case "block" -> applyBlock(declaration);
                    default -> throw new IllegalArgumentException(
                            "Unknown modification kind '" + declaration.kind() + "'");
                }
            }
            lastAppliedFingerprint = ModificationCandidatePlan.fingerprintOf(adapterId(), declarations, null);
            boolean restoredOnly = declarations.isEmpty();
            lastDiagnostics = new Diagnostics(restoredOnly ? Outcome.RESTORED : Outcome.APPLIED, source,
                    countKind(declarations, "item"), countKind(declarations, "block"), List.copyOf(restored), null);
            if (!declarations.isEmpty()) {
                NekoJS.LOGGER.info("NekoJS modifications applied at {} ({} item(s), {} block(s))",
                        source, countKind(declarations, "item"), countKind(declarations, "block"));
            }
        } catch (Throwable t) {
            // 尽力恢复后仍如实报告（不宣称成功；静默 stale 不算成功）
            try {
                for (Map.Entry<String, DataComponentMap> entry : itemBaselines.entrySet()) {
                    Item item = resolveItem(entry.getKey());
                    if (item != null) ItemModificationEventJS.applyComponents(item, entry.getValue());
                }
                for (Map.Entry<String, BlockModificationJS.PropertySnapshot> entry : blockBaselines.entrySet()) {
                    Block block = resolveBlock(entry.getKey());
                    if (block != null) entry.getValue().applyTo(block);
                }
            } catch (Throwable suppressed) {
                t.addSuppressed(suppressed);
            }
            // 失败批次不产生「最近成功应用」：复位指纹，避免下次启动收集按等价跳过
            // 误报 SKIPPED_IDENTICAL 掩盖「实际停在基线」的事实（AC7：静默 stale 不算成功）
            lastAppliedFingerprint = null;
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, source,
                    countKind(declarations, "item"), countKind(declarations, "block"),
                    List.copyOf(restored), String.valueOf(t));
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
        applyItemProperties(builder, base, declaration.properties());
        ItemModificationEventJS.applyComponents(item, builder.build());
    }

    /** 声明属性 → 组件写入（包内可见：ItemModificationComponentsTest 直接驱动生产应用路径）。 */
    void applyItemProperties(DataComponentMap.Builder builder, DataComponentMap base,
            Map<String, Object> properties) {
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
            builder.set(DataComponents.DAMAGE_RESISTANT, fireResistant ? createFireResistance() : null);
        }
        if (properties.containsKey("food")) {
            Object value = properties.get("food");
            if (value == null) {
                ItemModificationComponents.removeFood(builder, base);
            } else {
                @SuppressWarnings("unchecked")
                ItemModificationComponents.FoodSpec food = ItemModificationComponents.FoodSpec
                        .fromNormalized((Map<String, Object>) value);
                ItemModificationComponents.applyFood(builder, base, food);
            }
        }
        if (properties.containsKey("tool")) {
            Object value = properties.get("tool");
            if (value == null) {
                builder.set(DataComponents.TOOL, null);
            } else {
                @SuppressWarnings("unchecked")
                ItemModificationComponents.ToolSpec tool = ItemModificationComponents.ToolSpec
                        .fromNormalized((Map<String, Object>) value);
                ItemModificationComponents.applyTool(builder, tool);
            }
        }
        if (properties.containsKey("attackDamage") || properties.containsKey("attackSpeed")) {
            Double attackDamage = (Double) properties.get("attackDamage");
            Double attackSpeed = (Double) properties.get("attackSpeed");
            ItemModificationComponents.applyAttributes(builder, base, attackDamage, attackSpeed);
        }
    }

    private void applyBlock(ModificationDeclaration declaration) {
        Block block = resolveBlock(declaration.targetId());
        if (block == null) {
            throw new IllegalStateException("Block disappeared between preflight and apply: "
                    + declaration.targetId());
        }
        blockBaselines.computeIfAbsent(declaration.targetId(), key -> BlockModificationJS.PropertySnapshot.capture(block));
        BlockModificationJS view = new BlockModificationJS(block);
        Map<String, Object> properties = declaration.properties();
        if (properties.containsKey("hardness")) view.setHardness((Float) properties.get("hardness"));
        if (properties.containsKey("resistance")) view.setResistance((Float) properties.get("resistance"));
        if (properties.containsKey("lightLevel")) view.setLightLevel((Integer) properties.get("lightLevel"));
        if (properties.containsKey("requiresTool")) view.setRequiresTool((Boolean) properties.get("requiresTool"));
        if (properties.containsKey("friction")) view.setFriction((Float) properties.get("friction"));
        if (properties.containsKey("jumpFactor")) view.setJumpFactor((Float) properties.get("jumpFactor"));
        view.applyTo(block);
    }

    /** item 基线（首改时捕获原始组件；此后恒为同一份进程级基线）。 */
    private DataComponentMap baselineItemComponents(String targetId, Item item) {
        return itemBaselines.computeIfAbsent(targetId, key -> item.components());
    }

    private static Item resolveItem(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static Block resolveBlock(String targetId) {
        Identifier id = Identifier.tryParse(targetId);
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    /**
     * 26.x 走 DAMAGE_RESISTANT 组件（指向 IS_FIRE damage type tag），需要 registry access。
     * 与 vanilla {@code Item.Properties#fireResistant()} 同构。
     */
    private DamageResistant createFireResistance() {
        MinecraftServer server = boundServer;
        if (server == null) {
            throw new IllegalStateException("fireResistant requires a bound server (damage type registry)");
        }
        return new DamageResistant(
                server.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FIRE));
    }

    private static Integer intProperty(Map<String, Object> properties, String name) {
        Object value = properties.get(name);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Float floatProperty(Map<String, Object> properties, String name) {
        Object value = properties.get(name);
        return value == null ? null : ((Number) value).floatValue();
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
            for (Map.Entry<String, BlockModificationJS.PropertySnapshot> entry : blockBaselines.entrySet()) {
                Block block = resolveBlock(entry.getKey());
                if (block != null) entry.getValue().applyTo(block);
            }
        } finally {
            itemBaselines.clear();
            blockBaselines.clear();
            lastAppliedFingerprint = null;
            boundServer = null;
        }
    }

    /** 修改域结局（诊断区分 active / blocked / recovery-failed / restored，AC7）。 */
    public enum Outcome {
        /** 尚无收集/应用发生。 */
        INITIAL,
        /** 新计划已应用（active 修改 = 该计划）。 */
        APPLIED,
        /** 空计划应用：基线已恢复（声明移除语义的成功形态）。 */
        RESTORED,
        /** 与最近成功应用等价的初始收集被跳过（幂等，不产生第二次应用）。 */
        SKIPPED_IDENTICAL,
        /** 预检拒绝：整批未提交，既有值保持。 */
        BLOCKED,
        /** 应用/恢复意外失败：已尽力恢复，不宣称成功。 */
        RECOVERY_FAILED
    }

    /** 修改域最近一次结局的观察记录（公开诊断面）。 */
    public record Diagnostics(Outcome outcome, String source, int itemDeclarations, int blockDeclarations,
                              List<String> restoredTargets, String detail) {
    }
}
//?}
