//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * NeoForge data map 查询 helper（绑定全局 {@code DataMap}，KubeJS {@code DataMapWrapper}
 * 的对标——只搬脚本最常用的内置 data map；其余经 {@code Registry.get(...).dataMapValue(...)} 查询）。
 *
 * <p>查询不到（条目没有该 data map 值）返回 {@code null} 而非报错，便于 {@code ??} 兜底。
 */
public class DataMapJS {

    /** 该物品的熔炉燃料燃烧 tick 数；不是燃料返回 {@code null}。 */
    public Integer furnaceFuel(ItemStack stack) {
        FurnaceFuel fuel = stack.getItemHolder().getData(NeoForgeDataMaps.FURNACE_FUELS);
        return fuel == null ? null : fuel.burnTime();
    }

    /** 该物品的堆肥概率（0.0 ~ 1.0）；不可堆肥返回 {@code null}。 */
    public Float compostable(ItemStack stack) {
        Compostable compostable = stack.getItemHolder().getData(NeoForgeDataMaps.COMPOSTABLES);
        return compostable == null ? null : compostable.chance();
    }
}
//?}
