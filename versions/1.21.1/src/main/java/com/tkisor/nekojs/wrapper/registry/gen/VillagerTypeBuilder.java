// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分）：主干已 26.x 基准化，本文件为 1.21.1 的
// 完整实现（构造性变换）；主干行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerType;

/**
 * 村民类型 builder：纯标识类型（无配置字段），身份由注册 id 决定；
 * 贴图由 {@code assets/<ns>/textures/entity/villager/<type>/...} 提供，
 * 生物群系→类型映射由 data map 配置（不在本 builder 职责内）。
 */
public class VillagerTypeBuilder extends RegistryObjectBuilder<VillagerType> {

    public VillagerTypeBuilder(ResourceLocation id) {
        super(id);
    }

    @Override
    public VillagerType build() {
        return new VillagerType(id.getPath());
    }
}
