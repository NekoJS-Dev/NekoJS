package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.world.storage.loot.LootTable;

/**
 * 统一的 loot table 加载事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.name}（{@code String}，如 {@code "minecraft:entities/creeper"}）
 * 与 {@code event.lootTable}（{@code LootTable}）在 NeoForge（21.1/26.x）与
 * Cleanroom 上一致可用。
 *
 * <p>跨平台可移植性说明：
 * <ul>
 *   <li>{@code name} 统一为 {@code String}：Cleanroom 与 NeoForge 21.1 的原生类型为
 *       {@code ResourceLocation}，NeoForge 26.x 为 {@code Identifier}。统一取
 *       {@code .toString()}。</li>
 * </ul>
 */
@Getter
public class LootTableLoadEventJS {
    private final String name;
    private final LootTable lootTable;

    public LootTableLoadEventJS(String name, LootTable lootTable) {
        this.name = name;
        this.lootTable = lootTable;
    }
}
