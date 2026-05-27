package com.tkisor.nekojs.api.recipe;

/**
 * Recipe API 的宽松类型边界 —— 接收脚本侧任意 JS 值，统一转为 JSON。
 *
 * <h2>JS 侧使用方式</h2>
 * <p>脚本中<b>不需要也不应该</b>显式构造 {@code RecipeJsonValue}。
 * GraalVM 通过 {@code RecipeJsonValueAdapter}（注册为 {@code JSTypeAdapter}，
 * {@code getPrecedence() = LOWEST}）自动将 JS 值适配为此类型：</p>
 *
 * <pre>
 * // JS object → RecipeJsonValue → JsonObject
 * event.custom('minecraft:crafting_shaped', {
 *     pattern: ['AAA', 'A A'],
 *     key: { A: Ingredient.of('minecraft:stone') },
 *     result: ItemJS.of('minecraft:stick', 4)
 * })
 *
 * // 内嵌 NekoJS wrapper 会被 RecipeJsonValueConverter 递归展开并序列化：
 * //   IngredientJS    → Ingredient.CODEC  → JsonObject { item: "..." }
 * //   ItemStack       → ItemStack.CODEC   → JsonObject { id: "...", count: N }
 * //   FluidWrapper    → FluidStack.CODEC  → JsonObject { fluid: "...", amount: N }
 *
 * // RecipeJsonBuilder 的 setPath/merge/property 也走这个路径：
 * builder.setPath('result.count', 4)                        // number → JsonPrimitive
 * builder.setPath('ingredients.0', Ingredient.of('minecraft:stone'))  // → Codec 序列化
 * builder.merge({ group: 'nekojs_test' })                   // JS object → JsonObject
 * </pre>
 *
 * <h2>GraalVM 重载匹配</h2>
 * <p>因为 {@code RecipeJsonValueAdapter.getPrecedence() = LOWEST}，
 * handler 的重载方法中 {@code ItemStack} / {@code Ingredient} 等具体类型会优先匹配，
 * {@code RecipeJsonValue} 作为最后的兜底参数。用户传 raw JSON object
 * 时才命中这个 overload：</p>
 *
 * <pre>
 * // 命中 crafting_shaped(ItemStack, List, Map) — 优先
 * handler.crafting_shaped(ItemJS.of('stick', 4), ['AA','AA'], {A: Ingredient.of('stone')})
 *
 * // 命中 crafting_shaped(RecipeJsonValue) — LOWEST，兜底
 * handler.crafting_shaped({ result: {id:'stick'}, pattern:['AA'], key:{A:{item:'stone'}} })
 * </pre>
 */
public record RecipeJsonValue(Object value) {
}
