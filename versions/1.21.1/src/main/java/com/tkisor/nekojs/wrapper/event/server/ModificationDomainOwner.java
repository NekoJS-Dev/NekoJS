// 1.21.1 implementation, paired with the 26.x file of the same name under the version tree's src/.
// The block half exists only on 26.x: this node has no BlockEvents.modification bus, publishes
// components through the reflective Item.components field, and models fireResistant as the
// FIRE_RESISTANT:Unit component, so no damage type registry is needed.
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
 * Domain owner and platform adapter for item modification; the 1.21.1 twin of the 26.x file,
 * which carries the shared semantics. This node differs in:
 * <ul>
 *   <li>no block half (1.21.1 has no {@code BlockEvents.modification});</li>
 *   <li>components are published by writing the {@code Item#components} field reflectively
 *       (26.x calls {@code Holder.Reference#bindComponents});</li>
 *   <li>fireResistant is the {@code FIRE_RESISTANT:Unit} component (26.x uses DAMAGE_RESISTANT
 *       plus an IS_FIRE HolderSet, which needs the server's damage type registry). This node
 *       needs no server binding, so {@link #bindServer(MinecraftServer)} only keeps the
 *       lifecycle symmetric.</li>
 * </ul>
 */
public final class ModificationDomainOwner implements CandidateDomainCollector, ModificationApplier, AutoCloseable {

    /** Collector id, aligned with {@code ModificationCandidatePlan.DOMAIN}. */
    public static final String DOMAIN = ModificationCandidatePlan.DOMAIN;

    /** Stack size limit aligned with 26.x; 1.21.1 has no ABSOLUTE_MAX_STACK_SIZE constant, so the
     *  same value is used here. */
    private static final int MAX_STACK_SIZE_LIMIT = 99;

    /** Original component baseline per item (process-wide exception: frozen while the default
     *  components are registered; an instance field owned by the root). */
    private final Map<String, DataComponentMap> itemBaselines = new java.util.LinkedHashMap<>();
    /** Outcome of the last run, exposed for diagnostics: tells active, blocked, recovery-failed and
     *  restored apart. */
    private volatile Diagnostics lastDiagnostics = new Diagnostics(Outcome.INITIAL, "none", 0, 0, List.of(), null);
    /** Fingerprint of the plan that was applied last successfully; the initial generation uses it to
     *  skip an identical plan. */
    private volatile String lastAppliedFingerprint;

    public ModificationDomainOwner() {
    }

    /** Symmetric lifecycle placeholder: fireResistant on this node has no registry dependency. */
    public void bindServer(MinecraftServer server) {
    }

    /** Symmetric lifecycle placeholder. */
    public void clearServer() {
    }

    // ---- CandidateDomainCollector (DOMAIN_PLAN stage) ----

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

    // ---- Initial generation (collection point at server startup) ----

    /** Collects, preflights and applies the initial generation (same semantics as 26.x; no block half). */
    public void applyInitialPlan(MinecraftServer server) {
        bindServer(server);
        ModificationCandidatePlan plan = new ModificationCandidatePlan(this);
        try {
            ItemEvents.MODIFICATION.postForCollection(new ItemModificationEventJS(plan));
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

    // ---- ModificationApplier: preflight and apply ----

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
     * Applies a plan, at the commit point or for the initial generation: restores every baseline
     * NekoJS holds, then applies the complete new plan in declaration order. If something throws
     * unexpectedly, it restores as far as it can, records RECOVERY_FAILED and rethrows the
     * exception (same as 26.x).
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
            // A failed batch does not count as a successful application, so reset the fingerprint
            // (same semantics as 26.x).
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

    // ---- Diagnostics (public view) ----

    /** Outcome of the last collection or application. */
    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /** Closes the root: restores every held baseline and clears them, so an independent test root
     *  starts from a clean state. */
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

    /** Modification outcomes, the same enum as 26.x; the block count is always 0 on this node. */
    public enum Outcome {
        INITIAL, APPLIED, RESTORED, SKIPPED_IDENTICAL, BLOCKED, RECOVERY_FAILED
    }

    /** Record of the most recent modification outcome, exposed as diagnostics. */
    public record Diagnostics(Outcome outcome, String source, int itemDeclarations, int blockDeclarations,
                              List<String> restoredTargets, String detail) {
    }
}
