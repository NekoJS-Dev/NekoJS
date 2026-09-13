package com.tkisor.nekojs.util.selector;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * 全局绑定 {@code EntitySelectors}：{@link EntitySelectorBuilderJS} 的工厂与查询入口。
 *
 * <p>source contract 归类（ticket 25）：query binding——factory/builder/query 只读查询，
 * 不事件化、无生命周期 Point；server/test side 限定（查询需 {@code ServerLevel}）。
 * 非法输入抛带域+调用入口的普通错误（源位置由统一错误管线从脚本栈提取），不含修复提示。
 *
 * <p>脚本示例：
 * <pre>{@code
 * // 32 格内的 5 头牛（按距离排序）
 * const cows = EntitySelectors.find(level,
 *     EntitySelectors.create(b => b.type('minecraft:cow').distance(0, 32).limit(5)));
 * // 最近的玩家
 * const players = EntitySelectors.find(level, EntitySelectors.nearestPlayer().create(),
 *     player.x, player.y, player.z);
 * }</pre>
 *
 * <p>{@code find} 的锚点（x/y/z）同时是距离量测原点与体积选区锚点；不传时为
 * {@code (0, 0, 0)}。builder 的 {@code x()/y()/z()} 会覆盖锚点的对应分量
 * （原版 selector 语义）。排序选择器（nearest/furthest/random）务必显式传锚点。
 */
public class EntitySelectorsJS {

    /** 从 builder 配置回调创建 selector：{@code create(b => b.type('minecraft:cow'))}。 */
    public EntitySelector create(Consumer<EntitySelectorBuilderJS> config) {
        if (config == null) {
            throw new IllegalArgumentException("EntitySelectors.create: config must not be null");
        }
        var builder = new EntitySelectorBuilderJS();
        config.accept(builder);
        return builder.create();
    }

    /** 空构建器（后续链式配置）。 */
    public EntitySelectorBuilderJS builder() {
        return new EntitySelectorBuilderJS();
    }

    /** 全部玩家（{@code @a}）。 */
    public EntitySelectorBuilderJS allPlayers() {
        return EntitySelectorBuilderJS.allPlayers();
    }

    /** 全部实体（含玩家，{@code @e}）。 */
    public EntitySelectorBuilderJS allEntities() {
        return EntitySelectorBuilderJS.allEntities();
    }

    /** 最近的玩家（{@code @p}）。 */
    public EntitySelectorBuilderJS nearestPlayer() {
        return EntitySelectorBuilderJS.nearestPlayer();
    }

    /** 最近的实体（含玩家）。 */
    public EntitySelectorBuilderJS nearestEntity() {
        return EntitySelectorBuilderJS.nearestEntity();
    }

    /** 随机玩家（{@code @r}）。 */
    public EntitySelectorBuilderJS randomPlayer() {
        return EntitySelectorBuilderJS.randomPlayer();
    }

    /** 随机实体（含玩家）。 */
    public EntitySelectorBuilderJS randomEntity() {
        return EntitySelectorBuilderJS.randomEntity();
    }

    /**
     * 在指定维度执行 selector，返回匹配实体（含玩家；{@code includesEntities=false}
     * 的 selector 只查玩家）。锚点默认 {@code (0, 0, 0)}。
     */
    public List<? extends Entity> find(ServerLevel level, EntitySelector selector) {
        return find(level, selector, 0, 0, 0);
    }

    /** 在指定维度执行 selector，锚点为 {@code (x, y, z)}（距离原点 / 体积选区锚）。 */
    public List<? extends Entity> find(ServerLevel level, EntitySelector selector, double x, double y, double z) {
        if (level == null) {
            throw new IllegalArgumentException("EntitySelectors.find: level must not be null");
        }
        if (selector == null) {
            throw new IllegalArgumentException("EntitySelectors.find: selector must not be null");
        }
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(new Vec3(x, y, z));
        try {
            return selector.findEntities(source);
        } catch (Exception e) {
            // findEntities 声明 CommandSyntaxException，但脚本构建的 selector
            // （usesSelector=false）不会触发语法/权限分支；防御性兜底转运行时异常
            throw new IllegalStateException("EntitySelectors.find: entity selector query failed", e);
        }
    }

    /**
     * 解析实体类型 tag id（如 {@code 'minecraft:skeletons'}）为 {@link TagKey}。
     *
     * <p>ticket 25 修复（游戏内实证）：未知 tag 原实现静默构造一个空 {@link TagKey}，
     * 于是过滤条件恒假、查询恒返回空——正是 spec 04 禁止的静默 no-op，也与
     * {@code type(...)}（未知实体类型直接报错）不一致。改为按声明存在性校验：
     * 未声明的 tag 抛带域+入口的普通错误。
     */
    static TagKey<EntityType<?>> resolveEntityTypeTag(String tagId) {
        if (tagId == null) {
            throw new IllegalArgumentException("EntitySelectors.typeTag: tag must not be null");
        }
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(tagId));
        if (!entityTypeTagDeclared(tag)) {
            throw new IllegalArgumentException("EntitySelectors.typeTag: unknown entity type tag: " + tagId);
        }
        return tag;
    }

    /**
     * 该实体类型标签是否已被当前数据包声明。版本差异按 stonecutter 守卫分支：
     * 26.x 只有 {@code getTags()}，1.21.1 是 {@code getTagNames()}（同
     * {@code NeoForgeCatalogPlatformProvider.tagIds} 的既有写法）。
     */
    private static boolean entityTypeTagDeclared(TagKey<EntityType<?>> tag) {
//? if >=26 {
        return BuiltInRegistries.ENTITY_TYPE.getTags().anyMatch(named -> named.key().equals(tag));
//?} else {
/*        return BuiltInRegistries.ENTITY_TYPE.getTagNames().anyMatch(existing -> existing.equals(tag));
*///?}
    }
}
