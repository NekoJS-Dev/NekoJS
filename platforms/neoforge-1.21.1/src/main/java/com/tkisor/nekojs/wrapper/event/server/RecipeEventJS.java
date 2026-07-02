package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;
import com.tkisor.nekojs.api.recipe.RecipeCreationContext;
import com.tkisor.nekojs.api.recipe.RecipeEntryJS;
import com.tkisor.nekojs.api.recipe.RecipeFilter;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldRole;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.wrapper.RecipeRegistryProxy;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@CalledByDynamicCode
public class RecipeEventJS implements RecipeLifecycleContext {

    private final RecipeRegistryProxy recipesProxy;
    private final Map<ResourceLocation, JsonElement> jsons;
    private final Map<ResourceLocation, RecipeCreationContext> contexts = new HashMap<>();
    private final HolderLookup.Provider registries;
    private final RecipeTypeDefinitionRegistry recipeTypeDefinitions;
    private int recipeCounter = 0;

    private final Set<ResourceLocation> takenIds = new HashSet<>();

    public RecipeEventJS(Map<ResourceLocation, JsonElement> originalJsons, HolderLookup.Provider registries) {
        this(originalJsons, registries, RecipeTypeDefinitionRegistry.EMPTY);
    }

    public RecipeEventJS(Map<ResourceLocation, JsonElement> originalJsons, HolderLookup.Provider registries, RecipeTypeDefinitionRegistry recipeTypeDefinitions) {
        this.jsons = new HashMap<>(originalJsons);
        this.registries = registries;
        this.recipeTypeDefinitions = recipeTypeDefinitions == null ? RecipeTypeDefinitionRegistry.EMPTY : recipeTypeDefinitions;
        this.recipesProxy = new RecipeRegistryProxy(this);
    }

    public Map<ResourceLocation, JsonElement> getFinalJsons() { return this.jsons; }

    public RecipeTypeDefinitionRegistry getRecipeTypeDefinitions() { return this.recipeTypeDefinitions; }

