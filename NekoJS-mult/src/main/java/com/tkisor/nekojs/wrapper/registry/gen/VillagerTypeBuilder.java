// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerType;

/**
 * 村民类型 builder：纯标识类型（无配置字段），身份由注册 id 决定；
 * 贴图由 {@code assets/<ns>/textures/entity/villager/<type>/...} 提供，
 * 生物群系→类型映射由 data map 配置（不在本 builder 职责内）。
 */
public class VillagerTypeBuilder extends RegistryObjectBuilder<VillagerType> {

    public VillagerTypeBuilder(Identifier id) {
        super(id);
    }

    @Override
    public VillagerType build() {
        return new VillagerType();
    }
}
