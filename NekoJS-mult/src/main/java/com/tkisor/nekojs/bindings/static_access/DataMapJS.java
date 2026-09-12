//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * NeoForge data map 查询 helper（绑定全局 {@code DataMap}，KubeJS {@code DataMapWrapper}
 * 的对标——只搬脚本最常用的内置 data map；其余经 {@code Registry.get(...).dataMapValue(...)} 查询）。
 *
 * <p>source contract 归类（ticket 25）：query binding——只读查询，不事件化、无生命周期
 * Point；MC-facing data map 类型（{@link NeoForgeDataMaps}）由本类（neoforge 守卫）
 * 持有，共享契约不引入 MC/loader 依赖（替代面 {@code RegistryView.dataMapValue} 返回
 * portable JSON 字符串）。
 *
 * <p>查询不到（条目没有该 data map 值）返回 {@code null} 而非报错，便于 {@code ??} 兜底；
 * 非法输入（{@code null} 栈）抛带域+入口的普通错误，不含修复提示。方法只读——不暴露
 * 可变 registry view。
 */
public class DataMapJS {

    /** 该物品的熔炉燃料燃烧 tick 数；不是燃料返回 {@code null}。 */
    public Integer furnaceFuel(ItemStack stack) {
        // 26.x 的 ItemStack 无 getItemHolder——从注册表 wrap 等价 holder（见 ItemStackExtension 同款注释）
        FurnaceFuel fuel = itemHolder(stack, "furnaceFuel").getData(NeoForgeDataMaps.FURNACE_FUELS);
        return fuel == null ? null : fuel.burnTime();
    }

    /** 该物品的堆肥概率（0.0 ~ 1.0）；不可堆肥返回 {@code null}。 */
    public Float compostable(ItemStack stack) {
        Compostable compostable = itemHolder(stack, "compostable").getData(NeoForgeDataMaps.COMPOSTABLES);
        return compostable == null ? null : compostable.chance();
    }

    /** 入参守卫：错误消息带域+调用入口（源位置由统一错误管线从脚本栈提取），无修复提示。 */
    private static Holder<Item> itemHolder(ItemStack stack, String entry) {
        if (stack == null) {
            throw new IllegalArgumentException("DataMap." + entry + ": stack must not be null");
        }
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
    }
}
//?}
