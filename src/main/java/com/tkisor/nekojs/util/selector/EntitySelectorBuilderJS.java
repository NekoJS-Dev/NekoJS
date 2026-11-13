// 26.x implementation; this file should no longer contain version guards. The 1.21.1 implementation is in the
// file of the same name under versions/1.21.1/src. Keep it synchronized when changing this file's behavior.
package com.tkisor.nekojs.util.selector;

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Programmatic {@link EntitySelector} builder (ported from Katton's EntitySelectorBuilder,
 * mirroring {@link EntitySelectorParser} semantics: ±1 AABB padding, order selectors, distance functions,
 * predicate min/max bounds, limits, etc.), exposed to scripts via {@code EntitySelectors}.
 *
 * <p>26.1 and 26.2 have different {@code MinMaxBounds} package names (criterion / predicates).
 * Distance ranges and construction are bridged via reflection in {@link EntitySelectorFactory}; both versions share this class.
 * 1.21.1 is statically constructed.
 *
 * <p>Uncovered (v1 items): scores / advancements / NBT / loot predicate filtering.
 * The built selector has {@code usesSelector=false} and does not trigger vanilla selector permission checks.
 */
public class EntitySelectorBuilderJS {

    private int maxResults = 0;
    private boolean includesEntities = false;
    private boolean worldLimited = false;
    private Object distance;
    private Double x;
    private Double y;
    private Double z;
    private Double deltaX;
    private Double deltaY;
    private Double deltaZ;
    private final List<Predicate<Entity>> predicates = new ArrayList<>();
    private BiConsumer<Vec3, List<? extends Entity>> order = EntitySelector.ORDER_ARBITRARY;
    private EntityType<?> type;

    /**
     * Filters by entity type (e.g., {@code 'minecraft:cow'}); if {@code inverse} is true, excludes the type.
     * (Mirrors vanilla {@code type=!cow}: exclusion uses a predicate, the constructor's {@code type} stays null).
     *
     * <p>This method only adjusts the scope bit for a player type; a non-player type does <b>not</b> change the
     * scope. So when the base is a player set, {@code create(b => b.type('minecraft:cow'))} finds nothing,
     * because only {@code allEntities()} base sets can see cows. Letting explicit entity type filtering change
     * the scope would change public selector semantics, so the behavior stays as it is.
     *
     * <p>When entity scope is needed, start from an entity base preset ({@link #allEntities()},
     * {@link #nearestEntity()}, {@link #randomEntity()}) and then apply type/tag filtering.
     */
    public EntitySelectorBuilderJS type(String entityTypeId, boolean inverse) {
        EntityType<?> resolved = entityType(entityTypeId);
        if (inverse) {
            predicates.add(entity -> entity.getType() != resolved);
            if (isPlayerType(resolved)) {
                this.includesEntities = true;
            }
        } else {
            this.type = resolved;
            if (isPlayerType(resolved)) {
                this.includesEntities = false;
            }
        }
        return this;
    }

    /** Filters by entity type (positive match). */
    public EntitySelectorBuilderJS type(String entityTypeId) {
        return type(entityTypeId, false);
    }

    /**
     * Filters by entity tag (e.g., {@code 'minecraft:skeletons'}); if {@code inverse} is true, excludes the tag.
     *
     * <p>Like {@link #type}, it does <b>not</b> change the scope bit. A known tag is only evaluated against the
     * base set; for what happens with an unknown tag, see
     * {@link EntitySelectorsJS#resolveEntityTypeTag}.
     */
    public EntitySelectorBuilderJS typeTag(String tagId, boolean inverse) {
        var tag = EntitySelectorsJS.resolveEntityTypeTag(tagId);
        predicates.add(entity -> entity.is(tag) != inverse);
        return this;
    }

    /** Filters by entity tag (positive match). */
    public EntitySelectorBuilderJS typeTag(String tagId) {
        return typeTag(tagId, false);
    }

    /** Selects only living entities. */
    public EntitySelectorBuilderJS isAlive() {
        predicates.add(Entity::isAlive);
        return this;
    }

    /** Volume selection anchor (corresponds to vanilla {@code x=} parameter); enables world restriction. */
    public EntitySelectorBuilderJS x(double x) {
        this.x = x;
        this.worldLimited = true;
        return this;
    }

    public EntitySelectorBuilderJS y(double y) {
        this.y = y;
        this.worldLimited = true;
        return this;
    }

    public EntitySelectorBuilderJS z(double z) {
        this.z = z;
        this.worldLimited = true;
        return this;
    }

    /** Volume selection size (corresponds to vanilla {@code dx=} parameter, negative values extend downwards); enables world restriction. */
    public EntitySelectorBuilderJS dx(double deltaX) {
        this.deltaX = deltaX;
        this.worldLimited = true;
        return this;
    }

