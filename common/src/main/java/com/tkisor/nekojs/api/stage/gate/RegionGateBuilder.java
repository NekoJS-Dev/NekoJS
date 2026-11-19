package com.tkisor.nekojs.api.stage.gate;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for spatial region stage gate declarations.
 */
@Doc("Fluent builder for spatial region stage gate declarations.")
public final class RegionGateBuilder {
    private final NekoId id;
    private final String stage;
    private int priority = 0;
    private StageScope scope = StageScope.PLAYER;
    private String lockedMessage = "";
    private final List<RegionShape> shapes = new ArrayList<>();
    private boolean allowEnter = false;
    private boolean allowBreak = false;
    private boolean allowPlace = false;
    private boolean allowInteract = false;

    @ScriptInternalConstructor
    public RegionGateBuilder(NekoId id, String stage) {
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
    public RegionGateBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    @Doc("Sets the stage evaluation scope ('player', 'server', 'either', or 'both').")
    @Param(name = "scope", value = "Scope name.")
    @Return("This builder for chaining.")
    public RegionGateBuilder scope(String scope) {
        this.scope = StageGateKind.parseScope(scope);
        return this;
    }

    @HideFromJS
    public RegionGateBuilder scope(StageScope scope) {
        this.scope = StageGateKind.requireScope(scope);
        return this;
    }

    @Doc("Sets a custom feedback message displayed when a region action is blocked.")
    @Param(name = "message", value = "Custom locked message, or empty to use default feedback.")
    @Return("This builder for chaining.")
    public RegionGateBuilder lockedMessage(String message) {
        this.lockedMessage = StageGateKind.normalizeText(message);
        return this;
    }

    @Doc("Adds an axis-aligned bounding-box region in the specified dimension.")
    @Param(name = "dimension", value = "Target dimension id.")
    @Param(name = "x1", value = "First corner X coordinate.")
    @Param(name = "y1", value = "First corner Y coordinate.")
    @Param(name = "z1", value = "First corner Z coordinate.")
    @Param(name = "x2", value = "Opposite corner X coordinate.")
    @Param(name = "y2", value = "Opposite corner Y coordinate.")
    @Param(name = "z2", value = "Opposite corner Z coordinate.")
    @Return("This builder for chaining.")
    public RegionGateBuilder box(
            NekoId dimension,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2
    ) {
        this.shapes.add(RegionShape.box(dimension, x1, y1, z1, x2, y2, z2));
        return this;
    }

    @Doc("Adds a spherical region in the specified dimension.")
    @Param(name = "dimension", value = "Target dimension id.")
    @Param(name = "centerX", value = "Sphere center X coordinate.")
    @Param(name = "centerY", value = "Sphere center Y coordinate.")
    @Param(name = "centerZ", value = "Sphere center Z coordinate.")
    @Param(name = "radius", value = "Sphere radius (must be > 0).")
    @Return("This builder for chaining.")
    public RegionGateBuilder sphere(
            NekoId dimension,
            double centerX,
            double centerY,
            double centerZ,
            double radius
    ) {
        this.shapes.add(RegionShape.sphere(dimension, centerX, centerY, centerZ, radius));
        return this;
    }

    @Doc("Adds pre-constructed region shapes to this gate.")
    @Param(name = "shapes", value = "One or more RegionShape definitions.")
    @Return("This builder for chaining.")
    public RegionGateBuilder shapes(RegionShape... shapes) {
        if (shapes == null) {
            throw new IllegalArgumentException("[NEKO-4071] Stage gate shapes array cannot be null");
        }
        for (RegionShape shape : shapes) {
            if (shape == null) {
                throw new IllegalArgumentException("[NEKO-4071] Stage gate shape entry cannot be null");
            }
            this.shapes.add(shape);
        }
        return this;
    }

    @Doc("Adds a single pre-constructed region shape to this gate.")
    @Param(name = "shape", value = "RegionShape definition.")
    @Return("This builder for chaining.")
    public RegionGateBuilder shape(RegionShape shape) {
        return shapes(shape);
    }

    @Doc("Allows entering gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowEnter() {
        return allowEnter(true);
    }

    @Doc("Configures whether entering gated regions is allowed.")
    @Param(name = "allow", value = "True to allow entry; false to deny.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowEnter(boolean allow) {
        this.allowEnter = allow;
        return this;
    }

    @Doc("Denies entering gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder denyEnter() {
        return allowEnter(false);
    }

    @Doc("Allows breaking blocks inside gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowBreak() {
        return allowBreak(true);
    }

    @Doc("Configures whether breaking blocks inside gated regions is allowed.")
    @Param(name = "allow", value = "True to allow breaking; false to deny.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowBreak(boolean allow) {
        this.allowBreak = allow;
        return this;
    }

    @Doc("Denies breaking blocks inside gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder denyBreak() {
        return allowBreak(false);
    }

    @Doc("Allows placing blocks inside gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowPlace() {
        return allowPlace(true);
    }

    @Doc("Configures whether placing blocks inside gated regions is allowed.")
    @Param(name = "allow", value = "True to allow placing; false to deny.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowPlace(boolean allow) {
        this.allowPlace = allow;
        return this;
    }

    @Doc("Denies placing blocks inside gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder denyPlace() {
        return allowPlace(false);
    }

    @Doc("Allows interacting inside gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowInteract() {
        return allowInteract(true);
    }

    @Doc("Configures whether interacting inside gated regions is allowed.")
    @Param(name = "allow", value = "True to allow interaction; false to deny.")
    @Return("This builder for chaining.")
    public RegionGateBuilder allowInteract(boolean allow) {
        this.allowInteract = allow;
        return this;
    }

    @Doc("Denies interacting inside gated regions without the required stage.")
    @Return("This builder for chaining.")
    public RegionGateBuilder denyInteract() {
        return allowInteract(false);
    }

    @HideFromJS
    public StageGateDeclaration.RegionGate buildDeclaration() {
        StageGateKind.requireTarget(id, !shapes.isEmpty());
        return new StageGateDeclaration.RegionGate(
                id,
                stage,
                priority,
                scope,
                lockedMessage,
                shapes,
                allowEnter,
                allowBreak,
                allowPlace,
                allowInteract
        );
    }
}
