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
 * Fluent builder for block and crop stage gate declarations.
 */
@Doc("Fluent builder for block and crop stage gate declarations.")
public final class BlockGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final Set<NekoId> blocks = new LinkedHashSet<>();
    private final Set<NekoId> tags = new LinkedHashSet<>();
    private final Set<String> namespaces = new LinkedHashSet<>();
    private boolean allowBreak = false;
    private boolean allowPlace = false;
    private boolean allowInteract = false;
    private boolean allowToolModify = false;
    private @Nullable Integer minAge = null;

    @ScriptInternalConstructor
    public BlockGateBuilder(NekoId id, String stage) {
        this.id = StageGateKind.requireRuleId(id);
        this.stage = StageGateKind.requireStage(stage);
    }

    /**
     * Creates a builder initialized with crop preset defaults ({@code minAge = 0}, overridable via
     * {@link #minAge(int)}).
     */
    @HideFromJS
    public static BlockGateBuilder cropPreset(NekoId id, String stage) {
        BlockGateBuilder builder = new BlockGateBuilder(id, stage);
        builder.minAge = 0;
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
    public BlockGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public BlockGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public BlockGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when an action is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public BlockGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds target block identifiers to this gate.")
    @Param(name = "blocks", value = "One or more block ids.")
    @Return("This builder for chaining.")
    public BlockGateBuilder blocks(NekoId... blocks) {
        StageGateKind.addIds(this.blocks, blocks, "blocks");
        return this;
    }

    @Doc("Adds a single target block identifier to this gate.")
    @Param(name = "block", value = "Target block id.")
    @Return("This builder for chaining.")
    public BlockGateBuilder block(NekoId block) {
        StageGateKind.addIds(this.blocks, new NekoId[]{block}, "blocks");
        return this;
    }

    @Doc("Adds target block tag identifiers to this gate.")
    @Param(name = "tags", value = "One or more block tag ids.")
    @Return("This builder for chaining.")
    public BlockGateBuilder tags(NekoId... tags) {
        StageGateKind.addTagIds(this.tags, tags);
        return this;
    }

    @Doc("Adds a single target block tag identifier to this gate.")
    @Param(name = "tag", value = "Target block tag id.")
    @Return("This builder for chaining.")
    public BlockGateBuilder tag(NekoId tag) {
        StageGateKind.addTagIds(this.tags, new NekoId[]{tag});
        return this;
    }

    @Doc("Adds target mod namespaces to match all blocks belonging to those namespaces.")
    @Param(name = "modIds", value = "One or more namespace strings matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public BlockGateBuilder namespaces(String... modIds) {
        StageGateKind.addNamespaces(this.namespaces, modIds);
        return this;
    }

    @Doc("Adds a single target mod namespace to this gate.")
    @Param(name = "modId", value = "Namespace string matching [a-z0-9_.-]+.")
    @Return("This builder for chaining.")
    public BlockGateBuilder namespace(String modId) {
        StageGateKind.addNamespaces(this.namespaces, new String[]{modId});
        return this;
    }

    @Doc("Allows breaking gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowBreak() {
        return allowBreak(true);
    }

    @Doc("Configures whether breaking gated blocks is allowed.")
    @Param(name = "allow", value = "True to allow breaking; false to deny.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowBreak(boolean allow) {
        this.allowBreak = allow;
        return this;
    }

    @Doc("Denies breaking gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder denyBreak() {
        return allowBreak(false);
    }

    @Doc("Allows placing gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowPlace() {
        return allowPlace(true);
    }

    @Doc("Configures whether placing gated blocks is allowed.")
    @Param(name = "allow", value = "True to allow placing; false to deny.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowPlace(boolean allow) {
        this.allowPlace = allow;
        return this;
    }

    @Doc("Denies placing gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder denyPlace() {
        return allowPlace(false);
    }

    @Doc("Allows right-click interacting with gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowInteract() {
        return allowInteract(true);
    }

    @Doc("Configures whether right-click interacting with gated blocks is allowed.")
    @Param(name = "allow", value = "True to allow interaction; false to deny.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowInteract(boolean allow) {
        this.allowInteract = allow;
        return this;
    }

    @Doc("Denies right-click interacting with gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder denyInteract() {
        return allowInteract(false);
    }

    @Doc("Allows tool state modifications (tilling, stripping, flattening) on gated blocks without the stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowToolModify() {
        return allowToolModify(true);
    }

    @Doc("Configures whether tool modifications on gated blocks are allowed.")
    @Param(name = "allow", value = "True to allow tool modification; false to deny.")
    @Return("This builder for chaining.")
    public BlockGateBuilder allowToolModify(boolean allow) {
        this.allowToolModify = allow;
        return this;
    }

    @Doc("Denies tool state modifications on gated blocks without the required stage.")
    @Return("This builder for chaining.")
    public BlockGateBuilder denyToolModify() {
        return allowToolModify(false);
    }

    @Doc("Restricts crop breaking/harvesting when the crop block age property is >= minAge.")
    @Param(name = "minAge", value = "Minimum crop age threshold (>= 0).")
    @Return("This builder for chaining.")
    public BlockGateBuilder minAge(int minAge) {
        if (minAge < 0) {
            throw new IllegalArgumentException(
                    "[NEKO-4071] Stage gate '" + id + "' minAge cannot be negative: " + minAge);
        }
        this.minAge = minAge;
        return this;
    }

    @HideFromJS
    public StageGateDeclaration.BlockGate buildDeclaration() {
        StageGateKind.requireTarget(id, !blocks.isEmpty() || !tags.isEmpty() || !namespaces.isEmpty());
        return new StageGateDeclaration.BlockGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                blocks,
                tags,
                namespaces,
                allowBreak,
                allowPlace,
                allowInteract,
                allowToolModify,
                minAge
        );
    }
}