    public EntitySelectorBuilderJS dy(double deltaY) {
        this.deltaY = deltaY;
        this.worldLimited = true;
        return this;
    }

    public EntitySelectorBuilderJS dz(double deltaZ) {
        this.deltaZ = deltaZ;
        this.worldLimited = true;
        return this;
    }

    /** Distance range (Euclidean distance from anchor to target's feet); enables world restriction. */
    public EntitySelectorBuilderJS distance(double minDistance, double maxDistance) {
        if (minDistance < 0 || maxDistance < 0) {
            throw new IllegalArgumentException("EntitySelectors.distance: distance cannot be negative");
        }
        if (minDistance > maxDistance) {
            throw new IllegalArgumentException("EntitySelectors.distance: min distance cannot be greater than max distance");
        }
        this.distance = EntitySelectorFactory.doublesBetween(minDistance, maxDistance);
        this.worldLimited = true;
        return this;
    }

    /** Distance upper bound ({@code distance(0, max)}). */
    public EntitySelectorBuilderJS distanceBelow(double maxDistance) {
        if (maxDistance < 0) {
            throw new IllegalArgumentException("EntitySelectors.distanceBelow: distance cannot be negative");
        }
        this.distance = EntitySelectorFactory.doublesAtMost(maxDistance);
        this.worldLimited = true;
        return this;
    }

    /** Distance lower bound. */
    public EntitySelectorBuilderJS distanceAbove(double minDistance) {
        if (minDistance < 0) {
            throw new IllegalArgumentException("EntitySelectors.distanceAbove: distance cannot be negative");
        }
        this.distance = EntitySelectorFactory.doublesAtLeast(minDistance);
        this.worldLimited = true;
        return this;
    }

    /** Filters by name (scoreboard name); if {@code inverse} is true, excludes the name. */
    public EntitySelectorBuilderJS name(String name, boolean inverse) {
        predicates.add(entity -> entity.getScoreboardName().equals(name) != inverse);
        return this;
    }

    public EntitySelectorBuilderJS name(String name) {
        return name(name, false);
    }

    /** Filters by game mode ({@code survival|creative|adventure|spectator}); naturally excludes non-players. */
    public EntitySelectorBuilderJS gamemode(String gamemodeName, boolean inverse) {
        GameType gamemode = GameType.byName(gamemodeName.toLowerCase(Locale.ROOT));
        if (gamemode == GameType.DEFAULT_MODE && !"survival".equalsIgnoreCase(gamemodeName)) {
            throw new IllegalArgumentException("EntitySelectors.gamemode: unknown gamemode: " + gamemodeName);
        }
        predicates.add(entity -> {
            if (entity instanceof ServerPlayer player) {
                return (player.gameMode() == gamemode) != inverse;
            }
            return false;
        });
        this.includesEntities = false;
        return this;
    }

    public EntitySelectorBuilderJS gamemode(String gamemodeName) {
        return gamemode(gamemodeName, false);
    }

    /** Filters by scoreboard team; if {@code inverse} is true, excludes the team. */
    public EntitySelectorBuilderJS team(String team, boolean inverse) {
        predicates.add(entity -> {
            var entityTeam = entity.getTeam();
            String teamName = entityTeam == null ? "" : entityTeam.getName();
            return teamName.equals(team) != inverse;
        });
        return this;
    }

    public EntitySelectorBuilderJS team(String team) {
        return team(team, false);
    }

    /** Filters by entity scoreboard tag; empty string matches "no tag"; if {@code inverse} is true, excludes the tag. */
    public EntitySelectorBuilderJS tag(String tag, boolean inverse) {
        predicates.add(entity -> {
            var tags = entity.entityTags();
            return (tag.isEmpty() ? tags.isEmpty() : tags.contains(tag)) != inverse;
        });
        return this;
    }

    public EntitySelectorBuilderJS tag(String tag) {
        return tag(tag, false);
    }

    /** Filters by experience level; naturally excludes non-players. */
    public EntitySelectorBuilderJS level(int minLevel, int maxLevel) {
        if (minLevel < 0 || maxLevel < 0) {
            throw new IllegalArgumentException("EntitySelectors.level: level cannot be negative");
        }
        if (minLevel > maxLevel) {
            throw new IllegalArgumentException("EntitySelectors.level: min level cannot be greater than max level");
        }
        predicates.add(entity ->
                entity instanceof Player player
                        && player.experienceLevel >= minLevel
                        && player.experienceLevel <= maxLevel);
        this.includesEntities = false;
        return this;
    }

