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
 * Collects, validates, and applies item and block property modifications on the server.
 *
 * <p>Platform assembly creates one instance per runtime root and registers it via
 * {@code NekoRuntimeRoot#registerDomainCollector}. Item default components and block properties
 * are process-wide and fixed at registration, so original baselines are captured on first use,
 * owned for the lifetime of the root, and restored and cleared when {@link #close()} runs so
 * separate test roots stay isolated.
 *
 * <p>Collection runs at two points:
 * <ul>
 *   <li><b>Transactional reload ({@code DOMAIN_PLAN}):</b> {@link #collect} dispatches collection
 *       events to pending candidate listeners and registers an inert
 *       {@link ModificationCandidatePlan} that succeeds or fails atomically with
 *       {@code global}/shared top-level state.</li>
 *   <li><b>Initial generation (server about-to-start):</b> {@link #applyInitialPlan} dispatches
 *       collection events to active listeners, preflights the resulting plan, and applies it; if
 *       preflight fails, the whole batch is rejected and vanilla baselines remain untouched.</li>
 * </ul>
 *
 * <p>Applying a plan first restores every baseline held by NekoJS and then applies all new
 * declarations in order, so removed declarations revert to their baseline values on reload.
 * Only fields owned by NekoJS are restored (the item default component map and the three copies
 * of block properties); side effects in other mods, worlds, network state, or files are not
 * rolled back. Differences between 26.x and 1.21.1 item component updates ({@code bindComponents}
 * vs. reflected fields) live in each version's {@code ItemModificationEventJS}; block modification
 * exists only on 26.x.
 */
public final class ModificationDomainOwner implements CandidateDomainCollector, ModificationApplier, AutoCloseable {

    /** Collector identifier (aligned with {@code ModificationCandidatePlan.DOMAIN}). */
    public static final String DOMAIN = ModificationCandidatePlan.DOMAIN;

    /** 26.x component limit: {@code Item#ABSOLUTE_MAX_STACK_SIZE}. */
    private static final int MAX_STACK_SIZE_LIMIT = 99;

    /** Original item default components captured before first modification, held per runtime root. */
    private final Map<String, DataComponentMap> itemBaselines = new java.util.LinkedHashMap<>();
    /** Original block property snapshots on 26.x, including the original light-emission functions. */
    private final Map<String, BlockModificationJS.PropertySnapshot> blockBaselines = new java.util.LinkedHashMap<>();
    /** Server bound at about-to-start, providing the damage type registry needed by {@code fireResistant}. */
    private volatile MinecraftServer boundServer;
    /** Most recent collection/application outcome exposed for diagnostics. */
    private volatile Diagnostics lastDiagnostics = new Diagnostics(Outcome.INITIAL, "none", 0, 0, List.of(), null);
    /** Fingerprint of the last successfully applied plan, used to skip duplicate startup runs. */
    private volatile String lastAppliedFingerprint;

    /** Snapshot of the last successfully published declaration, used for client catch-up after a new player logs in. */
    private volatile List<ModificationDeclaration> activeDeclarations = List.of();
    private volatile long syncGeneration;

    public ModificationDomainOwner() {
    }

    // ---- Server binding (source of registry access for fireResistant) ----

    /** Server about-to-start binding (may be required by DOMAIN_PLAN before any transaction reload). */
    public void bindServer(MinecraftServer server) {
        this.boundServer = server;
    }

    /** Unbinds when the server stops. */
    public void clearServer() {
        this.boundServer = null;
    }

    /** Sends the current committed plan to a newly logged-in player. */
    public void syncTo(net.minecraft.server.level.ServerPlayer player) {
        if (player != null && boundServer != null) {
            ModificationSyncWire.sendTo(player, syncGeneration, activeDeclarations);
        }
    }

    /** Sends the committed plan to all connected clients after a successful server apply. */
    private void syncToClients(List<ModificationDeclaration> declarations) {
        if (boundServer != null) {
            ModificationSyncWire.broadcast(syncGeneration, declarations);
        }
    }


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
        // The initial server resource reload can run before vanilla binds item components.
        // Keep this candidate inert; applyInitialPlan collects the active listeners after the
        // server is ready and performs the first real preflight.
        if (boundServer == null) {
            handle.registerPlan(plan);
            return;
        }
        handle.dispatch(ItemEvents.MODIFICATION, new ItemModificationEventJS(plan));
        handle.dispatch(BlockEvents.MODIFICATION, new BlockModificationEventJS(plan));
        handle.registerPlan(plan);
    }

    // ---- Initial generation (server startup collection) ----

    /**
     * Collects, preflights, and applies modifications for the initial generation by dispatching
     * to active listeners. If preflight fails, the entire batch is blocked without partial changes
     * and the error is recorded in diagnostics. Skips re-applying if the plan fingerprint matches
     * the last successfully applied plan.
     */
    public void applyInitialPlan(MinecraftServer server) {
        bindServer(server);
        ModificationCandidatePlan plan = new ModificationCandidatePlan(this);
        try {
            ItemEvents.MODIFICATION.postForCollection(new ItemModificationEventJS(plan));
            BlockEvents.MODIFICATION.postForCollection(new BlockModificationEventJS(plan));
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

    // ---- ModificationApplier: preflight and apply ----

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
            // 26.x DAMAGE_RESISTANT requires the damage type registry from a bound server.
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
     * Restores all baselines held by NekoJS and then applies the new plan in declaration order
     * (each declaration builds on the baseline, so a later declaration for the same target
     * replaces earlier ones). If an unexpected error occurs after preflight, attempts to restore
     * baselines, records {@link Outcome#RECOVERY_FAILED}, and rethrows.
     */
    @Override
    public void apply(List<ModificationDeclaration> declarations) {
        apply(declarations, "commit");
    }

    private void apply(List<ModificationDeclaration> declarations, String source) {
        List<String> restored = new ArrayList<>();
        try {
            // (1) Restore NekoJS baselines so targets omitted from the new plan return to original values
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
            // (2) Apply declarations in order, each starting from baseline
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
            activeDeclarations = List.copyOf(declarations);
            syncGeneration++;
            syncToClients(activeDeclarations);
            if (!declarations.isEmpty()) {
                NekoJS.LOGGER.info("NekoJS modifications applied at {} ({} item(s), {} block(s))",
                        source, countKind(declarations, "item"), countKind(declarations, "block"));
            }
        } catch (Throwable t) {
            // Best-effort baseline restore before reporting the failure
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
            // Clear the last applied fingerprint so a subsequent run will not treat a failed batch
            // as SKIPPED_IDENTICAL while targets actually sit at baseline.
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

    /** Applies normalized item properties to the component builder; package-private for {@code ItemModificationComponentsTest}. */
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
        BlockModificationJS.PropertySnapshot baseline = blockBaselines.computeIfAbsent(
                declaration.targetId(), key -> BlockModificationJS.PropertySnapshot.capture(block));
        baseline.applyTo(block);
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

    /** Returns the item's baseline components, capturing them on first modification. */
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
     * Builds a 26.x {@code DAMAGE_RESISTANT} component backed by the {@code IS_FIRE} damage type
     * tag from the server's registry access, matching vanilla {@code Item.Properties#fireResistant()}.
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

    // ---- Diagnostics ----

    /** Returns diagnostics for the most recent collection or apply run. */
    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /** Restores all captured baselines and clears state when the runtime root closes. */
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

    /** Outcome of the most recent modification collection or apply run. */
    public enum Outcome {
        /** No collection or apply has run yet. */
        INITIAL,
        /** A non-empty plan was applied. */
        APPLIED,
        /** An empty plan was applied and captured baselines were restored. */
        RESTORED,
        /** Startup collection matched the last applied plan and was skipped. */
        SKIPPED_IDENTICAL,
        /** Preflight rejected the plan; existing values were left unchanged. */
        BLOCKED,
        /** Applying or restoring failed unexpectedly after best-effort baseline recovery. */
        RECOVERY_FAILED
    }

    /** Diagnostic snapshot of the most recent modification run. */
    public record Diagnostics(Outcome outcome, String source, int itemDeclarations, int blockDeclarations,
                              List<String> restoredTargets, String detail) {
    }
}
//?}