    @Override
    public Set<String> ids() {
        return jsons.keySet().stream().map(ResourceLocation::toString).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public String getJson(String id) {
        ResourceLocation parsedId = RecipeJsonBuilder.parseId(id);
        if (parsedId == null) return null;
        JsonElement json = jsons.get(parsedId);
        return json == null ? null : json.toString();
    }

    @Override
    public void setJson(String id, String json) {
        ResourceLocation parsedId = RecipeJsonBuilder.parseId(id);
        if (parsedId == null) {
            throw new IllegalArgumentException("Invalid recipe id: " + id);
        }
        jsons.put(parsedId, JsonParser.parseString(json));
        contexts.remove(parsedId);
    }

    @Override
    public void removeById(String id) {
        ResourceLocation parsedId = RecipeJsonBuilder.parseId(id);
        if (parsedId == null) return;
        jsons.remove(parsedId);
        contexts.remove(parsedId);
    }

    public void setRecipeContext(ResourceLocation id, RecipeCreationContext context) {
        if (id != null && context != null) contexts.put(id, context);
    }

    public RecipeCreationContext getRecipeContext(ResourceLocation id) {
        return contexts.get(id);
    }

    public void removeRecipeContext(ResourceLocation id) {
        contexts.remove(id);
    }

    public String describeRecipe(ResourceLocation id, JsonObject json) {
        RecipeCreationContext context = contexts.get(id);
        if (context != null) return context.describe(id.toString());
        String type = json != null && json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "unknown";
        return "id=" + id + ", type=" + type + ", api=datapack, prefix=existing";
    }

    public JsonElement serializeResult(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow(JsonParseException::new);
    }

    public JsonElement serializeIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return new JsonArray();
        return Ingredient.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), ingredient).getOrThrow(JsonParseException::new);
    }

    public JsonElement serializeFluidStack(FluidStack stack) {
        return FluidStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow(JsonParseException::new);
    }

    public JsonElement serializeFluidIngredient(FluidIngredient ingredient) {
        return FluidIngredient.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), ingredient).getOrThrow(JsonParseException::new);
    }

    public JsonElement serializeSizedFluidIngredient(SizedFluidIngredient ingredient) {
        return SizedFluidIngredient.FLAT_CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), ingredient).getOrThrow(JsonParseException::new);
    }

    public ResourceLocation generateRecipeId(String prefix) {
        String baseString = prefix + "_" + (recipeCounter++);

        String randomSuffix = UUID.nameUUIDFromBytes(baseString.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 8);

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("nekojs", prefix + "_" + randomSuffix);
        int attempt = 1;
        while (!takenIds.add(id)) {
            id = ResourceLocation.fromNamespaceAndPath("nekojs", prefix + "_" + randomSuffix + "_" + (attempt++));
        }
        return id;
    }

    public void replaceInput(RecipeFilter filter, Ingredient match, Ingredient replacement) {
        if (match == null || replacement == null) return;
        JsonElement replacementJson = serializeIngredient(replacement);
        int replaced = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject jsonObj = entry.getValue().getAsJsonObject();
            if (filter != null && !passFilter(entry.getKey(), jsonObj, filter)) continue;
            Set<String> inputKeys = inputKeysFor(jsonObj);
            if (replaceInputInJson(jsonObj, match, replacementJson, false, inputKeys)) replaced++;
        }
        NekoJS.LOGGER.debug("Successfully intercepted JSON tree and replaced input ingredients in {} recipes", replaced);
    }

    private boolean replaceInputInJson(JsonElement element, Ingredient match, JsonElement replacementJson, boolean inputContext, Set<String> inputKeys) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            boolean modified = false;
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (inputContext && testSingleIngredient(child, match)) {
                    array.set(i, replacementJson.deepCopy());
                    modified = true;
                } else if (replaceInputInJson(child, match, replacementJson, inputContext, inputKeys)) {
                    modified = true;
                }
            }
            return modified;
        }
        if (!element.isJsonObject()) return false;

        boolean modified = false;
        JsonObject object = element.getAsJsonObject();
        for (String key : new ArrayList<>(object.keySet())) {
            JsonElement child = object.get(key);
            boolean childInputContext = inputContext || inputKeys.contains(key);
            if (childInputContext && testIngredientNode(child, match)) {
                object.add(key, replacementJson.deepCopy());
                modified = true;
            } else if (replaceInputInJson(child, match, replacementJson, childInputContext, inputKeys)) {
                modified = true;
            }
        }
        return modified;
    }

    private boolean testSingleIngredient(JsonElement node, Ingredient match) {
        try {
            Ingredient nodeIng = Ingredient.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), node).getOrThrow();
            return !nodeIng.isEmpty() && java.util.Arrays.stream(nodeIng.getItems()).anyMatch(h -> match.test(h));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testIngredientNode(JsonElement node, Ingredient match) {
        try {
            Ingredient nodeIng = Ingredient.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), node).getOrThrow();
            List<String> nodeItems = Arrays.stream(nodeIng.getItems()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList();
            List<String> matchItems = Arrays.stream(match.getItems()).map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList();
            // Partial match (B-档): non-empty intersection suffices — the size-equality guard
            // was the common "tag ingredient fails because it has more items than match" trap.
            // Replace strategy: callers in array-context pass through testSingleIngredient which
            // already does per-item anyMatch; here at the object level we keep the same
            // containsAll-as-subset semantics (intersection non-empty) to enable partial replace.
            return !nodeItems.isEmpty() && !matchItems.isEmpty() && nodeItems.stream().anyMatch(matchItems::contains);
        } catch (Exception e) {
            return false;
        }
    }

    public void replaceOutput(RecipeFilter filter, Ingredient match, ItemStack replacement) {
        if (match == null || replacement == null) return;
        JsonElement replacementJson = serializeResult(replacement);
        int replaced = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject jsonObj = entry.getValue().getAsJsonObject();
            if (filter != null && !passFilter(entry.getKey(), jsonObj, filter)) continue;
            Set<String> outputKeys = outputKeysFor(jsonObj);
            if (replaceOutputInJson(jsonObj, match, replacementJson, false, outputKeys)) replaced++;
        }
        NekoJS.LOGGER.debug("Successfully intercepted JSON tree and replaced outputs in {} recipes", replaced);
    }

    private boolean replaceOutputInJson(JsonElement element, Ingredient match, JsonElement replacementJson, boolean outputContext, Set<String> outputKeys) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            boolean modified = false;
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (outputContext && testOutputNode(child, match)) {
                    array.set(i, replacementJson.deepCopy());
                    modified = true;
                } else if (replaceOutputInJson(child, match, replacementJson, outputContext, outputKeys)) {
                    modified = true;
                }
            }
            return modified;
        }
        if (!element.isJsonObject()) return false;

        boolean modified = false;
        JsonObject object = element.getAsJsonObject();
        for (String key : new ArrayList<>(object.keySet())) {
            JsonElement child = object.get(key);
            boolean childOutputContext = outputContext || outputKeys.contains(key);
            if (childOutputContext && testOutputNode(child, match)) {
                object.add(key, replacementJson.deepCopy());
                modified = true;
            } else if (replaceOutputInJson(child, match, replacementJson, childOutputContext, outputKeys)) {
                modified = true;
            }
        }
        return modified;
    }

    private boolean testOutputNode(JsonElement node, Ingredient match) {
        try {
            ItemStack outputStack = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), node).getOrThrow();
            return !outputStack.isEmpty() && match.test(outputStack);
        } catch (Exception e) {
            if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isString()) {
                ResourceLocation id = ResourceLocation.tryParse(node.getAsString());
                return id != null && testOutputId(id, match);
            }
            return false;
        }
    }

    private boolean testOutputId(ResourceLocation id, Ingredient match) {
        return Arrays.stream(match.getItems()).anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id));
    }

    /**
     * Names that mark an input slot for the given recipe: the conservative hardcoded fallback
     * (classic ingredient slots) plus any schema field tagged role=INPUT for this recipe type
     * (e.g. smithing template/base/addition, shaped pattern/key) — so schema-known recipes get
     * broader, schema-accurate input recognition while schema-less fallback recipes keep prior
     * behaviour.
     */
    private Set<String> inputKeysFor(JsonObject json) {
        Set<String> keys = new HashSet<>();
        keys.add("ingredient");
        keys.add("ingredients");
        keys.add("input");
        keys.add("inputs");
        keys.add("key");
        RecipeTypeDefinition def = definitionOf(json);
        if (def != null) {
            for (RecipeFieldDefinition field : def.fields().values()) {
                if (field.role() == RecipeFieldRole.INPUT) keys.add(field.name());
            }
        }
        return keys;
    }

    private Set<String> outputKeysFor(JsonObject json) {
        Set<String> keys = new HashSet<>();
        keys.add("result");
        keys.add("results");
        keys.add("output");
        keys.add("outputs");
        RecipeTypeDefinition def = definitionOf(json);
        if (def != null) {
            for (RecipeFieldDefinition field : def.fields().values()) {
                if (field.role() == RecipeFieldRole.OUTPUT) keys.add(field.name());
            }
        }
        return keys;
    }

    private RecipeTypeDefinition definitionOf(JsonObject json) {
        if (json == null || !json.has("type") || !json.get("type").isJsonPrimitive()) return null;
        String type = json.get("type").getAsString();
        int colon = type.indexOf(':');
        if (colon <= 0) return null;
        return recipeTypeDefinitions.get(type.substring(0, colon), type.substring(colon + 1));
    }

    public void remove(RecipeFilter filter) {
        if (filter == null) return;
        int before = jsons.size();
        jsons.entrySet().removeIf(entry -> {
            if (!entry.getValue().isJsonObject()) return false;
            boolean remove = passFilter(entry.getKey(), entry.getValue().getAsJsonObject(), filter);
            if (remove) contexts.remove(entry.getKey());
            return remove;
        });
        NekoJS.LOGGER.debug("Removed {} recipes matching the filter", before - jsons.size());
    }

    public String dump(RecipeFilter filter) {
        List<RecipeEntryJS> recipes = filter == null ? all() : find(filter);
        JsonObject dump = new JsonObject();
        for (RecipeEntryJS recipe : recipes) {
            dump.add(recipe.id(), recipe.json().deepCopy());
        }
        return dump.toString();
    }

    public String dump() {
        return dump(null);
    }

    public void print(RecipeFilter filter) {
        List<RecipeEntryJS> recipes = filter == null ? all() : find(filter);
        for (RecipeEntryJS recipe : recipes) {
            NekoJS.LOGGER.info("Recipe dump {}: {}", recipe.id(), recipe.json());
        }
    }

    public void print() {
        print(null);
    }

    public List<RecipeEntryJS> all() {
        List<RecipeEntryJS> recipes = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                recipes.add(new RecipeEntryJS(this, entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
        return recipes;
    }

    public RecipeEntryJS get(String id) {
        ResourceLocation parsedId = RecipeJsonBuilder.parseId(id);
        if (parsedId == null) return null;
        JsonElement json = jsons.get(parsedId);
        if (json == null || !json.isJsonObject()) return null;
        return new RecipeEntryJS(this, parsedId, json.getAsJsonObject());
    }

    public int count() {
        return jsons.size();
    }

    public int count(RecipeFilter filter) {
        return find(filter).size();
    }

    public boolean exists(String id) {
        ResourceLocation parsedId = RecipeJsonBuilder.parseId(id);
        return parsedId != null && jsons.containsKey(parsedId);
    }

    public boolean exists(RecipeFilter filter) {
        return !find(filter).isEmpty();
    }

    public List<RecipeEntryJS> find(RecipeFilter filter) {
        List<RecipeEntryJS> recipes = new ArrayList<>();
        if (filter == null) return recipes;
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsons.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject json = entry.getValue().getAsJsonObject();
            if (passFilter(entry.getKey(), json, filter)) {
                recipes.add(new RecipeEntryJS(this, entry.getKey(), json));
            }
        }
        return recipes;
    }

    public void forEach(RecipeFilter filter, Consumer<RecipeEntryJS> callback) {
        for (RecipeEntryJS recipe : find(filter)) {
            callback.accept(recipe);
        }
    }

    public void modify(RecipeFilter filter, Consumer<RecipeEntryJS> callback) {
        forEach(filter, callback);
    }

    public void forEach(Consumer<RecipeEntryJS> callback) {
        for (RecipeEntryJS recipe : all()) {
            callback.accept(recipe);
        }
    }

    private boolean passFilter(ResourceLocation id, JsonObject jsonObj, RecipeFilter filter) {
        try {
            Recipe<?> tempRecipe = Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), jsonObj).getOrThrow();
            return filter.test(new RecipeHolder<>(id, tempRecipe), registries);
        } catch (Exception e) {
            return false;
        }
    }

    public void validateRecipe(ResourceLocation id, JsonObject json) {
        try {
            Recipe.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), json).getOrThrow(JsonParseException::new);
        } catch (Exception e) {
            throw new IllegalArgumentException(formatRecipeError("Invalid recipe", id, json, e), e);
        }
    }

    public String formatRecipeError(String prefix, ResourceLocation id, JsonObject json, Exception e) {
        return prefix + " (" + describeRecipe(id, json) + "): " + e.getMessage() + "\nJSON: " + json;
    }

    public RecipeJsonBuilder custom(String type, RecipeJsonValue value) {
        JsonObject recipeJson = requireJsonObject(value, "Custom recipe JSON");
        recipeJson.addProperty("type", type);
        return custom(recipeJson);
    }

    public RecipeJsonBuilder custom(RecipeJsonValue value) {
        return custom(requireJsonObject(value, "Custom recipe JSON"));
    }

    public RecipeJsonBuilder custom(JsonObject recipeJson) {
        if (recipeJson == null || !recipeJson.has("type") || !recipeJson.get("type").isJsonPrimitive()) {
            NekoJS.LOGGER.debug("Failed to register custom recipe: missing required 'type' field!");
            return null;
        }
        return new RecipeJsonBuilder(this, recipeJson, "custom");
    }

    public RecipeJsonBuilder shaped(ItemStack result, List<String> pattern, Map<String, Ingredient> keys) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        JsonArray patternArray = new JsonArray();
        for (String row : pattern) patternArray.add(row);
        json.add("pattern", patternArray);
        JsonObject keyObj = new JsonObject();
        for (Map.Entry<String, Ingredient> entry : keys.entrySet()) {
            keyObj.add(entry.getKey(), serializeIngredient(entry.getValue()));
        }
        json.add("key", keyObj);
        json.add("result", serializeResult(result));
        return new RecipeJsonBuilder(this, json, "shaped");
    }

    private JsonObject requireJsonObject(RecipeJsonValue value, String name) {
        JsonElement json = RecipeJsonValueConverter.toJson(this, value);
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return json.getAsJsonObject();
    }

    public RecipeJsonBuilder shapeless(ItemStack result, List<Ingredient> ingredients) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        JsonArray ingredientsArray = new JsonArray();
        if (ingredients != null) for (Ingredient ing : ingredients) ingredientsArray.add(serializeIngredient(ing));
        json.add("ingredients", ingredientsArray);
        json.add("result", serializeResult(result));
        return new RecipeJsonBuilder(this, json, "shapeless");
    }

    private RecipeJsonBuilder createCookingRecipe(String type, ItemStack result, Ingredient ingredient, float xp, int cookTime, String prefix) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("ingredient", serializeIngredient(ingredient));
        json.add("result", serializeResult(result));
        json.addProperty("experience", xp);
        json.addProperty("cookingtime", cookTime);
        return new RecipeJsonBuilder(this, json, prefix);
    }

    public RecipeJsonBuilder smelting(ItemStack result, Ingredient ingredient) {
        return createCookingRecipe("minecraft:smelting", result, ingredient, 0.1f, 200, "smelting");
    }

    public RecipeJsonBuilder smelting(ItemStack result, Ingredient ingredient, float xp, int cookTime) {
        return createCookingRecipe("minecraft:smelting", result, ingredient, xp, cookTime, "smelting");
    }

    public RecipeJsonBuilder blasting(ItemStack result, Ingredient ingredient) {
        return createCookingRecipe("minecraft:blasting", result, ingredient, 0.1f, 100, "blasting");
    }

    public RecipeJsonBuilder blasting(ItemStack result, Ingredient ingredient, float xp, int cookTime) {
        return createCookingRecipe("minecraft:blasting", result, ingredient, xp, cookTime, "blasting");
    }

    public RecipeJsonBuilder smoking(ItemStack result, Ingredient ingredient) {
        return createCookingRecipe("minecraft:smoking", result, ingredient, 0.1f, 100, "smoking");
    }

    public RecipeJsonBuilder campfireCooking(ItemStack result, Ingredient ingredient) {
        return createCookingRecipe("minecraft:campfire_cooking", result, ingredient, 0.1f, 600, "campfire");
    }

    public static String getRecipeOutputId(Recipe<?> recipe, HolderLookup.Provider registries) {
        if (recipe instanceof ShapedRecipe shaped) return BuiltInRegistries.ITEM.getKey(shaped.getResultItem(registries).getItem()).toString();
        if (recipe instanceof ShapelessRecipe shapeless) return BuiltInRegistries.ITEM.getKey(shapeless.getResultItem(registries).getItem()).toString();
        if (recipe instanceof SingleItemRecipe single) return BuiltInRegistries.ITEM.getKey(single.getResultItem(registries).getItem()).toString();
        if (recipe instanceof AbstractCookingRecipe cooking) return BuiltInRegistries.ITEM.getKey(cooking.getResultItem(registries).getItem()).toString();
        return null;
    }

    public static List<Ingredient> getIngredients(Recipe<?> recipe) {
        // 1.21.1 原版的 Recipe 接口本身就有 getIngredients 方法，所有配方均适用。
        return recipe.getIngredients();
    }

    public RecipeRegistryProxy getRecipes() {
        return this.recipesProxy;
    }

    public RecipeJsonBuilder builder(String type) {
        String prefix = type.contains(":") ? type.split(":")[1] : "custom";
        return new RecipeJsonBuilder(this, type, prefix);
    }
}