package com.tkisor.nekojs.util.selector;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
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
 * <p><b>作用域基座（工单 25 双轴审查后钉住现状）</b>：{@link #builder()} / {@link #create}
 * 的基座是<b>玩家集合</b>（{@code includesEntities=false}，等价 {@code @a}）；
 * {@link #allEntities()} / {@link #nearestEntity()} / {@link #randomEntity()} 的基座是
 * <b>实体集合</b>。{@code type(...)} / {@code typeTag(...)} <b>不</b>切作用域（仅玩家类型
 * 会调整该位），所以查非玩家实体必须用实体基座预设。
 *
 * <p>脚本示例：
 * <pre>{@code
 * // 32 格内的 5 头牛（按距离排序）——实体基座 + 类型过滤
 * const cows = EntitySelectors.find(level,
 *     EntitySelectors.allEntities().type('minecraft:cow').distance(0, 32).limit(5).create());
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
     * <p><b>characterization（工单 25 双轴审查后钉住现状）</b>：本方法<b>不</b>校验该
     * tag 是否已被当前数据包声明——未声明的 tag 会得到一个没有任何成员的
     * {@link TagKey}，于是使用它的过滤条件恒假、查询恒返回空（silent no-op）。
     *
     * <p>这是<b>已记录的缺口</b>（REPORT §7 N8，owner = 维护者裁决 / domain 票）：spec 04
     * 禁止静默 no-op，因此「未声明即抛带域+入口的普通错误」是期望修法——该修法在本票
     * 实现过并经游戏内实证，但它是<b>公开语义变更</b>（静默空结果 → 报错），超出工单 25
     * 「建立 fixture」的授权（项目纪律：公开语义变更先回决策），故回退为现状并在此写明。
     * 游戏内证据：{@code runserver-25971-postfix-extract.log} 的
     * {@code builder().typeTag('nekojs:no_such_tag') -> EntitySelectorBuilderJS@…}（无异常）。
     *
     * <p><b>唯一保留的入参校验是 {@code null}</b>（审查明确保留 AC3「错误含域+调用入口」
     * 的合规改进，同 {@code distance}/limit 等消息前缀）：{@code tagId == null} 抛带域+入口的
     * 普通错误，而不是让 {@code Identifier.parse(null)} 抛裸 NPE。它不改变任何既有可观察语义
     * （原本就是错误路径，只是错误类型与可读性），由
     * {@code EntitySelectorsQueryBindingTest.typeTagNullErrorCarriesDomainAndEntry} 守护。
     */
    static TagKey<EntityType<?>> resolveEntityTypeTag(String tagId) {
        if (tagId == null) {
            throw new IllegalArgumentException("EntitySelectors.typeTag: tag must not be null");
        }
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(tagId));
    }
}
