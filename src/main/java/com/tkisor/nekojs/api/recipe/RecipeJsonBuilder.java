package com.tkisor.nekojs.api.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
//? if neoforge {
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
//?}
import java.util.Map;

/** Fluent builder for constructing or modifying recipe JSON payloads in {@link RecipeEventJS}. */
public class RecipeJsonBuilder implements RecipeBuilder {
    private final JsonObject json;
    private final RecipeEventJS event;
    private final RecipeCreationContext context;
    private Identifier currentId;

    public RecipeJsonBuilder(RecipeEventJS event, String type, String prefix) {
        this(event, type, prefix, RecipeCreationContext.of("event.builder", type, prefix, captureScriptId()));
    }

    public RecipeJsonBuilder(RecipeEventJS event, String type, String prefix, RecipeCreationContext context) {
        this.event = event;
        this.json = new JsonObject();
        this.json.addProperty("type", type);
        this.context = context;

        this.currentId = event.generateRecipeId(prefix);
        this.event.getFinalJsons().put(this.currentId, this.json);
        this.event.setRecipeContext(this.currentId, this.context);
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, String prefix) {
        this(event, prebuiltJson, prefix, RecipeCreationContext.of("event.custom", recipeType(prebuiltJson), prefix, captureScriptId()));
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, String prefix, RecipeCreationContext context) {
        this.event = event;
        this.json = prebuiltJson;
        this.context = context;

        this.currentId = event.generateRecipeId(prefix);
        this.event.getFinalJsons().put(this.currentId, this.json);
        this.event.setRecipeContext(this.currentId, this.context);
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, Identifier currentId) {
        this(event, prebuiltJson, currentId, event.getRecipeContext(currentId));
    }

    public RecipeJsonBuilder(RecipeEventJS event, JsonObject prebuiltJson, Identifier currentId, RecipeCreationContext context) {
        this.event = event;
        this.json = prebuiltJson;
        this.currentId = currentId;
        this.context = context != null ? context : RecipeCreationContext.of("existing", recipeType(prebuiltJson), "existing");
        this.event.setRecipeContext(this.currentId, this.context);
    }

    public static Identifier parseId(String id) {
        if (id == null) {
            return null;
        }
        if (id.contains(":")) {
            return Identifier.tryParse(id);
        }
        return Identifier.tryParse("nekojs:" + id);
    }

    public RecipeJsonBuilder id(String newId) {
        Identifier oldId = this.currentId;
        event.getFinalJsons().remove(oldId);
        event.removeRecipeContext(oldId);

        Identifier parsedId = parseId(newId);

        if (parsedId == null) {
            NekoJS.LOGGER.debug("Invalid recipe ID: {}", newId);
            parsedId = oldId;
        }

        this.currentId = parsedId;
//? if >=26 {
        IngredientActionRegistry.rekey(oldId, this.currentId);
//?}
        if (oldId != null && this.currentId != null) {
            InlineRecipeStageRegistry.current().rekey(
                    NekoId.resource(oldId.getNamespace(), oldId.getPath()),
                    NekoId.resource(this.currentId.getNamespace(), this.currentId.getPath()));
        }
        event.getFinalJsons().put(this.currentId, this.json);
        event.setRecipeContext(this.currentId, this.context);

        return this;
    }

    public RecipeJsonBuilder group(String group) {
        json.addProperty("group", group);
        return this;
    }

    public RecipeJsonBuilder validate() {
        event.validateRecipe(currentId, json);
        return this;
    }

    @com.tkisor.nekojs.api.annotation.HideFromJS
    public RecipeBuilderSession session() {
        return event.builderSession(() -> currentId, json);
    }

    public JsonObject json() {
        return json;
    }

    public RecipeJsonBuilder removeProperty(String key) {
        json.remove(key);
        return this;
    }

    public RecipeJsonBuilder setPath(String path, RecipeJsonValue value) {
        RecipeJsonPath.set(json, path, RecipeJsonValueConverter.toJson(event, value));
        return this;
    }

