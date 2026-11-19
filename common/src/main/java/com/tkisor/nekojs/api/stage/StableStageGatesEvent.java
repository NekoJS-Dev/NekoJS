package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.gate.BlockGateBuilder;
import com.tkisor.nekojs.api.stage.gate.ContainerGateBuilder;
import com.tkisor.nekojs.api.stage.gate.DimensionGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EffectGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EnchantGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EntityGateBuilder;
import com.tkisor.nekojs.api.stage.gate.ItemGateBuilder;
import com.tkisor.nekojs.api.stage.gate.LootGateBuilder;
import com.tkisor.nekojs.api.stage.gate.RecipeGateBuilder;
import com.tkisor.nekojs.api.stage.gate.RegionGateBuilder;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Candidate-generation collector payload for {@code StageEvents.gates}, allowing server scripts
 * to declare stage-gated items, armor, blocks, crops, recipes, containers, dimensions, spatial
 * regions, entities, effects, enchantments, and loot drops without leaking loader or native types.
 */
@Doc("Declarative collector payload for StageEvents.gates during server script reload.")
public final class StableStageGatesEvent {
    private final List<PendingGate> pendingGates = new ArrayList<>();
    private final Set<NekoId> whitelistedContainers = new LinkedHashSet<>();
    private final Map<NekoId, Set<Integer>> whitelistedContainerSlots = new LinkedHashMap<>();

    @ScriptInternalConstructor
    public StableStageGatesEvent() {
    }

    /**
     * Whitelists one or more container menu types so item gates with {@code allowContainerStorage = false}
     * still allow storing gated items inside those safe storage menus.
     */
    @Doc("Whitelists container menu ids where restricted items may be stored safely without triggering item confiscation.")
    @Param(name = "menuIds", value = "One or more container menu type ids.")
    @Return("This event for chaining.")
    public StableStageGatesEvent whitelistContainer(NekoId... menuIds) {
        if (menuIds == null) {
            throw new IllegalArgumentException("[NEKO-4071] Whitelisted container menuIds array cannot be null");
        }
        for (NekoId menuId : menuIds) {
            if (menuId == null) {
                throw new IllegalArgumentException("[NEKO-4071] Whitelisted container menuId entry cannot be null");
            }
            this.whitelistedContainers.add(menuId);
        }
        return this;
    }

    public StableStageGatesEvent whitelistContainerSlots(NekoId menuId, int... slotIds) {
        if (menuId == null) {
            throw new IllegalArgumentException("[NEKO-4071] Whitelisted container menu ID cannot be null");
        }
        if (slotIds == null || slotIds.length == 0) {
            throw new IllegalArgumentException("[NEKO-4071] Whitelisted container requires at least one slot ID: " + menuId);
        }
        Set<Integer> slots = new LinkedHashSet<>();
        for (int slotId : slotIds) {
            if (slotId < 0) {
                throw new IllegalArgumentException("[NEKO-4071] Invalid whitelisted container slot ID " + slotId + " for " + menuId);
            }
            slots.add(slotId);
        }
        whitelistedContainerSlots.computeIfAbsent(menuId, key -> new LinkedHashSet<>()).addAll(slots);
        return this;
    }

    @Doc("Declares an item stage gate with standard item restriction defaults.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent ItemGateBuilder.")
    public ItemGateBuilder item(NekoId id, String stage) {
        ItemGateBuilder builder = new ItemGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares an item stage gate initialized with armor preset defaults (carryable and visible, equipping blocked).")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent ItemGateBuilder configured with armor defaults.")
    public ItemGateBuilder armor(NekoId id, String stage) {
        ItemGateBuilder builder = ItemGateBuilder.armorPreset(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a block stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent BlockGateBuilder.")
    public BlockGateBuilder block(NekoId id, String stage) {
        BlockGateBuilder builder = new BlockGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a crop block stage gate initialized with crop preset defaults.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent BlockGateBuilder configured for crop blocks.")
    public BlockGateBuilder crop(NekoId id, String stage) {
        BlockGateBuilder builder = BlockGateBuilder.cropPreset(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a recipe stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent RecipeGateBuilder.")
    public RecipeGateBuilder recipe(NekoId id, String stage) {
        RecipeGateBuilder builder = new RecipeGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a container menu stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent ContainerGateBuilder.")
    public ContainerGateBuilder container(NekoId id, String stage) {
        ContainerGateBuilder builder = new ContainerGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a dimension entry/presence stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent DimensionGateBuilder.")
    public DimensionGateBuilder dimension(NekoId id, String stage) {
        DimensionGateBuilder builder = new DimensionGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a spatial region stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent RegionGateBuilder.")
    public RegionGateBuilder region(NekoId id, String stage) {
        RegionGateBuilder builder = new RegionGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares an entity interaction, combat, or mount stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent EntityGateBuilder.")
    public EntityGateBuilder entity(NekoId id, String stage) {
        EntityGateBuilder builder = new EntityGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a mob effect application/retention stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent EffectGateBuilder.")
    public EffectGateBuilder effect(NekoId id, String stage) {
        EffectGateBuilder builder = new EffectGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares an enchantment stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent EnchantGateBuilder.")
    public EnchantGateBuilder enchant(NekoId id, String stage) {
        EnchantGateBuilder builder = new EnchantGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    @Doc("Declares a loot drop filtering or replacement stage gate.")
    @Param(name = "id", value = "Unique rule id.")
    @Param(name = "stage", value = "Required stage name.")
    @Return("Fluent LootGateBuilder.")
    public LootGateBuilder loot(NekoId id, String stage) {
        LootGateBuilder builder = new LootGateBuilder(id, stage);
        pendingGates.add(new PendingGate(builder.getId(), builder::buildDeclaration));
        return builder;
    }

    /**
     * Builds all registered stage gate declarations in registration order, rejecting duplicate
     * {@link NekoId} rule identifiers and targetless rules.
     */
    @HideFromJS
    public List<StageGateDeclaration> buildDeclarations() {
        Set<NekoId> seenIds = new LinkedHashSet<>(pendingGates.size());
        List<StageGateDeclaration> declarations = new ArrayList<>(pendingGates.size());
        for (PendingGate pending : pendingGates) {
            if (!seenIds.add(pending.id())) {
                throw new IllegalArgumentException("[NEKO-4071] Duplicate stage gate id: " + pending.id());
            }
            declarations.add(pending.supplier().get());
        }
        return Collections.unmodifiableList(declarations);
    }

    /**
     * Returns an unmodifiable snapshot of container menu identifiers whitelisted for safe restricted-item storage.
     */
    @HideFromJS
    public Set<NekoId> whitelistedContainers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(whitelistedContainers));
    }

    @HideFromJS
    public Map<NekoId, Set<Integer>> whitelistedContainerSlots() {
        Map<NekoId, Set<Integer>> snapshot = new LinkedHashMap<>();
        whitelistedContainerSlots.forEach((menuId, slots) -> snapshot.put(menuId, Set.copyOf(slots)));
        return Collections.unmodifiableMap(snapshot);
    }

    private record PendingGate(NekoId id, Supplier<StageGateDeclaration> supplier) {
    }
}
