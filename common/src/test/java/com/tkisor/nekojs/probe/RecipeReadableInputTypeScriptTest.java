package com.tkisor.nekojs.probe;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Uses original nominal component types and real adapter-alias generation for strict/editor checks. */
class RecipeReadableInputTypeScriptTest {
    static final class FluidStack {}
    static final class ProcessingOutput {}
    static final class ItemStack {}
    static final class ItemLike {}
    static final class Ingredient {}
    static final class SizedFluidIngredient {}
    private static final String PREFIX = "$RecipeReadableInputTypeScriptTest$";

    @Test void writeReadableRecipeConsumerAndLanguageServiceFixture() throws Exception {
        var aliases = new TypeAliasRegistry();
        var adapters = new AdapterAliasGenerator(aliases);
        List<AdapterCatalogEntry> catalog = List.of(
                adapter(ItemStack.class, self(), string(), itemObject()),
                adapter(FluidStack.class, self(), string(), object(Slot.opt("fluid", string()), Slot.opt("id", string()), Slot.opt("amount", number()))),
                adapter(ProcessingOutput.class, self(), union(host(ItemStack.class), host(ItemLike.class),
                        object(Slot.req("id", string()), Slot.opt("count", number()), Slot.opt("components", raw("{ [key: string]: unknown }")), Slot.req("chance", union(number(), string()))),
                        object(Slot.req("item", string()), Slot.opt("count", number()), Slot.opt("components", raw("{ [key: string]: unknown }")), Slot.req("chance", union(number(), string()))),
                        object(Slot.req("output", host(ItemStack.class)), Slot.req("chance", union(number(), string())))), raw("null")),
                adapter(Ingredient.class, self(), string(), arrayOf(self()), host(ItemStack.class),
                        object(Slot.opt("item", string()), Slot.opt("tag", string()), Slot.opt("filter", raw("(item: " + PREFIX + "ItemStack) => boolean")),
                                Slot.opt("any", arrayOf(self())), Slot.opt("all", arrayOf(self())), Slot.opt("not", self()))),
                adapter(SizedFluidIngredient.class, self(), string(), host(FluidStack.class),
                        object(Slot.opt("ingredient", self()), Slot.opt("fluid", string()), Slot.opt("tag", string()), Slot.opt("amount", number()))));
        adapters.prepare(catalog, catalog.stream().map(entry -> entry.targetType().getName()).collect(Collectors.toSet()));
        assertNull(aliases.getRegisteredAlias(List.class.getName()), "Native List conversion has no JSTypeAdapter registration");
        ApiTypeRef resultElement = ApiTypeRef.union(List.of(nominal(FluidStack.class), nominal(ProcessingOutput.class)));
        ApiTypeRef inputElement = ApiTypeRef.union(List.of(nominal(SizedFluidIngredient.class), nominal(Ingredient.class)));
        ApiTypeRef results = nominal(List.class, resultElement);
        ApiTypeRef ingredients = nominal(List.class, inputElement);
        var schema = schema("cutting", results, ingredients);
        var selfSchema = schema("cutting_self", ApiTypeRef.union(List.of(results, resultElement)), ingredients);
        var namespace = new RecipeNamespaceCatalogEntry("create", null, List.of("cutting", "cutting_self"), true,
                List.of(), List.of(), List.of(schema, selfSchema));
        var generator = new RecipeEventDeclarationGenerator(aliases);
        String declarations = generator.generate(List.of(namespace), ScriptType.SERVER);
        assertEquals(declarations, generator.generate(List.of(namespace), ScriptType.SERVER));
        assertTrue(declarations.contains("$List_<"), declarations);
        assertTrue(declarations.contains(PREFIX + "FluidStack_"), declarations);
        assertFalse(declarations.contains("SingleOrArray"), declarations);
        assertFalse(declarations.contains("Create$FluidStackInput"), declarations);
        assertFalse(declarations.contains("Create$ProcessingOutputInput"), declarations);
        assertFalse(declarations.contains("Create$ResultsInput"), declarations);
        assertEquals(2, declarations.lines().filter(line -> line.stripLeading().startsWith("cutting(")).count(), declarations);
        Path output = Path.of("build/probe-ts-recipe-readable");
        Files.createDirectories(output);
        Files.writeString(output.resolve("recipes.d.ts"), declarations);
        StringBuilder dependencies = new StringBuilder("""
                declare module "java:com/tkisor/nekojs/api/recipe" { export class $RecipeJsonBuilder {} }
                declare module "java:com/tkisor/nekojs/probe" {
                    export class $RecipeReadableInputTypeScriptTest$FluidStack { private readonly fluidStackIdentity: never; }
                    export class $RecipeReadableInputTypeScriptTest$ProcessingOutput { private readonly processingOutputIdentity: never; }
                    export class $RecipeReadableInputTypeScriptTest$ItemStack { private readonly itemStackIdentity: never; }
                    export class $RecipeReadableInputTypeScriptTest$ItemLike { private readonly itemLikeIdentity: never; }
                    export class $RecipeReadableInputTypeScriptTest$Ingredient { private readonly ingredientIdentity: never; }
                    export class $RecipeReadableInputTypeScriptTest$SizedFluidIngredient { private readonly sizedFluidIngredientIdentity: never; }
                """);
        // Synthetic nominal hosts stand in for unavailable Minecraft classes; all input aliases are production-generated.
        for (var entry : catalog) appendAlias(dependencies, adapters.getAlias(entry.targetType().getName()));
        dependencies.append("}\n").append("""
                declare module "java:java/util" {
                    export class $List<E> { private readonly listIdentity: never; get(index: number): E; }
                """);
        String listInput = aliases.getCollectionAlias(List.class.getName(), new String[]{"E"});
        assertNotNull(listInput, "Canonical IndexFileGenerator List input projection is available without an adapter");
        dependencies.append("    export type $List_<E> = ").append(listInput).append(";\n");
        dependencies.append("}\n");
        Files.writeString(output.resolve("dependencies.d.ts"), dependencies);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { DocumentedRecipes, Create$Cutting, Create$CuttingOptions } from "./recipes";
                import type { $RecipeReadableInputTypeScriptTest$FluidStack as HostFluidStack,
                    $RecipeReadableInputTypeScriptTest$ProcessingOutput as HostProcessingOutput,
                    $RecipeReadableInputTypeScriptTest$Ingredient as HostIngredient,
                    $RecipeReadableInputTypeScriptTest$SizedFluidIngredient as HostSizedFluidIngredient,
                    $RecipeReadableInputTypeScriptTest$FluidStack_ as FluidInput,
                    $RecipeReadableInputTypeScriptTest$ProcessingOutput_ as OutputInput
                } from "java:com/tkisor/nekojs/probe";
                declare const recipes: DocumentedRecipes;
                declare const fluid: HostFluidStack;
                declare const output: HostProcessingOutput;
                declare const ingredient: HostIngredient;
                declare const sizedFluid: HostSizedFluidIngredient;
                const fluidDto: FluidInput = {fluid: "minecraft:water", amount: 1000};
                const defaultBucket: FluidInput = {fluid: "minecraft:water"};
                const outputDto: OutputInput = {id: "minecraft:iron_ingot", count: 2, chance: 0.25};
                const stringChance: OutputInput = {output: "minecraft:iron_ingot", chance: "0.25"};
                const plainItemDto: OutputInput = {id: "minecraft:iron_ingot", count: 2};
                const short: Create$Cutting = recipes.create.cutting([fluidDto, outputDto, fluid, output], [ingredient]);
                recipes.create.cutting([outputDto], [{item: "minecraft:iron_block"}, sizedFluid], 20);
                recipes.create.cutting([fluid], ["minecraft:iron_block"], undefined);
                recipes.create.cutting([defaultBucket, stringChance, plainItemDto], [{fluid: "minecraft:water", amount: 50}]);
                recipes.create.cutting([outputDto], [{filter: item => true}]);
                recipes.create.cutting_self(outputDto, [ingredient]);
                const options: Create$CuttingOptions = {output: [outputDto], input: [ingredient, sizedFluid], processingTime: 20};
                recipes.create.cutting(options);
                short.results([fluidDto, outputDto]).output([outputDto]).input([ingredient]).processingTime(20).predicate(value => value > 0);
                short.raw_choice(42); short.raw_choice(value => value > 0);
                short.raw_bare_choice(42); short.raw_bare_choice(value => value > 0);
                // @ts-expect-error List-only original component metadata does not advertise runtime scalar lenience.
                recipes.create.cutting(outputDto, [ingredient]);
                // @ts-expect-error Ingredients are also a declared original List, not scalar-or-array.
                recipes.create.cutting([outputDto], ingredient);
                // @ts-expect-error Original wrapped processing output requires chance when output is present.
                recipes.create.cutting([{output: "minecraft:iron_ingot"}], [ingredient]);
                // @ts-expect-error Processing chance accepts numbers/numeric strings, not boolean.
                recipes.create.cutting([{id: "minecraft:iron_ingot", chance: true}], [ingredient]);
                // @ts-expect-error Item count retains its canonical numeric DTO metadata.
                recipes.create.cutting([{id: "minecraft:iron_ingot", count: false}], [ingredient]);
                // @ts-expect-error Canonical input aliases do not accept unknown DTO keys.
                recipes.create.cutting([{unsafe: true}], [ingredient]);
                // @ts-expect-error Canonical Ingredient aliases do not acquire arbitrary object forms.
                recipes.create.cutting([outputDto], [{unsupported: true}]);
                // @ts-expect-error Required second constructor argument is not optional.
                recipes.create.cutting([outputDto]);
                // @ts-expect-error Only trailing processing time accepts undefined.
                recipes.create.cutting([outputDto], undefined);
                // @ts-expect-error No permissive rest signature is introduced.
                recipes.create.cutting([outputDto], [ingredient], 20, true);
                // @ts-expect-error Named canonical and alias fields remain exclusive.
                recipes.create.cutting({results: [outputDto], output: [outputDto], ingredients: [ingredient]});
                // @ts-expect-error Named ingredient list is required.
                recipes.create.cutting({results: [outputDto]});
                // @ts-expect-error Raw callback return type stays boolean.
                short.predicate(value => "wrong");
                // @ts-expect-error Wrapped raw callback union must not become a callback returning number.
                short.raw_choice(value => 42);
                // @ts-expect-error Bare raw callback union must remain atomic.
                short.raw_bare_choice(value => 42);
                """);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[]},
                "files":["recipes.d.ts","dependencies.d.ts","exercise.ts"]}
                """);
    }

    private static RecipeSchemaTypeEntry schema(String name, ApiTypeRef results, ApiTypeRef ingredients) {
        return new RecipeSchemaTypeEntry("create", name, name, List.of(
                new RecipeSchemaTypeEntry.SchemaField("results", "JSON", true, false, "results", null, results),
                new RecipeSchemaTypeEntry.SchemaField("ingredients", "JSON", true, false, "ingredients", null, ingredients),
                new RecipeSchemaTypeEntry.SchemaField("processing_time", "INT", false, false, "processing_time", null, ApiTypeRef.primitive("number")),
                new RecipeSchemaTypeEntry.SchemaField("predicate", "JSON", false, false, "predicate", raw("(value: number) => boolean")),
                new RecipeSchemaTypeEntry.SchemaField("raw_choice", "JSON", false, false, "raw_choice", union(raw("((value: number) => boolean)"), number())),
                new RecipeSchemaTypeEntry.SchemaField("raw_bare_choice", "JSON", false, false, "raw_bare_choice", union(raw("(value: number) => boolean"), number()))),
                List.of(List.of("results", "ingredients"), List.of("results", "ingredients", "processing_time")),
                Map.of("output", "results", "input", "ingredients", "processingTime", "processing_time"), List.of(), null);
    }
    private static AdapterInputShape itemObject() {
        return object(Slot.opt("id", string()), Slot.opt("item", string()), Slot.opt("count", number()), Slot.opt("components", raw("{ [key: string]: unknown }")));
    }
    private static AdapterCatalogEntry adapter(Class<?> type, AdapterInputShape... shapes) {
        return new AdapterCatalogEntry(type, List.of(shapes), ConversionPrecedence.LOW, Optional.empty());
    }
    private static ApiTypeRef nominal(Class<?> type, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", type.getName()), List.of(arguments));
    }
    private static void appendAlias(StringBuilder output, AdapterAliasGenerator.AdapterAlias alias) {
        output.append("    export type ").append(alias.aliasName()).append(alias.typeParameters()).append(" = ").append(alias.union()).append(";\n");
    }
}
