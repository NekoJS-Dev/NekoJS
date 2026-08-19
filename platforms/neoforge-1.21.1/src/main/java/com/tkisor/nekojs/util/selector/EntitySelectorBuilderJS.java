package com.tkisor.nekojs.util.selector;

import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import java.util.function.Predicate;

/**
 * 程序化 {@link EntitySelector} 流式构建器（移植自 Katton 的 EntitySelectorBuilder，
 * 语义镜像 {@link EntitySelectorParser}：±1 AABB padding、顺序选择器、距离函数、
 * 经 min/max 的谓词、limit 等），经全局绑定 {@code EntitySelectors} 暴露给脚本。
 *
 * <p>与 26.x 镜像的差异仅在于 MC API：{@code MinMaxBounds} 包名
 * （{@code critereon}）、经验模式访问器（{@code gameMode.getGameModeForPlayer()}）、
 * 实体标签（{@code getTags()}）与 {@code Doubles.max()} 直接返回 Optional。
 *
 * <p>未覆盖（v1 后续项）：scores / advancements / NBT / loot predicate 过滤。
 * 构建的 selector {@code usesSelector=false}，不触发原版的 selector 权限校验。
 */
public class EntitySelectorBuilderJS {

    private int maxResults = 0;
    private boolean includesEntities = false;
    private boolean worldLimited = false;
    private MinMaxBounds.Doubles distance;
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
     * 实体类型过滤（如 {@code 'minecraft:cow'}）；inverse 为 true 时反选
     * （镜像原版 {@code type=!cow}：反选走谓词，ctor type 保持 null）。
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

    /** 实体类型过滤（正选）。 */
    public EntitySelectorBuilderJS type(String entityTypeId) {
        return type(entityTypeId, false);
    }

    /** 按实体 tag 过滤（如 {@code 'minecraft:skeletons'}）；inverse 为 true 时反选。 */
    public EntitySelectorBuilderJS typeTag(String tagId, boolean inverse) {
        var tag = EntitySelectorsJS.resolveEntityTypeTag(tagId);
        // 1.21.1 的 Entity 无注入的 is(TagKey)（26.x 有），走 EntityType#is
        Predicate<Entity> predicate = entity -> entity.getType().is(tag) != inverse;
        predicates.add(predicate);
        return this;
    }

    /** 按实体 tag 过滤（正选）。 */
    public EntitySelectorBuilderJS typeTag(String tagId) {
        return typeTag(tagId, false);
    }

    /** 只选存活实体。 */
    public EntitySelectorBuilderJS isAlive() {
        predicates.add(Entity::isAlive);
        return this;
    }

    /** 体积选区锚点（对应原版 {@code x=} 参数）；会启用世界限定。 */
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

    /** 体积选区尺寸（对应原版 {@code dx=} 参数，负值向下扩展）；会启用世界限定。 */
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

    /** 距离区间（从锚点量到目标脚底欧氏距离）；会启用世界限定。 */
    public EntitySelectorBuilderJS distance(double minDistance, double maxDistance) {
        if (minDistance < 0 || maxDistance < 0) {
            throw new IllegalArgumentException("distance cannot be negative");
        }
        if (minDistance > maxDistance) {
            throw new IllegalArgumentException("min distance cannot be greater than max distance");
        }
        this.distance = MinMaxBounds.Doubles.between(minDistance, maxDistance);
        this.worldLimited = true;
        return this;
    }

    /** 距离上限（{@code distance(0, max)}）。 */
    public EntitySelectorBuilderJS distanceBelow(double maxDistance) {
        if (maxDistance < 0) {
            throw new IllegalArgumentException("distance cannot be negative");
        }
        this.distance = MinMaxBounds.Doubles.atMost(maxDistance);
        this.worldLimited = true;
        return this;
    }

    /** 距离下限。 */
    public EntitySelectorBuilderJS distanceAbove(double minDistance) {
        if (minDistance < 0) {
            throw new IllegalArgumentException("distance cannot be negative");
        }
        this.distance = MinMaxBounds.Doubles.atLeast(minDistance);
        this.worldLimited = true;
        return this;
    }

    /** 按名称过滤（scoreboard 名）；inverse 为 true 时反选。 */
    public EntitySelectorBuilderJS name(String name, boolean inverse) {
        predicates.add(entity -> entity.getScoreboardName().equals(name) != inverse);
        return this;
    }

    public EntitySelectorBuilderJS name(String name) {
        return name(name, false);
    }

    /** 按游戏模式过滤（{@code survival|creative|adventure|spectator}）；天然排除非玩家。 */
    public EntitySelectorBuilderJS gamemode(String gamemodeName, boolean inverse) {
        GameType gamemode = GameType.byName(gamemodeName.toLowerCase(Locale.ROOT));
        if (gamemode == GameType.DEFAULT_MODE && !"survival".equalsIgnoreCase(gamemodeName)) {
            throw new IllegalArgumentException("unknown gamemode: " + gamemodeName);
        }
        predicates.add(entity -> {
            if (entity instanceof ServerPlayer player) {
                return (player.gameMode.getGameModeForPlayer() == gamemode) != inverse;
            }
            return false;
        });
        this.includesEntities = false;
        return this;
    }

    public EntitySelectorBuilderJS gamemode(String gamemodeName) {
        return gamemode(gamemodeName, false);
    }

    /** 按计分板队伍过滤；inverse 为 true 时反选。 */
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

