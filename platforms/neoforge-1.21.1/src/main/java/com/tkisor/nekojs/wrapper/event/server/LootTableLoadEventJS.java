package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * 统一的 loot table 加载事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.name}（{@code String}，如 {@code "minecraft:entities/creeper"}）
 * 与 {@code event.lootTable}（{@code LootTable}）在 NeoForge（21.1/26.x）与
 * Cleanroom 上一致可用。
 *
 * <p>跨平台可移植性说明：
 * <ul>
 *   <li>{@code name} 统一为 {@code String}：NeoForge 26.x 的原生类型为 {@code Identifier}，
 *       21.1 为 {@code ResourceLocation}，Cleanroom 为 {@code ResourceLocation}。
 *       统一取 {@code .toString()}。</li>
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
