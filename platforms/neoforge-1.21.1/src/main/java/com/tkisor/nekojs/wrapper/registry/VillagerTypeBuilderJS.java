package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerType;

/**
 * 村民类型注册器（{@code StartupEvents.registry('villagerType')}）。
 *
 * <p>{@code VillagerType} 是纯标识类型（无配置字段），身份由注册 id 决定；
 * 贴图由 {@code assets/<ns>/textures/entity/villager/<type>/...} 提供，
 * 生物群系→类型映射由 data map 配置（不在本注册器职责内）。
 */
public class VillagerTypeBuilderJS {
    @Getter
    private final ResourceLocation location;

    public VillagerTypeBuilderJS(ResourceLocation location) {
        this.location = location;
    }

    public VillagerType create() {
        // 1.21.1: VillagerType(String name)，name 用于显示 key；用注册 id 的 path 作为内部名
        return new VillagerType(location.getPath());
    }
}