    /** 按实体标签（scoreboard tag）过滤；空串匹配「无标签」；inverse 为 true 时反选。 */
    public EntitySelectorBuilderJS tag(String tag, boolean inverse) {
        predicates.add(entity -> {
            var tags = entity.getTags();
            return (tag.isEmpty() ? tags.isEmpty() : tags.contains(tag)) != inverse;
        });
        return this;
    }

    public EntitySelectorBuilderJS tag(String tag) {
        return tag(tag, false);
    }

    /** 按经验等级过滤；天然排除非玩家。 */
    public EntitySelectorBuilderJS level(int minLevel, int maxLevel) {
        if (minLevel < 0 || maxLevel < 0) {
            throw new IllegalArgumentException("level cannot be negative");
        }
        if (minLevel > maxLevel) {
            throw new IllegalArgumentException("min level cannot be greater than max level");
        }
        predicates.add(entity ->
                entity instanceof Player player
                        && player.experienceLevel >= minLevel
                        && player.experienceLevel <= maxLevel);
        this.includesEntities = false;
        return this;
    }

    /** 结果数量上限（&ge;1）。 */
    public EntitySelectorBuilderJS limit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        this.maxResults = limit;
        return this;
    }

    /** 顺序：任意（默认，找到即截断，最快）。 */
    public EntitySelectorBuilderJS orderArbitrary() {
        this.order = EntitySelector.ORDER_ARBITRARY;
        return this;
    }

    /** 顺序：最近优先。 */
    public EntitySelectorBuilderJS orderNearest() {
        this.order = EntitySelectorParser.ORDER_NEAREST;
        return this;
    }

    /** 顺序：最远优先。 */
    public EntitySelectorBuilderJS orderFurthest() {
        this.order = EntitySelectorParser.ORDER_FURTHEST;
        return this;
    }

    /** 顺序：随机。 */
    public EntitySelectorBuilderJS orderRandom() {
        this.order = EntitySelectorParser.ORDER_RANDOM;
        return this;
    }

    /**
     * 构建 {@link EntitySelector}。AABB / 距离函数语义镜像
     * {@code EntitySelectorParser#create()}：体积参数（dx/dy/dz）走 ±1 padding
     * 的 AABB；仅距离上限时以锚点为中心生成 {@code 2d+1} 盒。
     */
    public EntitySelector create() {
        AABB aabb;
        if (deltaX == null && deltaY == null && deltaZ == null) {
            Double maxDistance = maxDistanceOrNull(distance);
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
        // 未设置 x/y/z 时位置函数为恒等（find 传入的锚点原样生效）
        java.util.function.Function<Vec3, Vec3> position;
        if (x != null || y != null || z != null) {
            position = vec3 -> new Vec3(
                    x == null ? vec3.x : x,
                    y == null ? vec3.y : y,
                    z == null ? vec3.z : z);
        } else {
            position = java.util.function.Function.identity();
        }
        return new EntitySelector(
                maxResults,
                includesEntities,
                worldLimited,
                List.copyOf(predicates),
                distance,
                position,
                aabb,
                order,
                false,
                null,
                null,
                type,
                false);
    }

    /** 1.21.1：{@code Doubles.max()} 直接返回 Optional（26.x 为 {@code bounds().max()}）。 */
    private static Double maxDistanceOrNull(MinMaxBounds.Doubles range) {
        if (range == null) {
            return null;
        }
        return range.max().orElse(null);
    }

    /** 镜像 {@code EntitySelectorParser#createAabb()}：负 delta 向下扩展，正向 +1 padding。 */
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

    /** 按 id 解析实体类型（缺省 {@code minecraft:} 命名空间），走注册表查询。 */
    private static EntityType<?> entityType(String entityTypeId) {
        String normalized = entityTypeId.indexOf(':') >= 0 ? entityTypeId : "minecraft:" + entityTypeId;
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(ResourceLocation.parse(normalized))
                .orElseThrow(() -> new IllegalArgumentException("unknown entity type: " + entityTypeId));
    }

    /** 是否为玩家类型（经注册表比较，保持与 26.x 镜像一致）。 */
    private static boolean isPlayerType(EntityType<?> resolved) {
        EntityType<?> player = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecraft:player"));
        return resolved == player;
    }

    /** 全部玩家（{@code @a}）。 */
    public static EntitySelectorBuilderJS allPlayers() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = Integer.MAX_VALUE;
        builder.includesEntities = false;
        return builder.type("minecraft:player");
    }

    /** 全部实体（含玩家，{@code @e}）。 */
    public static EntitySelectorBuilderJS allEntities() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = Integer.MAX_VALUE;
        builder.includesEntities = true;
        return builder;
    }

    /** 最近的玩家（{@code @p}，配合锚点排序）。 */
    public static EntitySelectorBuilderJS nearestPlayer() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = false;
        return builder.orderNearest().type("minecraft:player");
    }

    /** 最近的实体（含玩家）。 */
    public static EntitySelectorBuilderJS nearestEntity() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = true;
        return builder.orderNearest();
    }

    /** 随机玩家（{@code @r}）。 */
    public static EntitySelectorBuilderJS randomPlayer() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = false;
        return builder.orderRandom().type("minecraft:player");
    }

    /** 随机实体（含玩家）。 */
    public static EntitySelectorBuilderJS randomEntity() {
        var builder = new EntitySelectorBuilderJS();
        builder.maxResults = 1;
        builder.includesEntities = true;
        return builder.orderRandom();
    }
}
