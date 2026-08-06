package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Entity 跨平台统一扩展规范。
 *
 * <p>各平台的 {@code EntityExtension} 必须 {@code extends EntitySpec}。
 *
 * <p><b>碰撞排除（调研驱动）：</b>以下方法因 NF 原生 {@code Entity} 零参同名碰撞
 * （Graal 无法分派）而<b>不</b>在本 spec 中：
 * <ul>
 *   <li>{@code getId()} —— NF 原生有 {@code int getId()}，neko 版返回 registry id 字符串。
 *       各平台用 {@code @Remap("getRegistryId")} 改名，避免碰撞。
 *   <li>{@code getX()/getY()/getZ()} —— NF 原生有零参 {@code double getX()}，行为一致。
 *       NF/NF121 删除 neko 注入版（JS 直接用原生）；CR 无原生 getter，保留 neko 版。
 * </ul>
 *
 * <p>{@link #neko$getLevel()} 返回 {@code Object}（NF 返回 {@code Level}，CR 返回 {@code World}），
 * spec 不声明具体类型以遵守 common-api 边界约束。两平台原生均<b>无</b> {@code getLevel()} 方法
 * （NF 是 {@code level()}，CR 是 {@code getEntityWorld()}），故无碰撞。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface EntitySpec {

    /** 实体所在维度。NF 返回 Level，CR 返回 World。 */
    default Object neko$getLevel() {
        throw new UnsupportedOperationException("EntitySpec.neko$getLevel not implemented");
    }

    /** 移除/杀死实体。NF 调 kill(ServerLevel)，CR 调 setDead()。 */
    default boolean neko$kill() {
        throw new UnsupportedOperationException("EntitySpec.neko$kill not implemented");
    }

    /** 同世界内传送实体到指定坐标。 */
    default void neko$teleport(double x, double y, double z) {
        throw new UnsupportedOperationException("EntitySpec.neko$teleport not implemented");
    }

    /** 移除实体（与 kill 类似但 void 返回）。NF 调 discard()，CR 调 setDead()。 */
    default void neko$remove() {
        throw new UnsupportedOperationException("EntitySpec.neko$remove not implemented");
    }
}
