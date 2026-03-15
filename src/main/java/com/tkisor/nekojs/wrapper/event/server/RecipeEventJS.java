package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.NekoEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RecipeEventJS implements NekoEvent {

    private final List<RecipeHolder<?>> holders;
    private final HolderLookup.Provider registries;
    private int recipeCounter = 0;

    public RecipeEventJS(Collection<RecipeHolder<?>> originalHolders, HolderLookup.Provider registries) {
        this.holders = new ArrayList<>(originalHolders);
        this.registries = registries;
    }

    public RecipeMap getFinalMap() {
        return RecipeMap.create(this.holders);
    }

    public void remove(Value filter) {
        String targetId = filter.hasMember("id") ? filter.getMember("id").asString() : null;
        String rawOutput = filter.hasMember("output") ? filter.getMember("output").asString() : null;
        String targetType = filter.hasMember("type") ? filter.getMember("type").asString() : null;

        final String targetOutput = (rawOutput != null && !rawOutput.contains(":")) ? "minecraft:" + rawOutput : rawOutput;

        holders.removeIf(holder -> {
            boolean match = true;

            if (targetId != null) {
                String keyStr = holder.id().toString();
                String actualId = keyStr.substring(keyStr.indexOf(" / ") + 3, keyStr.length() - 1);
                if (!actualId.equals(targetId)) match = false;
            }

            if (match && targetType != null) {
                String typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString();
                if (!typeId.equals(targetType)) match = false;
            }

            if (match && targetOutput != null) {
                String outId = getRecipeOutput(holder.value());
                if (outId == null || !outId.equals(targetOutput)) {
                    match = false;
                }
            }

//            if (match) {
//                NekoJS.LOGGER.info("[NekoJS 击杀] 成功在内存中抹除配方: {}", holder.id().toString());
//            }

            return match;
        });
    }

    private String getRecipeOutput(Recipe<?> recipe) {
        ItemStackTemplate template = null;

        if (recipe instanceof ShapedRecipe shaped) {
            template = shaped.result;
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            template = shapeless.result;
        } else if (recipe instanceof SingleItemRecipe singleItem) {
            template = singleItem.result();
        }

        if (template != null) {
            try {
                for (java.lang.reflect.Field field : template.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(template);

                    if (value instanceof net.minecraft.core.Holder<?> holder) {
                        Object heldObj = holder.value();
                        if (heldObj instanceof net.minecraft.world.item.Item item) {
                            return BuiltInRegistries.ITEM.getKey(item).toString();
                        }
                    }

                    if (value instanceof net.minecraft.world.item.Item item) {
                        return BuiltInRegistries.ITEM.getKey(item).toString();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public void shaped(String result, Value pattern, Value keys) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");

        JsonArray patternArray = new JsonArray();
        for (int i = 0; i < pattern.getArraySize(); i++) {
            patternArray.add(pattern.getArrayElement(i).asString());
        }
        json.add("pattern", patternArray);

        JsonObject keyObj = new JsonObject();
        for (String key : keys.getMemberKeys()) {
            keyObj.addProperty(key, keys.getMember(key).asString());
        }
        json.add("key", keyObj);

        json.add("result", parseResult(result));

        Identifier loc = Identifier.fromNamespaceAndPath("nekojs", "shaped_" + (recipeCounter++));
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, loc);

        try {
            Recipe<?> recipe = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);
            holders.add(new RecipeHolder<>(key, recipe));
        } catch (Exception e) {
            NekoJS.LOGGER.error("[NekoJS] 动态合成表构建失败: {}", e.getMessage());
        }
    }

    private JsonObject parseResult(String resultStr) {
        JsonObject resultObj = new JsonObject();
        int count = 1;
        String id = resultStr;
        if (resultStr.contains("x ")) {
            String[] parts = resultStr.split("x ");
            count = Integer.parseInt(parts[0].trim());
            id = parts[1].trim();
        }
        resultObj.addProperty("id", id);
        resultObj.addProperty("count", count);
        return resultObj;
    }
}