    /** Maximum result count ({@code >=1}). */
    public EntitySelectorBuilderJS limit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("EntitySelectors.limit: limit must be at least 1");
        }
        this.maxResults = limit;
        return this;
    }

    /** Order: arbitrary (default, stops at first match, fastest). */
    public EntitySelectorBuilderJS orderArbitrary() {
        this.order = EntitySelector.ORDER_ARBITRARY;
        return this;
    }

    /** Order: nearest first. */
    public EntitySelectorBuilderJS orderNearest() {
        this.order = EntitySelectorParser.ORDER_NEAREST;
        return this;
    }

    /** Order: farthest first. */
    public EntitySelectorBuilderJS orderFurthest() {
        this.order = EntitySelectorParser.ORDER_FURTHEST;
        return this;
    }

    /** Order: random. */
    public EntitySelectorBuilderJS orderRandom() {
        this.order = EntitySelectorParser.ORDER_RANDOM;
        return this;
    }

    /**
     * Constructs an {@link EntitySelector}. AABB / distance function semantics mirror
     * {@code EntitySelectorParser#create()}: volume parameters (dx/dy/dz) use ±1 AABB padding;
     * only distance upper bound generates a {@code 2d+1} box centered at the anchor.
     */
    public EntitySelector create() {
        AABB aabb;
        if (deltaX == null && deltaY == null && deltaZ == null) {
            Double maxDistance = EntitySelectorFactory.maxOf(distance);
            aabb = maxDistance == null
                    ? null
                    : new AABB(-maxDistance, -maxDistance, -maxDistance,
                            maxDistance + 1.0, maxDistance + 1.0, maxDistance + 1.0);
        } else {
            aabb = createAabb(
                    deltaX == null ? 0.0 : deltaX,
                    deltaY == null ? 0.0 : deltaY,
                    deltaZ == null ? 0.0 : deltaZ);
        }
        // When x/y/z are not set, the position function is the identity (the anchor passed to 'find' is used as-is).
        Function<Vec3, Vec3> position;
        if (x != null || y != null || z != null) {
            position = vec3 -> new Vec3(
                    x == null ? vec3.x : x,
                    y == null ? vec3.y : y,
                    z == null ? vec3.z : z);
        } else {
            position = Function.identity();
        }
        return EntitySelectorFactory.create(
                maxResults,
                includesEntities,
                worldLimited,
                List.copyOf(predicates),
                distance,
                position,
                aabb,
                order,
                type);
    }

    /** Mirrors {@code EntitySelectorParser#createAabb()}: negative delta extends downwards, positive adds +1 padding. */
    private static AABB createAabb(double d, double e, double f) {
        boolean negX = d < 0.0;
        boolean negY = e < 0.0;
        boolean negZ = f < 0.0;
        double minX = negX ? d : 0.0;
        double minY = negY ? e : 0.0;
        double minZ = negZ ? f : 0.0;
        double maxX = (negX ? 0.0 : d) + 1.0;
        double maxY = (negY ? 0.0 : e) + 1.0;
        double maxZ = (negZ ? 0.0 : f) + 1.0;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Resolves entity type by ID (defaults to {@code minecraft:} namespace).
     * 26.2 removed {@code EntityType.byString}; uses registry lookup instead (consistent with
     * {@code GoalRegistry}), applicable to 26.1 / 26.2 / 1.21.1.
     */
    private static EntityType<?> entityType(String entityTypeId) {
        String normalized = entityTypeId.indexOf(':') >= 0 ? entityTypeId : "minecraft:" + entityTypeId;
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.parse(normalized))
                .orElseThrow(() -> new IllegalArgumentException("EntitySelectors.type: unknown entity type: " + entityTypeId));
    }

    /** Checks if the type is a player (26.2 has no {@code EntityType.PLAYER} constant; uses registry comparison). */
    private static boolean isPlayerType(EntityType<?> resolved) {
        EntityType<?> player = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.parse("minecraft:player"))
                .orElse(null);
        return resolved == player;
    }

    /** All players ({@code @a}). */
    public static EntitySelectorBuilderJS allPlayers() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = Integer.MAX_VALUE;
        builder.includesEntities = false;
        return builder.type("minecraft:player");
    }

    /** All entities (including players, {@code @e}). */
    public static EntitySelectorBuilderJS allEntities() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = Integer.MAX_VALUE;
        builder.includesEntities = true;
        return builder;
    }

    /** Nearest player ({@code @p}, used with anchor sorting). */
    public static EntitySelectorBuilderJS nearestPlayer() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = false;
        return builder.orderNearest().type("minecraft:player");
    }

    /** Nearest entity (including players). */
    public static EntitySelectorBuilderJS nearestEntity() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = true;
        return builder.orderNearest();
    }

    /** Random player ({@code @r}). */
    public static EntitySelectorBuilderJS randomPlayer() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = false;
        return builder.orderRandom().type("minecraft:player");
    }

    /** Random entity (including players). */
    public static EntitySelectorBuilderJS randomEntity() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = true;
        return builder.orderRandom();
    }
}
