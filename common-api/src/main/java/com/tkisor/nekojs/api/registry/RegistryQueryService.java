package com.tkisor.nekojs.api.registry;

import java.util.List;

/**
 * 只读的 Minecraft 注册表查询 SPI。
 *
 * <p>由各平台实现（NeoForge / Cleanroom），返回基础类型（id 字符串、布尔值），
 * 不暴露任何 Minecraft 原生类型。供 {@code Registry} 脚本绑定使用。
 */
public interface RegistryQueryService {
    /** 指定注册表是否存在（例如 {@code minecraft:item}）。 */
    boolean hasRegistry(String registryId);

    /** 注册表内所有条目 id（含命名空间），按注册表自身顺序。 */
    List<String> all(String registryId);

    /** 注册表内是否存在指定 id（例如 {@code minecraft:stone}）。 */
    boolean has(String registryId, String id);

    /** 指定 tag 下的所有条目 id（例如 tag {@code minecraft:planks}）。 */
    List<String> tag(String registryId, String tagId);

    /**
     * 指定注册表已注册的所有 data map 类型 id（例如 {@code neoforge:furnace_fuels}）。
     * 注册表未知或平台无 data map（如 1.12.2）时返回空列表。
     */
    default List<String> dataMapIds(String registryId) {
        return List.of();
    }

    /**
     * 读取指定注册表条目的 data map 值，序列化为 JSON 字符串。
     * 条目 / data map 类型 / 注册表任一不存在时返回 {@code null}。
     */
    default String dataMapValue(String registryId, String dataMapTypeId, String id) {
        return null;
    }
}
