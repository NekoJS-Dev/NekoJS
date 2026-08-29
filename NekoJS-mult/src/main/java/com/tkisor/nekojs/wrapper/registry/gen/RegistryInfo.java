package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 通用注册表元信息（ADR-0004：贡献式扫描根的反射产物）。
 *
 * @param key   注册表资源键（如 {@code minecraft:item}）
 * @param token 注册表持有的基础类型令牌（如 {@code Item.class}，供诊断与类型工厂校验）
 */
public record RegistryInfo(ResourceKey<? extends Registry<?>> key, Class<?> token) {

    /**
     * 本注册表在脚本糖方法上的方法名：ResourceKey path 段的 snake_case 转
     * lowerCamelCase（{@code entity_type} → {@code entityType}），与旧类型化入口
     * 的脚本命名保持一致。不同 path 归并到同名糖方法时首胜（ LinkedHashMap 首胜语义）。
     */
    public String sugarName() {
        String[] parts = key.identifier().getPath().split("_");
        StringBuilder name = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return name.toString();
    }

    /** 便捷构造。 */
    public static RegistryInfo of(ResourceKey<? extends Registry<?>> key, Class<?> token) {
        return new RegistryInfo(key, token);
    }

    /** 对象 id 归属本注册表的完整键（诊断用）。 */
    public Identifier objectId(Identifier id) {
        return id;
    }
}
