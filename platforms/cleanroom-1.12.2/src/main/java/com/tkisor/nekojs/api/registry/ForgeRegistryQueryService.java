package com.tkisor.nekojs.api.registry;

import com.tkisor.nekojs.api.registry.RegistryQueryService;
import com.tkisor.nekojs.NekoJS;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Forge 1.12.2 (Cleanroom) 的只读注册表查询实现。
 *
 * <p>1.12.2 的注册表 id 是复数形式（{@code minecraft:items} 而非 {@code minecraft:item}），
 * 因此 {@code normalizeRegistryId} 提供常见单复数别名映射；tag 语义使用 OreDictionary
 * （1.12.2 无现代 TagKey），仅对 item/block 注册表有意义。
 */
public final class ForgeRegistryQueryService implements RegistryQueryService {
    public static final ForgeRegistryQueryService INSTANCE = new ForgeRegistryQueryService();

    // 触发 ForgeRegistries/GameData 初始化，保证 vanilla 注册表已进入 RegistryManager.ACTIVE。
    @SuppressWarnings("unused")
    private static final Object FORCE_INIT = ForgeRegistries.ITEMS;

    private ForgeRegistryQueryService() {
    }

    @Override
    public boolean hasRegistry(String registryId) {
        return RegistryManager.ACTIVE.getRegistry(normalizeRegistryId(registryId)) != null;
    }

    @Override
    public List<String> all(String registryId) {
        IForgeRegistry<?> registry = registry(registryId);
        if (registry == null) {
            return List.of();
        }
        return registry.getKeys().stream()
                .map(ResourceLocation::toString)
                .toList();
    }

    @Override
    public boolean has(String registryId, String id) {
        IForgeRegistry<?> registry = registry(registryId);
        if (registry == null) {
            return false;
        }
        ResourceLocation location = tryParse(id);
        return location != null && registry.containsKey(location);
    }

    @Override
    public List<String> tag(String registryId, String tagId) {
        // 1.12.2 无现代 TagKey；OreDictionary 是 tag 语义的等价物，仅对 item/block 有意义。
        ResourceLocation normalized = normalizeRegistryId(registryId);
        if (normalized == null) {
            return List.of();
        }
        boolean blockRegistry = "minecraft:blocks".equals(normalized.toString());
        if (!blockRegistry && !"minecraft:items".equals(normalized.toString())) {
            return List.of();
        }
        String oreName = tagId.startsWith("ore:") ? tagId.substring(4) : tagId;
        if (!OreDictionary.doesOreNameExist(oreName)) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : OreDictionary.getOres(oreName)) {
            if (stack == null || stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item == null) continue;
            if (blockRegistry) {
                // B8: 对 block 注册表，必须把 OreDictionary 的 ItemStack 映射回 Block 的 id，
                // 而不是返回 Item id。1.12.2 上 Item→Block 用 Block.getBlockFromItem；
                // 非 block 物品（如纯锭）会返回 Blocks.AIR，跳过并记 debug 日志。
                Block block = Block.getBlockFromItem(item);
                if (block == null || block == Blocks.AIR) {
                    NekoJS.LOGGER.debug("Skipping non-block OreDictionary entry '{}' for block registry tag '{}'",
                        item.getRegistryName(), oreName);
                    continue;
                }
                ResourceLocation blockId = block.getRegistryName();
                if (blockId != null) ids.add(blockId.toString());
            } else {
                ResourceLocation itemId = item.getRegistryName();
                if (itemId != null) ids.add(itemId.toString());
            }
        }
        return List.copyOf(ids);
    }

    @SuppressWarnings("unchecked")
    private static IForgeRegistry<?> registry(String registryId) {
        return RegistryManager.ACTIVE.getRegistry(normalizeRegistryId(registryId));
    }

    /**
     * 1.12.2 注册表 id 为复数（{@code minecraft:items}）。映射常见单数别名与
     * {@code RegistryInfos} 使用的显示 id（如 {@code item}）到真实注册表 key。
     */
    private static ResourceLocation normalizeRegistryId(String registryId) {
        if (registryId == null || registryId.isBlank()) {
            return null;
        }
        String raw = registryId;
        String path = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        String namespace = raw.contains(":") ? raw.substring(0, raw.indexOf(':')) : "minecraft";
        String plural = switch (path) {
            case "item" -> "items";
            case "block" -> "blocks";
            case "entity" -> "entities";
            case "sound_event" -> "soundevents";
            case "potion" -> "potions";
            case "enchantment" -> "enchantments";
            case "biome" -> "biomes";
            case "recipe" -> "recipes";
            case "villager_profession" -> "villagerprofessions";
            default -> path;
        };
        return new ResourceLocation(namespace, plural);
    }

    private static ResourceLocation tryParse(String value) {
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
