package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * 1.12.2 {@link Entity} 统一扩展方法，注入到 MC 的 {@link Entity} 基类。
 *
 * <p>1.12.2 与 1.21.1 的关键差异：
 * <ul>
 *   <li>没有 {@code teleportTo}，传送用 {@link Entity#setPosition(double, double, double)}</li>
 *   <li>没有 {@code kill(ServerLevel)}，移除实体用 {@link Entity#setDead()}</li>
 *   <li>{@code Level} → {@code World}，提供 {@code getLevel()} 作为 {@code getWorld()} 的 alias</li>
 *   <li>实体 id 用 {@link EntityList#getKey(Entity)}（返回带 namespace 的 {@link ResourceLocation}）</li>
 * </ul>
 *
 * @see Entity
 * @author ZZZank
 */
@RemapByPrefix("neko$")
public interface EntityExtension {

    private Entity self() {
        return (Entity) this;
    }

    /**
     * 取实体类型注册 id（{@code minecraft:cow} 形式）。
     * 1.12.2 用 {@link EntityList#getKey(Entity)}，它返回的 {@link ResourceLocation} 已包含
     * namespace（vanilla 是 {@code minecraft:}，modded 是各自 mod id），覆盖原版与 modded 实体。
     * <p>回退 {@link EntityList#getEntityString(Entity)}（仅返回 path，无 namespace），
     * 用于极少数未通过标准注册表登记的实体。
     *
     * @return 实体类型 id；未知返回 null
     */
    default String neko$getId() {
        ResourceLocation key = EntityList.getKey(self());
        if (key != null) {
            return key.toString();
        }
        // 回退：旧式 entity_string（仅 path，无 namespace）
        String name = EntityList.getEntityString(self());
        return name != null ? "minecraft:" + name : null;
    }

    /**
     * 移除实体（等效 1.21.1 {@code kill()}）。
     * 1.12.2 没有 {@code kill(ServerLevel)}，直接 {@link Entity#setDead()} 即可。
     *
     * @return 总是 {@code true}（1.12.2 的 setDead 不会失败）
     */
    default boolean neko$kill() {
        self().setDead();
        return true;
    }

    /**
     * {@code remove} 的 alias，与 1.21.1 的 {@code remove(Entity.RemovalReason)} 对齐。
     * 1.12.2 没有 RemovalReason 概念，统一调用 {@link Entity#setDead()}。
     */
    default void neko$remove() {
        self().setDead();
    }

    /**
     * 传送实体到指定坐标。对齐 1.21.1 {@code teleportTo(double, double, double)}。
     * 1.12.2 用 {@link Entity#setPosition(double, double, double)}（同世界内传送）。
     *
     * @param x 目标 x
     * @param y 目标 y
     * @param z 目标 z
     */
    default void neko$teleport(double x, double y, double z) {
        self().setPosition(x, y, z);
    }

    default double neko$getX() {
        return self().posX;
    }

    default double neko$getY() {
        return self().posY;
    }

    default double neko$getZ() {
        return self().posZ;
    }

    /**
     * 返回实体所在 World。对齐 1.21.1 {@code level()}——在 1.12.2 等价于 {@code getWorld()}。
     *
     * @return 实体所在 World
     */
    default World neko$getLevel() {
        return self().getEntityWorld();
    }
}