    public RecipeJsonBuilder setPaths(RecipeJsonValue values) {
        JsonElement element = RecipeJsonValueConverter.toJson(event, values);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Recipe JSON batch path values must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            RecipeJsonPath.set(json, entry.getKey(), entry.getValue());
        }
        return this;
    }

    public RecipeJsonBuilder removePath(String path) {
        RecipeJsonPath.remove(json, path);
        return this;
    }

    public RecipeJsonBuilder removePaths(RecipeJsonValue paths) {
        JsonElement element = RecipeJsonValueConverter.toJson(event, paths);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Recipe JSON batch remove paths must be an array");
        }
        for (JsonElement path : element.getAsJsonArray()) {
            removePath(path.getAsString());
        }
        return this;
    }

    public RecipeJsonBuilder merge(JsonObject value) {
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        return this;
    }

    public RecipeJsonBuilder merge(RecipeJsonValue value) {
        JsonElement element = RecipeJsonValueConverter.toJson(event, value);
        if (element.isJsonObject()) {
            return merge(element.getAsJsonObject());
        }
        return this;
    }

    public RecipeJsonBuilder input(String key, Ingredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeIngredient(ingredient));
        return this;
    }

    public RecipeJsonBuilder output(String key, ItemStack result) {
        if (result != null) json.add(key, event.serializeResult(result));
        return this;
    }

//? if neoforge {
    public RecipeJsonBuilder fluid(String key, FluidStack stack) {
        if (stack != null) json.add(key, event.serializeFluidStack(stack));
        return this;
    }

    public RecipeJsonBuilder fluidIngredient(String key, FluidIngredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeFluidIngredient(ingredient));
        return this;
    }

    public RecipeJsonBuilder sizedFluidIngredient(String key, SizedFluidIngredient ingredient) {
        if (ingredient != null) json.add(key, event.serializeSizedFluidIngredient(ingredient));
        return this;
    }
