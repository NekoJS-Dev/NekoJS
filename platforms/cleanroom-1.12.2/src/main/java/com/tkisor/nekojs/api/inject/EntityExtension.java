package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.EntitySpec;
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
 *   <li>{@code Level} → {@code World}，提供 {@code getLevel()} 作为 {@code getEntityWorld()} 的 alias</li>
 *   <li>实体 id 用 {@link EntityList#getKey(Entity)}（返回带 namespace 的 {@link ResourceLocation}）</li>
 * </ul>
 *
 * @see Entity
 */
@RemapByPrefix("neko$")
public interface EntityExtension extends EntitySpec {

    private Entity self() {
        return (Entity) this;
    }

    /** 取实体类型注册 id。CR 原生无 getId 零参方法，不碰撞，保留原名。 */
    default String neko$getId() {
        ResourceLocation key = EntityList.getKey(self());
        if (key != null) {
            return key.toString();
        }
        String name = EntityList.getEntityString(self());
        return name != null ? "minecraft:" + name : null;
    }

    @Override
    default boolean neko$kill() {
        self().setDead();
        return true;
    }

    @Override
    default void neko$remove() {
        self().setDead();
    }

    @Override
    default void neko$teleport(double x, double y, double z) {
        self().setPosition(x, y, z);
    }

    // CR 原生 Entity 无 getX/Y/Z getter（用 posX/posY/posZ 字段），neko 版提供统一 getter
    default double neko$getX() {
        return self().posX;
    }

    default double neko$getY() {
        return self().posY;
    }

    default double neko$getZ() {
        return self().posZ;
    }

    @Override
    default Object neko$getLevel() {
        return self().getEntityWorld();
    }

    /**
     * 判断实体是否带有指定标签。对齐 NF {@code neko$hasTag}。
     * 1.12.2 {@link Entity#getTags()} 直接返回 {@code Set<String>}（vanilla），无需注册表。
     *
     * @param tag 标签名
     * @return {@code true} 若实体带此标签
     */
    default boolean neko$hasTag(String tag) {
        return tag != null && self().getTags().contains(tag);
    }
}
