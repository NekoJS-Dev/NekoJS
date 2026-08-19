package com.tkisor.nekojs.util.selector;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 全局绑定 {@code EntitySelectors}：{@link EntitySelectorBuilderJS} 的工厂与查询入口
 * （1.21.1 镜像，差异仅在 tag 解析走 {@code ResourceLocation}）。
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
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(selector, "selector");
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(new Vec3(x, y, z));
        try {
            return selector.findEntities(source);
        } catch (Exception e) {
            // findEntities 声明 CommandSyntaxException，但脚本构建的 selector
            // （usesSelector=false）不会触发语法/权限分支；防御性兜底转运行时异常
            throw new IllegalStateException("entity selector query failed", e);
        }
    }

    /** 解析实体类型 tag id（如 {@code 'minecraft:skeletons'}）为 {@link TagKey}。 */
    static TagKey<EntityType<?>> resolveEntityTypeTag(String tagId) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(tagId));
    }
}
