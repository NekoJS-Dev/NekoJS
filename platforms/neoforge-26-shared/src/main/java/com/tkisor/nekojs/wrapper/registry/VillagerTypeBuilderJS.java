package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * 村民类型注册器（{@code StartupEvents.registry('villagerType')}）。
 *
 * <p>{@code VillagerType} 是纯标识类型（无配置字段），身份由注册 id 决定；
 * 贴图由 {@code assets/<ns>/textures/entity/villager/<type>/...} 提供，
 * 生物群系→类型映射由 data map 配置（不在本注册器职责内）。
 */
public class VillagerTypeBuilderJS {
    @Getter
    private final Identifier location;

    public VillagerTypeBuilderJS(Identifier location) {
        this.location = location;
    }

    public VillagerType create() {
        return new VillagerType();
    }
}
