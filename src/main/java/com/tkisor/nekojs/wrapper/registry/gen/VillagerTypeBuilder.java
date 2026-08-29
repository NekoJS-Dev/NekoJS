package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
//? if >=26 {
import net.minecraft.world.entity.npc.villager.VillagerType;
//?}
//? if <26 {
/*import net.minecraft.world.entity.npc.VillagerType;
*///?}

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
//? if >=26 {
        return new VillagerType();
//?} else {
/*        return new VillagerType(id.getPath());
*///?}
    }
}
