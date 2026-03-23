package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.wrapper.RecipeRegistryProxy;
import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.stream.Collectors;

public class RecipeEventJS {

    private final RecipeRegistryProxy recipesProxy;
    private final Map<Identifier, JsonElement> jsons;
    private final HolderLookup.Provider registries;
    private int recipeCounter = 0;

    public RecipeEventJS(Map<Identifier, JsonElement> originalJsons, HolderLookup.Provider registries) {
        this.jsons = new HashMap<>(originalJsons);
        this.registries = registries;
        this.recipesProxy = new RecipeRegistryProxy(this);
    }

    public Map<Identifier, JsonElement> getFinalJsons() {
        return this.jsons;
    }

    public JsonElement serializeResult(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow(JsonParseException::new);
    }

    public JsonElement serializeIngredient(Ingredient ingredient) {
        return Ingredient.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), ingredient).getOrThrow(JsonParseException::new);
    }

    private void register(JsonObject json, String prefix) {
        Identifier loc = Identifier.fromNamespaceAndPath("nekojs", prefix + "_" + (recipeCounter++));
        jsons.put(loc, json);
    }

    public void replaceInput(RecipeFilter filter, Ingredient match, Ingredient replacement) {
        if (match == null || replacement == null) return;
        JsonElement replacementJson = serializeIngredient(replacement);
        int replaced = 0;

        for (Map.Entry<Identifier, JsonElement> entry : jsons.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject jsonObj = entry.getValue().getAsJsonObject();

            if (filter != null && !passFilter(entry.getKey(), jsonObj, filter)) continue;

            if (replaceInputInJson(jsonObj, match, replacementJson)) {
                replaced++;
            }
        }
        NekoJS.LOGGER.info("[NekoJS] 成功拦截 JSON 树并替换了 {} 个配方的输入材料", replaced);
    }

    private boolean replaceInputInJson(JsonObject recipeJson, Ingredient match, JsonElement replacementJson) {
        boolean modified = false;

        if (recipeJson.has("ingredient") && testIngredientNode(recipeJson.get("ingredient"), match)) {
            recipeJson.add("ingredient", replacementJson);
            modified = true;
        }
        if (recipeJson.has("ingredients")) {
            JsonArray arr = recipeJson.getAsJsonArray("ingredients");
            for (int i = 0; i < arr.size(); i++) {
                if (testIngredientNode(arr.get(i), match)) {
                    arr.set(i, replacementJson);
                    modified = true;
                }
            }
        }
        if (recipeJson.has("key")) {
            JsonObject keyObj = recipeJson.getAsJsonObject("key");
            for (String k : keyObj.keySet()) {
                if (testIngredientNode(keyObj.get(k), match)) {
                    keyObj.add(k, replacementJson);
                    modified = true;
                }
            }
        }
        return modified;
    }

    public void custom(JsonObject recipeJson) {
        if (recipeJson == null) {
            NekoJS.LOGGER.error("[NekoJS] custom 配方注册失败：传入的参数不是有效的对象！");
            return;
        }

        if (!recipeJson.has("type") || !recipeJson.get("type").isJsonPrimitive()) {
            NekoJS.LOGGER.error("[NekoJS] custom 配方注册失败：缺少必要的 'type' 字段！");
            return;
        }

        try {
            register(recipeJson, "custom");
        } catch (Exception e) {
            NekoJS.LOGGER.error("[NekoJS] custom 配方解析失败: ", e);
        }
    }

    private boolean testIngredientNode(JsonElement node, Ingredient match) {
        try {
            Ingredient nodeIng = Ingredient.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), node).getOrThrow();
            List<String> nodeItems = nodeIng.items().map(h -> BuiltInRegistries.ITEM.getKey(h.value()).toString()).toList();
            List<String> matchItems = match.items().map(h -> BuiltInRegistries.ITEM.getKey(h.value()).toString()).toList();
            return !nodeItems.isEmpty() && nodeItems.size() == matchItems.size() && nodeItems.containsAll(matchItems);
        } catch (Exception e) {
            return false;
        }
    }

    public void replaceOutput(RecipeFilter filter, Ingredient match, ItemStack replacement) {
        if (match == null || replacement == null) return;
        JsonElement replacementJson = serializeResult(replacement);
        int replaced = 0;

        for (Map.Entry<Identifier, JsonElement> entry : jsons.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject jsonObj = entry.getValue().getAsJsonObject();

            if (filter != null && !passFilter(entry.getKey(), jsonObj, filter)) continue;

            if (jsonObj.has("result")) {
                JsonElement resultNode = jsonObj.get("result");
                try {
                    ItemStack outputStack = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), resultNode).getOrThrow();
                    if (!outputStack.isEmpty() && match.test(outputStack)) {
                        jsonObj.add("result", replacementJson);
                        replaced++;
                    }
                } catch (Exception e) {
                    if (resultNode.isJsonPrimitive() && resultNode.getAsJsonPrimitive().isString()) {
                        Identifier loc = Identifier.tryParse(resultNode.getAsString());
                        if (loc != null) {
                            Item item = BuiltInRegistries.ITEM.getValue(loc);
                            if (item != null && item != Items.AIR) {
                                if (match.test(new ItemStack(item))) {
                                    jsonObj.add("result", replacementJson);
                                    replaced++;
                                }
                            }
                        }
                    }
                }
            }
        }
        NekoJS.LOGGER.info("[NekoJS] 成功拦截 JSON 树并篡改了 {} 个配方的输出产物", replaced);
    }

    public void remove(RecipeFilter filter) {
        if (filter == null) return;
        int before = jsons.size();
        jsons.entrySet().removeIf(entry -> {
            if (!entry.getValue().isJsonObject()) return false;
            return passFilter(entry.getKey(), entry.getValue().getAsJsonObject(), filter);
        });
        NekoJS.LOGGER.info("[NekoJS] 过滤器匹配并移除了 {} 个配方", before - jsons.size());
    }

    private boolean passFilter(Identifier id, JsonObject jsonObj, RecipeFilter filter) {
        try {
            Recipe<?> tempRecipe = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), jsonObj).getOrThrow();

            ResourceKey<Recipe<?>> recipeKey = ResourceKey.create(Registries.RECIPE, id);

            return filter.test(new RecipeHolder<>(recipeKey, tempRecipe), registries);
        } catch (Exception e) {
            return false;
        }
    }

    public void shaped(ItemStack result, Value pattern, Value keys) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        JsonArray patternArray = new JsonArray();
        for (int i = 0; i < pattern.getArraySize(); i++) patternArray.add(pattern.getArrayElement(i).asString());
        json.add("pattern", patternArray);
        JsonObject keyObj = new JsonObject();
        for (String key : keys.getMemberKeys())
            keyObj.add(key, serializeIngredient(keys.getMember(key).as(Ingredient.class)));
        json.add("key", keyObj);
        json.add("result", serializeResult(result));
        register(json, "shaped");
    }

    public void shapeless(ItemStack result, List<Ingredient> ingredients) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        JsonArray ingredientsArray = new JsonArray();
        if (ingredients != null) for (Ingredient ing : ingredients) ingredientsArray.add(serializeIngredient(ing));
        json.add("ingredients", ingredientsArray);
        json.add("result", serializeResult(result));
        register(json, "shapeless");
    }

    private void createCookingRecipe(String type, ItemStack result, Ingredient ingredient, float xp, int cookTime, String prefix) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("ingredient", serializeIngredient(ingredient));
        json.add("result", serializeResult(result));
        json.addProperty("experience", xp);
        json.addProperty("cookingtime", cookTime);
        register(json, prefix);
    }

    public void smelting(ItemStack result, Ingredient ingredient) {
        createCookingRecipe("minecraft:smelting", result, ingredient, 0.1f, 200, "smelting");
    }

    public void smelting(ItemStack result, Ingredient ingredient, float xp, int cookTime) {
        createCookingRecipe("minecraft:smelting", result, ingredient, xp, cookTime, "smelting");
    }

    public void blasting(ItemStack result, Ingredient ingredient) {
        createCookingRecipe("minecraft:blasting", result, ingredient, 0.1f, 100, "blasting");
    }

    public void blasting(ItemStack result, Ingredient ingredient, float xp, int cookTime) {
        createCookingRecipe("minecraft:blasting", result, ingredient, xp, cookTime, "blasting");
    }

    public void smoking(ItemStack result, Ingredient ingredient) {
        createCookingRecipe("minecraft:smoking", result, ingredient, 0.1f, 100, "smoking");
    }

    public void campfireCooking(ItemStack result, Ingredient ingredient) {
        createCookingRecipe("minecraft:campfire_cooking", result, ingredient, 0.1f, 600, "campfire");
    }

    public static String getRecipeOutputId(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) return getIdFromTemplate(shaped.result);
        if (recipe instanceof ShapelessRecipe shapeless) return getIdFromTemplate(shapeless.result);
        if (recipe instanceof SingleItemRecipe single) return getIdFromTemplate(single.result());
        if (recipe instanceof AbstractCookingRecipe cooking) return getIdFromTemplate(cooking.result);
        return null;
    }

    private static String getIdFromTemplate(ItemStackTemplate template) {
        return BuiltInRegistries.ITEM.getKey(template.item().value()).toString();
    }

    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped)
            return shaped.getIngredients().stream().flatMap(Optional::stream).collect(Collectors.toList());
        if (recipe instanceof ShapelessRecipe shapeless) return shapeless.ingredients;
        if (recipe instanceof SingleItemRecipe single) return List.of(single.input());
        if (recipe instanceof AbstractCookingRecipe cooking) return List.of(cooking.input());
        return List.of();
    }

    public RecipeRegistryProxy getRecipes() {
        return this.recipesProxy;
    }

    public RecipeJsonBuilder builder(String type) {
        return new RecipeJsonBuilder(this, type);
    }
}