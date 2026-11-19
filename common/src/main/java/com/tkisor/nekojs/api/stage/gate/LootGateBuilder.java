package com.tkisor.nekojs.api.stage.gate;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fluent builder for loot drop filtering and replacement stage gate declarations.
 */
@Doc("Fluent builder for loot drop filtering and replacement stage gate declarations.")
public final class LootGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> items = new LinkedHashSet<>();
    private final Set<NekoId> tags = new LinkedHashSet<>();
    private final Set<NekoId> fromLootTables = new LinkedHashSet<>();
    private final Set<NekoId> fromEntities = new LinkedHashSet<>();
    private @Nullable NekoId replacementItem = null;

    @ScriptInternalConstructor
    public LootGateBuilder(NekoId id, String stage) {
        this.id = StageGateKind.requireRuleId(id);
        this.stage = StageGateKind.requireStage(stage);
    }

    @Doc("Returns the unique stage gate rule id.")
    @Return("Rule id.")
    public NekoId getId() {
        return id;
    }

    @Doc("Returns the required stage name.")
    @Return("Stage name.")
    public String getStage() {
        return stage;
    }

    @Doc("Sets the evaluation priority for this rule (default 0).")
    @Param(name = "priority", value = "Rule priority; higher values take precedence on specificity ties.")
    @Return("This builder for chaining.")
    public LootGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public LootGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public LootGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets an optional custom feedback message associated with this loot gate.")
    @Param(name = "message", value = "Custom locked message, or empty for none.")
    @Return("This builder for chaining.")
    public LootGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target dropped item identifiers to filter or replace.")
    @Param(name = "items", value = "One or more loot item ids.")
    @Return("This builder for chaining.")
    public LootGateBuilder items(NekoId... items) {
        StageGateKind.addIds(this.items, items, "items");
        return this;
    }

    @Doc("Adds a single target dropped item identifier to filter or replace.")
    @Param(name = "item", value = "Target loot item id.")
    @Return("This builder for chaining.")
    public LootGateBuilder item(NekoId item) {
        return items(item);
    }

    @Doc("Adds target dropped item tag identifiers to filter or replace.")
    @Param(name = "tags", value = "One or more item tag ids.")
    @Return("This builder for chaining.")
    public LootGateBuilder tags(NekoId... tags) {
        StageGateKind.addTagIds(this.tags, tags);
        return this;
    }

    @Doc("Adds a single target dropped item tag identifier to filter or replace.")
    @Param(name = "tag", value = "Target item tag id.")
    @Return("This builder for chaining.")
    public LootGateBuilder tag(NekoId tag) {
        return tags(tag);
    }

    @Doc("Scopes this loot gate to drops originating from the specified loot table identifiers.")
    @Param(name = "tables", value = "One or more loot table ids.")
    @Return("This builder for chaining.")
    public LootGateBuilder fromLootTables(NekoId... tables) {
        StageGateKind.addIds(this.fromLootTables, tables, "fromLootTables");
        return this;
    }

    @Doc("Scopes this loot gate to drops originating from the specified loot table identifiers.")
    @Param(name = "tables", value = "One or more loot table ids.")
    @Return("This builder for chaining.")
    public LootGateBuilder lootTables(NekoId... tables) {
        return fromLootTables(tables);
    }

    @Doc("Scopes this loot gate to drops originating from a single loot table identifier.")
    @Param(name = "table", value = "Loot table id.")
    @Return("This builder for chaining.")
    public LootGateBuilder fromLootTable(NekoId table) {
        return fromLootTables(table);
    }

    @Doc("Scopes this loot gate to drops originating from the specified entity type identifiers.")
    @Param(name = "entityTypes", value = "One or more entity type ids.")
    @Return("This builder for chaining.")
    public LootGateBuilder fromEntities(NekoId... entityTypes) {
        StageGateKind.addIds(this.fromEntities, entityTypes, "fromEntities");
        return this;
    }

    @Doc("Scopes this loot gate to drops originating from the specified entity type identifiers.")
    @Param(name = "entityTypes", value = "One or more entity type ids.")
    @Return("This builder for chaining.")
    public LootGateBuilder entities(NekoId... entityTypes) {
        return fromEntities(entityTypes);
    }

    @Doc("Scopes this loot gate to drops originating from a single entity type identifier.")
    @Param(name = "entityType", value = "Entity type id.")
    @Return("This builder for chaining.")
    public LootGateBuilder fromEntity(NekoId entityType) {
        return fromEntities(entityType);
    }

    @Doc("Replaces blocked loot stacks with the specified item (preserving stack count) instead of removing them.")
    @Param(name = "replacementItem", value = "Replacement item id, or null to remove blocked drops.")
    @Return("This builder for chaining.")
    public LootGateBuilder replaceWith(@Nullable NekoId replacementItem) {
        this.replacementItem = replacementItem;
        return this;
    }

    @Doc("Replaces blocked loot stacks with the specified item (preserving stack count) instead of removing them.")
    @Param(name = "replacementItem", value = "Replacement item id, or null to remove blocked drops.")
    @Return("This builder for chaining.")
    public LootGateBuilder replacementItem(@Nullable NekoId replacementItem) {
        this.replacementItem = replacementItem;
        return this;
    }

    @HideFromJS
    public StageGateDeclaration.LootGate buildDeclaration() {
        StageGateKind.requireTarget(
                id,
                !items.isEmpty()
                        || !tags.isEmpty()
                        || !fromLootTables.isEmpty()
                        || !fromEntities.isEmpty()
        );
        return new StageGateDeclaration.LootGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                items,
                tags,
                fromLootTables,
                fromEntities,
                replacementItem
        );
    }
}
