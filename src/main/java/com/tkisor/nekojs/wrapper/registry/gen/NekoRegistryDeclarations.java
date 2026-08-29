package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;

import java.util.List;

/**
 * 通用注册表体系的手写类型声明（probe 输出到 {@code @manual/index.d.ts}）。
 *
 * <p>事件 payload {@link RegistryEventJS} 的糖方法经 ProxyObject 动态暴露，
 * 反射声明生成器看不到——此处手写全量脚本面（糖方法 + 各 builder 的 public
 * field 面）。糖方法集与 {@code NekoRegistryPointsPlugin} 登记的类型清单一一对应；
 * NeoForge 特化糖方法（7 个）按平台守卫，Fabric 端只保留平台无关的 5 个。
 * EntityTypeBuilder 已由 {@code nekojs.entity-goal} 声明，此处不重复。
 */
public final class NekoRegistryDeclarations {
    private NekoRegistryDeclarations() {}

    public static void register(TypeDocsRegister registry) {
        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of(
                com.tkisor.nekojs.api.ScriptTypePredicate.exact(com.tkisor.nekojs.api.ScriptType.STARTUP),
                "nekojs.registry",
                "interface RegistryEvent { "
                        + sugar("soundEvent", "SoundEventBuilder")
                        + sugar("mobEffect", "MobEffectBuilder")
                        + sugar("potion", "PotionBuilder")
                        + sugar("paintingVariant", "PaintingVariantBuilder")
                        + sugar("villagerType", "VillagerTypeBuilder")
//? if neoforge {
                        + sugar("item", "ItemBuilder")
                        + sugar("block", "BlockBuilder")
                        + sugar("fluid", "FluidBuilder")
                        + sugar("entityType", "EntityTypeBuilder")
                        + sugar("enchantment", "EnchantmentBuilder")
                        + sugar("particleType", "ParticleTypeBuilder")
                        + sugar("creativeModeTab", "CreativeTabBuilder")
//?}
                        + "custom(id: string, typeName: string, cb: (b: any) => void): any; "
                        + "register(registry: string, id: string, supplier: () => any): any; } "
                        + "interface SoundEventBuilder { fixedRange: number | null; } "
                        + "interface MobEffectBuilder { category: string; color: number; } "
                        + "interface PotionBuilder { "
                        + "effect(effect: string, durationTicks: number, amplifier: number, "
                        + "ambient?: boolean, visible?: boolean): void; } "
                        + "interface PaintingVariantBuilder { width: number; height: number; "
                        + "assetId: string | null; "
//? if >=26 {
                        + "title: string | null; author: string | null; "
//?}
                        + "} "
                        + "interface VillagerTypeBuilder { } "
//? if neoforge {
                        + "interface ItemBuilder { maxStackSize: number; maxDamage: number; "
                        + "fireResistant: boolean; rarity: string; glowing: boolean; burnTime: number; "
                        + "groupTab: string | null; "
                        + "food(cb: (f: FoodBuilder) => void): void; "
                        + "tag(...tags: string[]): void; } "
                        + "interface FoodBuilder { nutrition(v: number): FoodBuilder; "
                        + "saturation(v: number): FoodBuilder; alwaysEat(): FoodBuilder; "
                        + "fastEat(): FoodBuilder; "
                        + "effect(effectId: string, durationTicks: number, amplifier: number, "
                        + "probability: number): FoodBuilder; } "
                        + "interface BlockBuilder { hardness: number; resistance: number; "
                        + "lightLevel: number; requiresTool: boolean; sound: string; mapColor: string; "
                        + "renderType: string | null; item: ItemBuilder | null; "
                        + "unbreakable(): void; noItem(): void; "
                        + "item(cb: (b: ItemBuilder) => void): void; "
                        + "tag(...tags: string[]): void; } "
                        + "interface FluidBuilder { displayName: string | null; density: number; "
                        + "temperature: number; viscosity: number; lightLevel: number; "
                        + "canConvertToSource: boolean; slopeFindDistance: number; "
                        + "levelDecreasePerBlock: number; explosionResistance: number; tickRate: number; "
                        + "bucket: boolean; block: boolean; "
                        + "noBucket(): void; noBlock(): void; "
                        + "tag(...tags: string[]): void; } "
                        + "interface EnchantmentBuilder { supportedItems: string | null; weight: number; "
                        + "maxLevel: number; minCostBase: number; minCostPerLevel: number; "
                        + "maxCostBase: number; maxCostPerLevel: number; anvilCost: number; "
                        + "slots: string; tag(...tags: string[]): void; } "
                        + "interface ParticleTypeBuilder { overrideLimiter: boolean; } "
                        + "interface CreativeTabBuilder { title: string; icon: any; "
                        + "add(item: any): void; } "
//?}
                        + "type RegistrySugar = 'soundEvent' | 'mobEffect' | 'potion' "
                        + "| 'paintingVariant' | 'villagerType'"
//? if neoforge {
                        + " | 'item' | 'block' | 'fluid' | 'entityType' | 'enchantment' "
                        + "| 'particleType' | 'creativeModeTab'"
//?}
                        + ";",
                "Generic registry surface: RegistryEvents.register collects builders once per boot; "
                        + "sugar methods come from the registry_types extension point (default type required).",
                List.of(
                        "RegistryEvents.register(event => { event.item('mymod:ruby', b => { b.maxStackSize = 16 }) })",
                        "RegistryEvents.register(event => { event.block('mymod:ruby_block', b => { b.noItem() }) })",
                        "RegistryEvents.register(event => { event.fluid('mymod:molten_iron', b => { b.noBucket() }) })")));
    }

    /** 糖方法双形态：(id, cb) 走 default 类型；(id, typeName, cb) 走命名类型。 */
    private static String sugar(String name, String builder) {
        return name + "(id: string, cb: (b: " + builder + ") => void): " + builder + "; "
                + name + "(id: string, typeName: string, cb: (b: " + builder + ") => void): " + builder + "; ";
    }
}
