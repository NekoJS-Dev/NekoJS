package com.tkisor.nekojs.api.recipe;

/**
 * Loose value type for the Recipe API: accepts any JS value a script passes and converts it to
 * JSON.
 *
 * <h2>Usage from JavaScript</h2>
 * <p>Scripts <b>neither need nor should</b> construct {@code RecipeJsonValue} explicitly.
 * GraalVM adapts JS values to this type through {@code RecipeJsonValueAdapter} (registered as a
 * {@code JSTypeAdapter} with {@code getPrecedence() = LOWEST}):</p>
 *
 * <pre>
 * // JS object → RecipeJsonValue → JsonObject
 * event.custom('minecraft:crafting_shaped', {
 *     pattern: ['AAA', 'A A'],
 *     key: { A: Ingredient.of('minecraft:stone') },
 *     result: ItemJS.of('minecraft:stick', 4)
 * })
 *
 * // Embedded NekoJS wrappers are expanded recursively by RecipeJsonValueConverter and serialized:
 * //   IngredientJS    → Ingredient.CODEC  → JsonObject { item: "..." }
 * //   ItemStack       → ItemStack.CODEC   → JsonObject { id: "...", count: N }
 * //   FluidWrapper    → FluidStack.CODEC  → JsonObject { fluid: "...", amount: N }
 *
 * // RecipeJsonBuilder setPath/merge/property also go through this path:
 * builder.setPath('result.count', 4)                        // number → JsonPrimitive
 * builder.setPath('ingredients.0', Ingredient.of('minecraft:stone'))  // → serialized through the Codec
 * builder.merge({ group: 'nekojs_test' })                   // JS object → JsonObject
 * </pre>
 *
 * <h2>Overload matching in GraalVM</h2>
 * <p>Because {@code RecipeJsonValueAdapter.getPrecedence() = LOWEST}, overloads that take concrete
 * types such as {@code ItemStack} or {@code Ingredient} match first, and {@code RecipeJsonValue} is
 * the last fallback. It is only picked when the caller passes a raw JSON object:</p>
 *
 * <pre>
 * // matches crafting_shaped(ItemStack, List, Map) - preferred
 * handler.crafting_shaped(ItemJS.of('stick', 4), ['AA','AA'], {A: Ingredient.of('stone')})
 *
 * // matches crafting_shaped(RecipeJsonValue) - LOWEST, the fallback
 * handler.crafting_shaped({ result: {id:'stick'}, pattern:['AA'], key:{A:{item:'stone'}} })
 * </pre>
 */
public record RecipeJsonValue(Object value) {
}
