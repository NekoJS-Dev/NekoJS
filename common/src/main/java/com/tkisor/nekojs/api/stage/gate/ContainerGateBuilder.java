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
 * Fluent builder for container menu stage gate declarations.
 */
@Doc("Fluent builder for container menu stage gate declarations.")
public final class ContainerGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> menus = new LinkedHashSet<>();

    @ScriptInternalConstructor
    public ContainerGateBuilder(NekoId id, String stage) {
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
    public ContainerGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public ContainerGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public ContainerGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when opening a gated menu is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public ContainerGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target menu type identifiers to this gate.")
    @Param(name = "menuIds", value = "One or more menu type ids.")
    @Return("This builder for chaining.")
    public ContainerGateBuilder menus(NekoId... menuIds) {
        StageGateKind.addIds(this.menus, menuIds, "menus");
        return this;
    }

    @Doc("Adds target container menu type identifiers to this gate.")
    @Param(name = "menuIds", value = "One or more menu type ids.")
    @Return("This builder for chaining.")
    public ContainerGateBuilder containers(NekoId... menuIds) {
        return menus(menuIds);
    }

    @Doc("Adds a single target menu type identifier to this gate.")
    @Param(name = "menuId", value = "Target menu type id.")
    @Return("This builder for chaining.")
    public ContainerGateBuilder menu(NekoId menuId) {
        return menus(menuId);
    }

    @HideFromJS
    public StageGateDeclaration.ContainerGate buildDeclaration() {
        StageGateKind.requireTarget(id, !menus.isEmpty());
        return new StageGateDeclaration.ContainerGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                menus
        );
    }
}
