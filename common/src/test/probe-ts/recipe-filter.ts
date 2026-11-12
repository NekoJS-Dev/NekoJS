import type { $RecipeFilter, $RecipeFilter_, $RecipeMatchContext,
  $RecipeFilterExpression_, $RecipeFilterConditions_, $RecipeFilterInput_, $RecipeFilterOutput_
} from "java:com/tkisor/nekojs/api/recipe";
import type { $Ingredient, $Ingredient_ } from "java:net/minecraft/world/item/crafting";
import type { $ItemStack, $ItemStack_ } from "java:net/minecraft/world/item";
import type { $Pattern } from "java:java/util/regex";
import type { RegistryTypes } from "@special/types";

declare function accept(filter: $RecipeFilter_): void;
declare const nativeFilter: $RecipeFilter;
declare const nativeIngredient: $Ingredient;
declare const nativeStack: $ItemStack;
declare const hostPattern: $Pattern;

accept(nativeFilter);
accept("*");
accept("-");
accept("/");
accept("/minecraft:.*stick/");
accept("minecraft:made_up_recipe_id");
accept(/minecraft:.*stick/);
accept(hostPattern);
accept(match => match.recipe.getOrCreateId().startsWith("minecraft:"));
accept((match: $RecipeMatchContext): boolean => match.getRecipe().getMod() === "minecraft");
accept(match => match.getRecipe().getType() === "minecraft:crafting_shapeless");
accept(match => match.getRecipe().getGroup() === "tools");
accept({ id: /stick/, mod: hostPattern, group: /tools/ });
accept({ type: "minecraft:crafting_shapeless", recipeType: "minecraft:crafting" });
accept({ not: /stick/, and: [nativeFilter, { mod: "minecraft" }], or: ["-", match => true] });
accept([nativeFilter, /stick/, ["minecraft:stone", { id: "minecraft:stick" }]]);
accept({ input: "#minecraft:logs", output: "minecraft:stick" });
accept({ input: nativeIngredient, output: nativeStack });
accept({ input: { match: nativeIngredient, exact: true }, output: { match: nativeStack, exact: false } });
accept({ output: { match: nativeIngredient, exact: false } });
// @ts-expect-error Exact output matching requires a stack, not an Ingredient host.
accept({ output: { match: nativeIngredient, exact: true } });
// @ts-expect-error An ingredient tag is not an exact output stack.
accept({ output: { match: "#minecraft:logs", exact: true } });
accept({ input: { any: ["minecraft:stone", "minecraft:stick"] }, output: { item: "minecraft:stick", count: 2 } });

// @ts-expect-error The recipe Bean property is not a callable method.
accept(match => match.recipe().getOrCreateId() === "minecraft:stick");
// @ts-expect-error The native recipe Bean property is read-only.
accept(match => { match.recipe = match.getRecipe(); return true; });
// @ts-expect-error Context predicates must return boolean, not truthy numbers.
accept(match => 1);
// @ts-expect-error The callback receives the native match context, not an ID string.
accept((match: string) => match.length > 0);
// @ts-expect-error Unknown recipe selector keys must not become permissive index signatures.
accept({ imput: "minecraft:stone" });
// @ts-expect-error Context exposes read-only recipe identity, not a mutable builder.
accept(match => { match.getRecipe().setId("other:id"); return true; });
// @ts-expect-error Exact match information uses a boolean flag.
accept({ input: { match: nativeIngredient, exact: "yes" } });
// @ts-expect-error Match information must include its required match value.
accept({ input: { exact: true } });
// @ts-expect-error Native recipe type IDs are not serializer IDs.
accept({ type: "minecraft:crafting" });
// @ts-expect-error Serializer IDs are not native recipe type IDs.
accept({ recipeType: "minecraft:crafting_shapeless" });
// @ts-expect-error Native callback context values have no fabricated Rhino API.
accept(match => match.rhinoRecipe().id() === "minecraft:stick");
// @ts-expect-error Numbers are not recipe filters.
accept(42);

// Each readable helper is imported from the actual generated module and remains independently useful.
const namedExpression: $RecipeFilterExpression_ = /minecraft:/;
const namedInput: $RecipeFilterInput_ = { match: nativeIngredient, exact: true };
const namedOutput: $RecipeFilterOutput_ = { match: nativeStack, exact: true };
const namedConditions: $RecipeFilterConditions_ = {
  id: namedExpression, input: namedInput, output: namedOutput,
  type: "minecraft:crafting_shaped", recipeType: "minecraft:crafting",
  and: [match => match.recipe.getType() === "minecraft:crafting_shaped"]
};
accept(namedConditions);
accept({ input: namedInput, output: namedOutput });
// @ts-expect-error Expression helpers do not accept numbers.
const invalidExpression: $RecipeFilterExpression_ = 42;
// @ts-expect-error Named conditions remain strict about misspelled selector keys.
const invalidConditions: $RecipeFilterConditions_ = { imput: "minecraft:stone" };
// @ts-expect-error Named input match information still requires a match value.
const invalidInput: $RecipeFilterInput_ = { exact: true };
// @ts-expect-error Named output helpers cannot request exact Ingredient matching.
const invalidOutput: $RecipeFilterOutput_ = { match: nativeIngredient, exact: true };
// @ts-expect-error Naming does not make the exact flag truthy or string-valued.
const invalidNamedExact: $RecipeFilterInput_ = { match: nativeStack, exact: "yes" };

// Independent former inline contract: naming may restructure declarations, never accepted inputs.
type FormerRecipeFilter = $RecipeFilter | string | RegExp | $Pattern
  | ((match: $RecipeMatchContext) => boolean)
  | FormerRecipeFilter[]
  | {
      not?: FormerRecipeFilter;
      and?: FormerRecipeFilter;
      or?: FormerRecipeFilter;
      output?: $Ingredient_ | $ItemStack_
        | { match: $ItemStack_; exact?: boolean }
        | { match: $Ingredient_; exact?: false };
      input?: $Ingredient_ | $ItemStack_ | { match: $Ingredient_ | $ItemStack_; exact?: boolean };
      mod?: string | RegExp | $Pattern;
      group?: string | RegExp | $Pattern;
      id?: string | RegExp | $Pattern;
      idStartsWith?: string;
      idEndsWith?: string;
      idContains?: string;
      type?: RegistryTypes.RecipeSerializer;
      recipeType?: RegistryTypes.RecipeType;
    };
declare const formerlyExpanded: FormerRecipeFilter;
declare const newlyStructured: $RecipeFilter_;
const structuredAcceptsFormer: $RecipeFilter_ = formerlyExpanded;
const formerAcceptsStructured: FormerRecipeFilter = newlyStructured;
accept(structuredAcceptsFormer);
accept(formerAcceptsStructured);
