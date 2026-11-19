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
 * Fluent builder for enchantment stage gate declarations.
 */
@Doc("Fluent builder for enchantment stage gate declarations.")
public final class EnchantGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> enchants = new LinkedHashSet<>();
    private int minLevel = 1;

    @ScriptInternalConstructor
    public EnchantGateBuilder(NekoId id, String stage) {
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
    public EnchantGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public EnchantGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public EnchantGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when a gated enchantment action is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public EnchantGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target enchantment identifiers to this gate.")
    @Param(name = "enchants", value = "One or more enchantment ids.")
    @Return("This builder for chaining.")
    public EnchantGateBuilder enchants(NekoId... enchants) {
        StageGateKind.addIds(this.enchants, enchants, "enchants");
        return this;
    }

    @Doc("Adds target enchantment identifiers to this gate.")
    @Param(name = "enchants", value = "One or more enchantment ids.")
    @Return("This builder for chaining.")
    public EnchantGateBuilder enchantments(NekoId... enchants) {
        return enchants(enchants);
    }

    @Doc("Adds a single target enchantment identifier to this gate.")
    @Param(name = "enchant", value = "Target enchantment id.")
    @Return("This builder for chaining.")
    public EnchantGateBuilder enchant(NekoId enchant) {
        return enchants(enchant);
    }

    @Doc("Sets the minimum enchantment level (inclusive) that requires the stage (default 1).")
    @Param(name = "minLevel", value = "Minimum restricted enchantment level (>= 1).")
    @Return("This builder for chaining.")
    public EnchantGateBuilder minLevel(int minLevel) {
        if (minLevel < 1) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Stage gate '" + id + "' minLevel must be >= 1, got: " + minLevel);
        }
        this.minLevel = minLevel;
        return this;
    }

    @Doc("Restricts enchantment levels strictly above the given level (sets minLevel = level + 1).")
    @Param(name = "level", value = "Maximum allowed enchantment level without the stage (>= 0).")
    @Return("This builder for chaining.")
    public EnchantGateBuilder aboveLevel(int level) {
        if (level < 0 || level == Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Stage gate '" + id + "' aboveLevel must be >= 0 and < Integer.MAX_VALUE, got: " + level);
        }
        this.minLevel = level + 1;
        return this;
    }

    @HideFromJS
    public StageGateDeclaration.EnchantGate buildDeclaration() {
        StageGateKind.requireTarget(id, !enchants.isEmpty());
        return new StageGateDeclaration.EnchantGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                enchants,
                minLevel
        );
    }
}
