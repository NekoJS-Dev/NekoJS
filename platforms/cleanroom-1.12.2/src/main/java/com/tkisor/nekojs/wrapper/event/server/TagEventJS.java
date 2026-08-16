package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.12.2 标签修改事件（OreDictionary 适配）。
 *
 * <p>1.12.2 是 pre-1.13 时代，没有现代按注册表分类的 tag 系统，等价物是
 * {@link OreDictionary}（"等价物品组"语义）。本事件把脚本侧
 * {@code ServerEvents.tags('ore_dict', e => ...)} 形态的调用映射到 OreDictionary 运行时操作：
 * <ul>
 *   <li>{@link #add} → {@link OreDictionary#registerOre(String, ItemStack)} 追加物品进 ore 名</li>
 *   <li>{@link #remove} → 从 ore 名列表移除物品（运行时反射移除）</li>
 *   <li>{@link #replaceAll} → 先清空再写入新条目</li>
 * </ul>
 *
 * <p><b>语义边界（wiki 必须记录）</b>：与 NeoForge 的 {@code ServerEvents.tags} 不同，
 * 1.12.2 OreDictionary 仅对物品/方块有意义，没有按注册表分类（dispatch 键固定 {@code "ore_dict"}），
 * 且移除是运行时操作（非数据包声明）。脚本写跨平台 tag 操作时，1.12.2 是近似支持，不是真正等价物。
 *
 * <p>事件在 {@code serverAboutToStart}（SERVER 脚本加载后）触发一次，让脚本注册的 ore 名
 * 在配方注册前生效。dispatch 键固定 {@code "ore_dict"}——脚本以
 * {@code ServerEvents.tags('ore_dict', event => ...)} 监听。
 */
@Doc("Tag modification event adapted to the 1.12.2 OreDictionary.")
@Doc("Items and blocks only; dispatch key is fixed to 'ore_dict'; removals are runtime operations, not datapack declarations.")
public final class TagEventJS {

    /** 固定 dispatch 键：1.12.2 无按注册表分类的 tag。 */
    @Doc("The fixed dispatch key for this event on 1.12.2 (no per-registry tags); always 'ore_dict'.")
    public static final String REGISTRY_KEY = "ore_dict";

    private final Map<String, List<ItemStack>> additions = new LinkedHashMap<>();
    private final Map<String, List<ItemStack>> removals = new LinkedHashMap<>();
    private final Map<String, List<String>> replacements = new LinkedHashMap<>();

    /** dispatch 键（脚本监听用的 registry id 近似）。 */
    @Doc("The dispatch key scripts listen with; always 'ore_dict' on 1.12.2.")
    @Return("the fixed registry key string 'ore_dict'")
    public String getRegistry() {
        return REGISTRY_KEY;
    }

    /** 把物品 id（{@code minecraft:iron_ingot}）加进 ore 名（{@code forge:ingots/iron}）。 */
    @Doc("Stages an item id for addition to an ore name (applied on apply()).")
    @Param(name = "tag", value = "ore name like 'forge:ingots/iron', 'ore:ingots/iron', or 'ingots/iron'")
    @Param(name = "entry", value = "item id like 'minecraft:iron_ingot'; unknown ids are ignored")
    public void add(String tag, String entry) {
        ItemStack stack = resolveItemStack(entry);
        if (stack == null) return;
        additions.computeIfAbsent(tag, k -> new ArrayList<>()).add(stack);
    }

    /** 从 ore 名移除物品 id。 */
    @Doc("Stages an item id for removal from an ore name (applied on apply()).")
    @Param(name = "tag", value = "ore name like 'ingots/iron'")
    @Param(name = "entry", value = "item id like 'minecraft:iron_ingot'; unknown ids are ignored")
    public void remove(String tag, String entry) {
        ItemStack stack = resolveItemStack(entry);
        if (stack == null) return;
        removals.computeIfAbsent(tag, k -> new ArrayList<>()).add(stack);
    }

    /** 清空 ore 名的全部条目（延迟应用）。 */
    @Doc("Stages the removal of every entry from an ore name (applied on apply()).")
    @Param(name = "tag", value = "ore name like 'ingots/iron'")
    public void removeAll(String tag) {
        replacements.put(tag, new ArrayList<>());
    }

    /** 用新条目整体替换 ore 名的全部内容。 */
    @Doc("Stages the replacement of an ore name's whole content with new entries (applied on apply()).")
    @Param(name = "tag", value = "ore name like 'ingots/iron'")
    @Param(name = "entries", value = "item ids that form the new content")
    public void replaceAll(String tag, String... entries) {
        List<String> list = new ArrayList<>();
        for (String entry : entries) {
            list.add(entry);
        }
        replacements.put(tag, list);
    }

    /** 读取 ore 名当前的物品 id 列表。 */
    @Doc("Reads the item ids currently in an ore name.")
    @Param(name = "tag", value = "ore name like 'ingots/iron'")
    @Return("the current item ids; empty list if the ore name does not exist")
    public List<String> getEntries(String tag) {
        String oreName = stripOrePrefix(tag);
        if (!OreDictionary.doesOreNameExist(oreName)) return List.of();
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : OreDictionary.getOres(oreName)) {
            if (stack != null && !stack.isEmpty() && stack.getItem() != null) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (rl != null) ids.add(rl.toString());
            }
        }
        return List.copyOf(ids);
    }

    /** 把脚本侧记录的 add/remove/replaceAll 应用到 OreDictionary 运行时。 */
    @Doc("Applies all staged add/remove/replaceAll operations to the OreDictionary runtime.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
    public void apply() {
        // replaceAll/removeAll：先清空再写入（1.12.2 无清空 API，等价做法是逐个移除现有条目）
        for (var entry : replacements.entrySet()) {
            String tag = entry.getKey();
            String oreName = stripOrePrefix(tag);
            List<ItemStack> existing = OreDictionary.doesOreNameExist(oreName)
                    ? new ArrayList<>(OreDictionary.getOres(oreName))
                    : List.of();
            for (ItemStack stack : existing) {
                OreDictionary.getOres(oreName).remove(stack);
            }
            // 写入新条目（replaceAll 有内容时）
            for (String itemEntry : entry.getValue()) {
                ItemStack stack = resolveItemStack(itemEntry);
                if (stack != null) {
                    OreDictionary.registerOre(oreName, stack);
                }
            }
        }
        // add
        for (var entry : additions.entrySet()) {
            String oreName = stripOrePrefix(entry.getKey());
            for (ItemStack stack : entry.getValue()) {
                OreDictionary.registerOre(oreName, stack);
            }
        }
        // remove
        for (var entry : removals.entrySet()) {
            String oreName = stripOrePrefix(entry.getKey());
            if (!OreDictionary.doesOreNameExist(oreName)) continue;
            for (ItemStack toRemove : entry.getValue()) {
                OreDictionary.getOres(oreName).removeIf(existing ->
                        existing != null && existing.getItem() == toRemove.getItem()
                                && (toRemove.getItemDamage() == OreDictionary.WILDCARD_VALUE
                                        || existing.getItemDamage() == toRemove.getItemDamage()));
            }
        }
    }

    /** {@code "forge:ingots/iron"} / {@code "ore:ingots/iron"} / {@code "ingots/iron"} → {@code "ingots/iron"}。 */
    private static String stripOrePrefix(String tag) {
        if (tag == null || tag.isBlank()) return tag;
        if (tag.startsWith("ore:")) return tag.substring(4);
        // 1.12.2 ore 名通常不带命名空间；脚本若写 forge:xxx 也兼容
        int colon = tag.indexOf(':');
        // 保留含斜杠的路径形 ore 名（如 forge:ingots/iron → ingots/iron）
        if (colon > 0 && tag.substring(colon + 1).contains("/")) {
            return tag.substring(colon + 1);
        }
        return tag;
    }

    /** 把 {@code "minecraft:iron_ingot"} 解析为 {@link ItemStack}，失败返回 null。 */
    private static ItemStack resolveItemStack(String entry) {
        if (entry == null || entry.isBlank()) return null;
        ResourceLocation id = new ResourceLocation(entry);
        if (!ForgeRegistries.ITEMS.containsKey(id)) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? null : new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE);
    }
}
