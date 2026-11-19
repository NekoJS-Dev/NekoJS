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
 * Fluent builder for recipe stage gate declarations.
 */
@Doc("Fluent builder for recipe stage gate declarations.")
public final class RecipeGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> recipeIds = new LinkedHashSet<>();
    private final Set<NekoId> outputItems = new LinkedHashSet<>();
    private final Set<NekoId> outputTags = new LinkedHashSet<>();
    private final Set<NekoId> recipeTypes = new LinkedHashSet<>();
    private final Set<String> namespaces = new LinkedHashSet<>();
    private boolean showInViewer = false;

    @ScriptInternalConstructor
    public RecipeGateBuilder(NekoId id, String stage) {
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
    public RecipeGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public RecipeGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when crafting is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target recipe identifiers to this gate.")
    @Param(name = "recipeIds", value = "One or more recipe ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder recipeIds(NekoId... recipeIds) {
        StageGateKind.addIds(this.recipeIds, recipeIds, "recipeIds");
        return this;
    }

    @Doc("Adds target recipe identifiers to this gate.")
    @Param(name = "recipeIds", value = "One or more recipe ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder recipes(NekoId... recipeIds) {
        return recipeIds(recipeIds);
    }

    @Doc("Adds a single target recipe identifier to this gate.")
    @Param(name = "recipeId", value = "Target recipe id.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder recipeId(NekoId recipeId) {
        return recipeIds(recipeId);
    }

    @Doc("Adds target recipe output item identifiers to this gate.")
    @Param(name = "items", value = "One or more output item ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder outputItems(NekoId... items) {
        StageGateKind.addIds(this.outputItems, items, "outputItems");
        return this;
    }

    @Doc("Adds target recipe output item identifiers to this gate.")
    @Param(name = "items", value = "One or more output item ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder outputs(NekoId... items) {
        return outputItems(items);
    }

    @Doc("Adds a single target recipe output item identifier to this gate.")
    @Param(name = "item", value = "Target output item id.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder outputItem(NekoId item) {
        return outputItems(item);
    }

    @Doc("Adds target recipe output item tag identifiers to this gate.")
    @Param(name = "tags", value = "One or more output item tag ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder outputTags(NekoId... tags) {
        StageGateKind.addTagIds(this.outputTags, tags);
        return this;
    }

    @Doc("Adds target recipe output item tag identifiers to this gate.")
    @Param(name = "tags", value = "One or more output item tag ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder tags(NekoId... tags) {
        return outputTags(tags);
    }

    @Doc("Adds a single target recipe output item tag identifier to this gate.")
    @Param(name = "tag", value = "Target output item tag id.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder outputTag(NekoId tag) {
        return outputTags(tag);
    }

    @Doc("Adds target recipe type identifiers to this gate.")
    @Param(name = "types", value = "One or more recipe type ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder recipeTypes(NekoId... types) {
        StageGateKind.addIds(this.recipeTypes, types, "recipeTypes");
        return this;
    }

    @Doc("Adds target recipe type identifiers to this gate.")
    @Param(name = "types", value = "One or more recipe type ids.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder types(NekoId... types) {
        return recipeTypes(types);
    }

    @Doc("Adds a single target recipe type identifier to this gate.")
    @Param(name = "type", value = "Target recipe type id.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder recipeType(NekoId type) {
        return recipeTypes(type);
    }

    @Doc("Adds target mod namespaces to match all recipes belonging to those namespaces.")
    @Param(name = "modIds", value = "One or more namespace strings matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder namespaces(String... modIds) {
        StageGateKind.addNamespaces(this.namespaces, modIds);
        return this;
    }

    @Doc("Adds a single target mod namespace to this gate.")
    @Param(name = "modId", value = "Namespace string matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder namespace(String modId) {
        return namespaces(modId);
    }

    @Doc("Keeps gated recipes visible in client recipe viewers even when the stage is locked.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder showInViewer() {
        return showInViewer(true);
    }

    @Doc("Configures whether gated recipes remain visible in client recipe viewers when locked.")
    @Param(name = "show", value = "True to show in viewers; false to hide.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder showInViewer(boolean show) {
        this.showInViewer = show;
        return this;
    }

    @Doc("Hides gated recipes from client recipe viewers while the stage is locked.")
    @Return("This builder for chaining.")
    public RecipeGateBuilder hideInViewer() {
        return showInViewer(false);
    }

    @HideFromJS
    public StageGateDeclaration.RecipeGate buildDeclaration() {
        StageGateKind.requireTarget(
                id,
                !recipeIds.isEmpty()
                        || !outputItems.isEmpty()
                        || !outputTags.isEmpty()
                        || !recipeTypes.isEmpty()
                        || !namespaces.isEmpty()
        );
        return new StageGateDeclaration.RecipeGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                recipeIds,
                outputItems,
                outputTags,
                recipeTypes,
                namespaces,
                showInViewer
        );
    }
}
