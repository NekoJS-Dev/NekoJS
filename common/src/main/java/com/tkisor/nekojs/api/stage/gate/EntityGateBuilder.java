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
 * Fluent builder for entity stage gate declarations.
 */
@Doc("Fluent builder for entity stage gate declarations.")
public final class EntityGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> entityTypes = new LinkedHashSet<>();
    private final Set<NekoId> tags = new LinkedHashSet<>();
    private final Set<String> namespaces = new LinkedHashSet<>();
    private boolean allowAttack = false;
    private boolean allowInteract = false;
    private boolean allowMount = false;

    @ScriptInternalConstructor
    public EntityGateBuilder(NekoId id, String stage) {
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
    public EntityGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public EntityGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public EntityGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when an entity action is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public EntityGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target entity type identifiers to this gate.")
    @Param(name = "entityTypes", value = "One or more entity type ids.")
    @Return("This builder for chaining.")
    public EntityGateBuilder entityTypes(NekoId... entityTypes) {
        StageGateKind.addIds(this.entityTypes, entityTypes, "entityTypes");
        return this;
    }

    @Doc("Adds target entity type identifiers to this gate.")
    @Param(name = "entityTypes", value = "One or more entity type ids.")
    @Return("This builder for chaining.")
    public EntityGateBuilder entities(NekoId... entityTypes) {
        return entityTypes(entityTypes);
    }

    @Doc("Adds a single target entity type identifier to this gate.")
    @Param(name = "entityType", value = "Target entity type id.")
    @Return("This builder for chaining.")
    public EntityGateBuilder entityType(NekoId entityType) {
        return entityTypes(entityType);
    }

    @Doc("Adds a single target entity type identifier to this gate.")
    @Param(name = "entityType", value = "Target entity type id.")
    @Return("This builder for chaining.")
    public EntityGateBuilder entity(NekoId entityType) {
        return entityTypes(entityType);
    }

    @Doc("Adds target entity type tag identifiers to this gate.")
    @Param(name = "tags", value = "One or more entity type tag ids.")
    @Return("This builder for chaining.")
    public EntityGateBuilder tags(NekoId... tags) {
        StageGateKind.addTagIds(this.tags, tags);
        return this;
    }

    @Doc("Adds a single target entity type tag identifier to this gate.")
    @Param(name = "tag", value = "Target entity type tag id.")
    @Return("This builder for chaining.")
    public EntityGateBuilder tag(NekoId tag) {
        return tags(tag);
    }

    @Doc("Adds target mod namespaces to match all entities belonging to those namespaces.")
    @Param(name = "modIds", value = "One or more namespace strings matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public EntityGateBuilder namespaces(String... modIds) {
        StageGateKind.addNamespaces(this.namespaces, modIds);
        return this;
    }

    @Doc("Adds a single target mod namespace to this gate.")
    @Param(name = "modId", value = "Namespace string matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public EntityGateBuilder namespace(String modId) {
        return namespaces(modId);
    }

    @Doc("Allows attacking gated entities without the required stage.")
    @Return("This builder for chaining.")
    public EntityGateBuilder allowAttack() {
        return allowAttack(true);
    }

    @Doc("Configures whether attacking gated entities is allowed.")
    @Param(name = "allow", value = "True to allow attacking; false to deny.")
    @Return("This builder for chaining.")
    public EntityGateBuilder allowAttack(boolean allow) {
        this.allowAttack = allow;
        return this;
    }

    @Doc("Denies attacking gated entities without the required stage.")
    @Return("This builder for chaining.")
    public EntityGateBuilder denyAttack() {
        return allowAttack(false);
    }

    @Doc("Allows interacting with gated entities without the required stage.")
    @Return("This builder for chaining.")
    public EntityGateBuilder allowInteract() {
        return allowInteract(true);
    }

    @Doc("Configures whether interacting with gated entities is allowed.")
    @Param(name = "allow", value = "True to allow interaction; false to deny.")
    @Return("This builder for chaining.")
    public EntityGateBuilder allowInteract(boolean allow) {
        this.allowInteract = allow;
        return this;
    }

    @Doc("Denies interacting with gated entities without the required stage.")
    @Return("This builder for chaining.")
    public EntityGateBuilder denyInteract() {
        return allowInteract(false);
    }

    @Doc("Allows mounting gated entities without the required stage.")
    @Return("This builder for chaining.")
    public EntityGateBuilder allowMount() {
        return allowMount(true);
    }

    @Doc("Configures whether mounting gated entities is allowed.")
    @Param(name = "allow", value = "True to allow mounting; false to deny.")
    @Return("This builder for chaining.")
    public EntityGateBuilder allowMount(boolean allow) {
        this.allowMount = allow;
        return this;
    }

    @Doc("Denies mounting gated entities without the required stage.")
    @Return("This builder for chaining.")
    public EntityGateBuilder denyMount() {
        return allowMount(false);
    }

    @HideFromJS
    public StageGateDeclaration.EntityGate buildDeclaration() {
        StageGateKind.requireTarget(id, !entityTypes.isEmpty() || !tags.isEmpty() || !namespaces.isEmpty());
        return new StageGateDeclaration.EntityGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                entityTypes,
                tags,
                namespaces,
                allowAttack,
                allowInteract,
                allowMount
        );
    }
}
