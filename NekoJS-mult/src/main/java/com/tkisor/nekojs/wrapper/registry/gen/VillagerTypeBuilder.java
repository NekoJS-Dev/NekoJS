// 26.x 基准主干（DEVEX-ROADMAP 档 1 整文件拆分）：内联版本守卫已清零，1.21.1 孪生住在
// versions/1.21.1/src 同名文件（构造性变换）；改本文件行为时须同步孪生文件。
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