//?}

    public RecipeJsonBuilder property(String key, RecipeJsonValue value) {
        json.add(key, RecipeJsonValueConverter.toJson(event, value));
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, JsonElement value) {
        json.add(key, value);
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, Number value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, String value) {
        json.addProperty(key, value);
        return this;
    }

    public RecipeJsonBuilder jsonProperty(String key, Boolean value) {
        json.addProperty(key, value);
//? if >=26 {
        return this;
    }

    // ---- Ingredient actions: they apply to the crafting remainder and are pure script state, never
    // written to JSON. See IngredientActionRegistry ----

    /**
     * A matching crafting ingredient takes amount durability damage when consumed. Unbreakable
     * items count as kept; an item whose durability runs out disappears.
     * Example: {@code .damageIngredient('minecraft:iron_pickaxe', 1)}.
     */
    @com.tkisor.nekojs.api.annotation.Doc("Matched crafting ingredients come back with durability reduced by amount (unbreakable items are kept intact; broken ones vanish).")
    @com.tkisor.nekojs.api.annotation.Param(name = "ingredient", value = "item id, item array, tag like '#c:tools', or an Ingredient")
    @com.tkisor.nekojs.api.annotation.Param(name = "amount", value = "durability to subtract per craft; defaults to 1")
    public RecipeJsonBuilder damageIngredient(Ingredient ingredient, int amount) {
        IngredientActionRegistry.record(currentId,
                new IngredientActionRegistry.Action(IngredientActionRegistry.Kind.DAMAGE, ingredient, amount, ItemStack.EMPTY));
        return this;
    }

    /**
     * A matching crafting ingredient is kept as it is when consumed, using a copy that loses no
     * durability. Example: {@code .keepIngredient('#c:tools')}.
     */
    @com.tkisor.nekojs.api.annotation.Doc("Matched crafting ingredients are kept intact after crafting (a fresh copy stays in the grid).")
    @com.tkisor.nekojs.api.annotation.Param(name = "ingredient", value = "item id, item array, tag like '#c:tools', or an Ingredient")
    public RecipeJsonBuilder keepIngredient(Ingredient ingredient) {
        IngredientActionRegistry.record(currentId,
                new IngredientActionRegistry.Action(IngredientActionRegistry.Kind.KEEP, ingredient, 1, ItemStack.EMPTY));
        return this;
    }

    /**
     * A matching crafting ingredient is replaced with the given item when consumed.
     * Example: {@code .replaceIngredient('minecraft:diamond', 'minecraft:emerald')}.
     */
    @com.tkisor.nekojs.api.annotation.Doc("Matched crafting ingredients are replaced with the given item after crafting.")
    @com.tkisor.nekojs.api.annotation.Param(name = "ingredient", value = "item id, item array, tag like '#c:tools', or an Ingredient")
    @com.tkisor.nekojs.api.annotation.Param(name = "with", value = "replacement item id or ItemStack")
    public RecipeJsonBuilder replaceIngredient(Ingredient ingredient, ItemStack with) {
        IngredientActionRegistry.record(currentId,
                new IngredientActionRegistry.Action(IngredientActionRegistry.Kind.REPLACE, ingredient, 1,
                        with == null ? ItemStack.EMPTY : with));
//?}
        return this;
    }

    // ---- Inline recipe stage gates: pure in-memory script state, never written to recipe JSON ----

    /**
     * Gates this recipe behind the given player progression stage without mutating the recipe JSON.
     * Example: {@code event.shapeless('minecraft:stick', ['minecraft:oak_planks']).stage('chapter_1')}.
     */
    @Doc("Gates this recipe behind the given player progression stage without modifying the underlying recipe JSON.")
    @Param(name = "stage", value = "required non-blank stage name")
    @Return("this builder for fluent chaining")
    public RecipeJsonBuilder stage(String stage) {
        return stage(stage, "player");
    }

    /**
     * Gates this recipe behind the given progression stage and evaluation scope without mutating
     * the recipe JSON. Example: {@code builder.stage('nether_unlocked', 'server')}.
     */
    @Doc("Gates this recipe behind the given progression stage in the specified evaluation scope without modifying the underlying recipe JSON.")
    @Param(name = "stage", value = "required non-blank stage name")
    @Param(name = "scope", value = "stage evaluation scope ('player', 'server', 'either', or 'both')")
    @Return("this builder for fluent chaining")
    public RecipeJsonBuilder stage(String stage, String scope) {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4070] Recipe stage must not be blank");
        }
        StageScope parsedScope = StageScope.fromString(scope);
        if (parsedScope == null) {
            throw new IllegalArgumentException("[NEKO-4070] Unknown recipe stage scope: " + scope);
        }
        if (this.currentId == null) {
            throw new IllegalStateException("[NEKO-4070] Cannot assign recipe stage before a recipe ID is bound");
        }
        NekoId nekoId = NekoId.resource(this.currentId.getNamespace(), this.currentId.getPath());
        InlineRecipeStageRegistry.current().put(nekoId, stage.trim(), parsedScope);
        return this;
    }

    public RecipeCreationContext context() {
        return context;
    }

    private static String recipeType(JsonObject json) {
        if (json != null && json.has("type") && json.get("type").isJsonPrimitive()) {
            return json.get("type").getAsString();
        }
        return "unknown";
    }

    /**
     * Captures the current script id. While a script callback runs (EventBusJS has switched the
     * current script id), {@code Context.getCurrent()} returns the current Graal context. On a
     * non-script thread, such as a plugin calling this directly, it returns null.
     */
    private static String captureScriptId() {
        try {
            var context = graal.graalvm.polyglot.Context.getCurrent();
            if (context == null) return null;
            return com.tkisor.nekojs.script.ScriptContextRegistry.currentScriptIdOf(context);
        } catch (IllegalStateException e) {
            // Not inside a polyglot context
            return null;
        }
    }
}
