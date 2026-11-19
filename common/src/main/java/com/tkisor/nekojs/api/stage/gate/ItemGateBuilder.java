package com.tkisor.nekojs.api.stage.gate;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fluent builder for item and armor stage gate declarations.
 */
@Doc("Fluent builder for item and armor stage gate declarations.")
public final class ItemGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> items = new LinkedHashSet<>();
    private final Set<NekoId> tags = new LinkedHashSet<>();
    private final Set<String> namespaces = new LinkedHashSet<>();
    private boolean allowInventoryStorage = false;
    private boolean allowContainerStorage = false;
    private boolean allowPickup = false;
    private boolean allowDrop = true;
    private boolean allowEquip = false;
    private boolean allowAttack = false;
    private boolean allowUse = false;
    private boolean showInViewer = false;
    private String lockedName = "";

    @ScriptInternalConstructor
    public ItemGateBuilder(NekoId id, String stage) {
        this.id = StageGateKind.requireRuleId(id);
        this.stage = StageGateKind.requireStage(stage);
    }

    /**
     * Creates a builder initialized with armor preset defaults (inventory/container/pickup/drop/attack
     * and recipe-viewer visibility allowed, while equipping and right-click equipping remain blocked).
     */
    @HideFromJS
    public static ItemGateBuilder armorPreset(NekoId id, String stage) {
        ItemGateBuilder builder = new ItemGateBuilder(id, stage);
        builder.allowInventoryStorage = true;
        builder.allowContainerStorage = true;
        builder.allowPickup = true;
        builder.allowDrop = true;
        builder.allowEquip = false;
        builder.allowAttack = true;
        builder.allowUse = false;
        builder.showInViewer = true;
        return builder;
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
    public ItemGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public ItemGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public ItemGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when an action is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public ItemGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target item identifiers to this gate.")
    @Param(name = "items", value = "One or more item ids.")
    @Return("This builder for chaining.")
    public ItemGateBuilder items(NekoId... items) {
        StageGateKind.addIds(this.items, items, "items");
        return this;
    }

    @Doc("Adds a single target item identifier to this gate.")
    @Param(name = "item", value = "Target item id.")
    @Return("This builder for chaining.")
    public ItemGateBuilder item(NekoId item) {
        StageGateKind.addIds(this.items, new NekoId[]{item}, "items");
        return this;
    }

    @Doc("Adds target item tag identifiers to this gate.")
    @Param(name = "tags", value = "One or more item tag ids.")
    @Return("This builder for chaining.")
    public ItemGateBuilder tags(NekoId... tags) {
        StageGateKind.addTagIds(this.tags, tags);
        return this;
    }

    @Doc("Adds a single target item tag identifier to this gate.")
    @Param(name = "tag", value = "Target item tag id.")
    @Return("This builder for chaining.")
    public ItemGateBuilder tag(NekoId tag) {
        StageGateKind.addTagIds(this.tags, new NekoId[]{tag});
        return this;
    }

    @Doc("Adds target mod namespaces to match all items belonging to those namespaces.")
    @Param(name = "modIds", value = "One or more namespace strings matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public ItemGateBuilder namespaces(String... modIds) {
        StageGateKind.addNamespaces(this.namespaces, modIds);
        return this;
    }

    @Doc("Adds a single target mod namespace to this gate.")
    @Param(name = "modId", value = "Namespace string matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public ItemGateBuilder namespace(String modId) {
        StageGateKind.addNamespaces(this.namespaces, new String[]{modId});
        return this;
    }

    @Doc("Allows holding gated items inside player inventory slots without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowInventoryStorage() {
        return allowInventoryStorage(true);
    }

    @Doc("Configures whether holding gated items inside player inventory slots is allowed.")
    @Param(name = "allow", value = "True to allow inventory storage; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowInventoryStorage(boolean allow) {
        this.allowInventoryStorage = allow;
        return this;
    }

    @Doc("Denies holding gated items inside player inventory slots without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyInventoryStorage() {
        return allowInventoryStorage(false);
    }

    @Doc("Allows storing or transferring gated items in open container menus without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowContainerStorage() {
        return allowContainerStorage(true);
    }

    @Doc("Configures whether gated items may be stored or transferred in open container menus.")
    @Param(name = "allow", value = "True to allow container storage; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowContainerStorage(boolean allow) {
        this.allowContainerStorage = allow;
        return this;
    }

    @Doc("Denies storing or transferring gated items in open container menus without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyContainerStorage() {
        return allowContainerStorage(false);
    }

    @Doc("Allows picking up gated item entities without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowPickup() {
        return allowPickup(true);
    }

    @Doc("Configures whether picking up gated item entities is allowed.")
    @Param(name = "allow", value = "True to allow pickup; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowPickup(boolean allow) {
        this.allowPickup = allow;
        return this;
    }

    @Doc("Denies picking up gated item entities without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyPickup() {
        return allowPickup(false);
    }

    @Doc("Allows dropping gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowDrop() {
        return allowDrop(true);
    }

    @Doc("Configures whether dropping gated items is allowed.")
    @Param(name = "allow", value = "True to allow dropping; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowDrop(boolean allow) {
        this.allowDrop = allow;
        return this;
    }

    @Doc("Denies dropping gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyDrop() {
        return allowDrop(false);
    }

    @Doc("Allows equipping gated items in armor or offhand equipment slots without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowEquip() {
        return allowEquip(true);
    }

    @Doc("Configures whether equipping gated items is allowed.")
    @Param(name = "allow", value = "True to allow equipping; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowEquip(boolean allow) {
        this.allowEquip = allow;
        return this;
    }

    @Doc("Denies equipping gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyEquip() {
        return allowEquip(false);
    }

    @Doc("Allows attacking with gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowAttack() {
        return allowAttack(true);
    }

    @Doc("Configures whether attacking with gated items is allowed.")
    @Param(name = "allow", value = "True to allow attacking; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowAttack(boolean allow) {
        this.allowAttack = allow;
        return this;
    }

    @Doc("Denies attacking with gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyAttack() {
        return allowAttack(false);
    }

    @Doc("Allows right-click using gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowUse() {
        return allowUse(true);
    }

    @Doc("Configures whether right-click using gated items is allowed.")
    @Param(name = "allow", value = "True to allow item use; false to deny.")
    @Return("This builder for chaining.")
    public ItemGateBuilder allowUse(boolean allow) {
        this.allowUse = allow;
        return this;
    }

    @Doc("Denies right-click using gated items without the required stage.")
    @Return("This builder for chaining.")
    public ItemGateBuilder denyUse() {
        return allowUse(false);
    }

    @Doc("Keeps gated items visible in client recipe viewers even when the stage is locked.")
    @Return("This builder for chaining.")
    public ItemGateBuilder showInViewer() {
        return showInViewer(true);
    }

    @Doc("Configures whether gated items remain visible in client recipe viewers when locked.")
    @Param(name = "show", value = "True to show in viewers; false to hide.")
    @Return("This builder for chaining.")
    public ItemGateBuilder showInViewer(boolean show) {
        this.showInViewer = show;
        return this;
    }

    @Doc("Hides gated items from client recipe viewers while the stage is locked.")
    @Return("This builder for chaining.")
    public ItemGateBuilder hideInViewer() {
        return showInViewer(false);
    }

    @Doc("Sets an obfuscated or replacement display name shown while the stage is locked.")
    @Param(name = "lockedName", value = "Display name when locked, or empty for default formatting.")
    @Return("This builder for chaining.")
    public ItemGateBuilder lockedName(String lockedName) {
        this.lockedName = StageGateKind.normalizeText(lockedName);
        return this;
    }

    @HideFromJS
    public StageGateDeclaration.ItemGate buildDeclaration() {
        StageGateKind.requireTarget(id, !items.isEmpty() || !tags.isEmpty() || !namespaces.isEmpty());
        return new StageGateDeclaration.ItemGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                items,
                tags,
                namespaces,
                allowInventoryStorage,
                allowContainerStorage,
                allowPickup,
                allowDrop,
                allowEquip,
                allowAttack,
                allowUse,
                showInViewer,
                lockedName
        );
    }
}
