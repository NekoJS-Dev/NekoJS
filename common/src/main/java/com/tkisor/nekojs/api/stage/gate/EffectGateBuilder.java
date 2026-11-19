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
 * Fluent builder for mob effect stage gate declarations.
 */
@Doc("Fluent builder for mob effect stage gate declarations.")
public final class EffectGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> effects = new LinkedHashSet<>();

    @ScriptInternalConstructor
    public EffectGateBuilder(NekoId id, String stage) {
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
    public EffectGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public EffectGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public EffectGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when a gated effect is stripped or blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public EffectGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target mob effect identifiers to this gate.")
    @Param(name = "effects", value = "One or more mob effect ids.")
    @Return("This builder for chaining.")
    public EffectGateBuilder effects(NekoId... effects) {
        StageGateKind.addIds(this.effects, effects, "effects");
        return this;
    }

    @Doc("Adds a single target mob effect identifier to this gate.")
    @Param(name = "effect", value = "Target mob effect id.")
    @Return("This builder for chaining.")
    public EffectGateBuilder effect(NekoId effect) {
        return effects(effect);
    }

    @HideFromJS
    public StageGateDeclaration.EffectGate buildDeclaration() {
        StageGateKind.requireTarget(id, !effects.isEmpty());
        return new StageGateDeclaration.EffectGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                effects
        );
    }
}
