package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.core.NekoJSScriptManager;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.wrapper.item.IngredientJS;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

public final class IngredientAdapter implements JSTypeAdapter<Ingredient> {

    @Override
    public Class<Ingredient> getTargetClass() {
        return Ingredient.class;
    }

    @Override
    public boolean canConvert(Value value) {
        if (value.isNull() || value.isString() || value.hasArrayElements()) {
            return true;
        }
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            return obj instanceof IngredientJS || obj instanceof Ingredient || obj instanceof ItemStack;
        }
        return false;
    }

    @Override
    public Ingredient convert(Value value) {
        if (value.isNull()) {
            return null; // 配方系统通常用 null 表示“空槽位”
        }

        // 1. Java 对象快速通道
        if (value.isHostObject()) {
            Object obj = value.asHostObject();
            if (obj instanceof IngredientJS wrapper) return wrapper.unwrap();
            if (obj instanceof Ingredient ing) return ing;
            if (obj instanceof ItemStack stack) return Ingredient.of(stack.getItem());
        }

        try {
            // 2. 单一字符串（占比 95% 以上的最常见情况）
            if (value.isString()) {
                return resolveSingle(value.asString());
            }

            // 3. 数组混合模式 (例如 ["minecraft:apple", "#minecraft:logs"])
            if (value.hasArrayElements()) {
                List<Holder<Item>> holders = new ArrayList<>();
                for (long i = 0; i < value.getArraySize(); i++) {
                    Value elem = value.getArrayElement(i);
                    if (elem.isString()) {
                        resolveToHolders(elem.asString(), holders);
                    }
                }
                if (!holders.isEmpty()) {
                    return Ingredient.of(HolderSet.direct(holders));
                }
            }

        } catch (Exception e) {
            ScriptType type = NekoJSScriptManager.getTypeFromContext(value.getContext());
            type.logger().error("材料转换失败: {}", e.getMessage());
        }

        return fallback();
    }

    /**
     * 如果是 Tag，直接返回底层的 Named HolderSet
     */
    private Ingredient resolveSingle(String rawId) {
        if (rawId == null || rawId.trim().isEmpty()) return fallback();

        String id = formatId(rawId);
        Identifier loc = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
        if (loc == null) return fallback();

        if (id.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, loc);
            // 直接获取具名 (Named) HolderSet，不将其解包成静态列表。
            // 这样未来别的 Mod 往这个 Tag 里塞东西时，该 Ingredient 依然生效！
            var tagSet = BuiltInRegistries.ITEM.get(tagKey);
            return tagSet.map(Ingredient::of).orElseGet(this::fallback);
        } else {
            Item item = BuiltInRegistries.ITEM.getValue(loc);
            if (item != null && item != Items.AIR) {
                return Ingredient.of(item);
            }
        }
        return fallback();
    }

    /**
     * 数组情况下的备用解析：解包加入收集列表中
     */
    private void resolveToHolders(String rawId, List<Holder<Item>> out) {
        if (rawId == null || rawId.trim().isEmpty()) return;

        String id = formatId(rawId);
        Identifier loc = Identifier.tryParse(id.startsWith("#") ? id.substring(1) : id);
        if (loc == null) return;

        if (id.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, loc);
            BuiltInRegistries.ITEM.get(tagKey).ifPresent(holders -> {
                for (Holder<Item> h : holders) {
                    out.add(h);
                }
            });
        } else {
            Item item = BuiltInRegistries.ITEM.getValue(loc);
            if (item != null && item != Items.AIR) {
                out.add(item.builtInRegistryHolder());
            }
        }
    }

    private String formatId(String inputStr) {
        if (inputStr.indexOf(':') != -1) {
            return inputStr; // 已经包含命名空间，直接返回
        }
        // 如果没有冒号，自动补充 minecraft 命名空间
        return inputStr.startsWith("#") ? "#minecraft:" + inputStr.substring(1) : "minecraft:" + inputStr;
    }

    private Ingredient fallback() {
        return Ingredient.of(Items.BARRIER);
    }
}