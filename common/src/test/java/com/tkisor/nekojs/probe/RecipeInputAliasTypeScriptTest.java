package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

/** Writes real generator output for the repository's separate strict TypeScript consumer check. */
class RecipeInputAliasTypeScriptTest {
    private static final AdapterInputShape RESULTS = union(registry("Item"), arrayOf(object(
            Slot.req("id", registry("Item")), Slot.opt("count", number()), Slot.opt("chance", number()))));
    private static final AdapterInputShape INGREDIENTS = arrayOf(union(registry("Item"), object(Slot.req("item", registry("Item")))));
    private static final AdapterInputShape PROCESSING_TIME = union(number(), object(Slot.req("ticks", number())));

    @Test
    void namedAndPositionalCallsKeepExactInputsAndTwoReadableOverloads() throws Exception {
        var rejected = new RecipeSchemaTypeEntry("create", "sequenced_assembly", "create_sequence",
                List.of(), List.of(), Map.of(), List.of(), "Unsupported sequence construction");
        var schemas = List.of(schema("cutting", RESULTS), schema("pressing", RESULTS), rejected);
        var aliases = new TypeAliasRegistry();
        var generator = new RecipeEventDeclarationGenerator(aliases);
        String output = generator.generate(List.of(namespace("create", schemas)), ScriptType.SERVER);
        assertEquals(2, count(output, "            cutting("), output);
        assertFalse(output.contains("export type Create$ResultsInput"), output);
        assertFalse(output.contains("export type Create$IngredientsInput"), output);
        assertFalse(output.contains("export type Create$ProcessingTimeInput"), output);
        assertTrue(output.contains("cutting(options: Create$CuttingOptions): Create$Cutting;"), output);
        assertTrue(output.contains("pressing(\n"
                + "                results: (RegistryTypes.Item | ({ id: RegistryTypes.Item, count?: number, chance?: number })[]),\n"
                + "                ingredients: (RegistryTypes.Item | { item: RegistryTypes.Item })[],\n"
                + "                processingTime?: (number | { ticks: number })\n"
                + "            ): Create$Pressing;"), output);
        assertTrue(output.contains("processingTime?: (number | { ticks: number })"), output);
        assertFalse(output.contains("Create$LabelInput"), "Atomic fields remain inline: " + output);
        assertTrue(output.contains("sequenced_assembly: never;"), output);
        assertFalse(output.contains("export class Create$SequencedAssembly"), output);
        assertEquals(output, generator.generate(List.of(namespace("create", schemas)), ScriptType.SERVER));
        assertNull(aliases.getAlias("Create$ResultsInput"));
        writeFixture("plain", output, """
                import type { DocumentedRecipes, Create$Cutting, Create$CuttingOptions } from "./recipes";
                declare const recipes: DocumentedRecipes;
                const results: Parameters<Create$Cutting["results"]>[0] = [{id: "minecraft:iron_ingot", count: 2, chance: 0.25}];
                const ingredients: Parameters<Create$Cutting["ingredients"]>[0] = ["minecraft:iron_block", {item: "minecraft:copper_block"}];
                const time: Parameters<Create$Cutting["processingTime"]>[0] = {ticks: 25};
                const options: Create$CuttingOptions = {output: results, input: ingredients, processingTime: time};
                const named: Create$Cutting = recipes.create.cutting(options);
                const short: Create$Cutting = recipes.create.cutting(results, ingredients);
                const long: Create$Cutting = recipes.create.cutting(results, ingredients, time);
                recipes.create.cutting({results, ingredients, processing_time: 50, label: "canonical"});
                recipes.create.pressing(results, ingredients, 20);
                named.results(results).output("minecraft:gold_ingot").ingredients(ingredients)
                    .input(["minecraft:gold_block"]).processing_time(time).processingTime(50).heated().label("named");
                // @ts-expect-error Missing a required canonical-or-alias input group.
                recipes.create.cutting({results});
                // @ts-expect-error Canonical and alias keys are exclusive, even for equal values.
                recipes.create.cutting({results, output: results, ingredients});
                // @ts-expect-error The ingredient alias is exclusive with its canonical key.
                recipes.create.cutting({results, ingredients, input: ingredients});
                // @ts-expect-error Optional aliases remain exclusive as well.
                recipes.create.cutting({results, ingredients, processing_time: 25, processingTime: 25});
                // @ts-expect-error Named options do not acquire a permissive unknown-key fallback.
                recipes.create.cutting({results, ingredients, typo: true});
                // @ts-expect-error Result object fields retain their original numeric types.
                recipes.create.cutting([{id: "minecraft:iron_ingot", count: "two"}], ingredients);
                // @ts-expect-error Registry-valued result inputs retain the selected registry identities.
                recipes.create.cutting("missing:unknown_item", ingredients);
                // @ts-expect-error Registry-valued nested object fields retain the selected registry identities.
                named.input([{item: "missing:unknown_item"}]);
                // @ts-expect-error The complete ingredient shape remains an array, not its element union.
                named.ingredients("minecraft:iron_block");
                // @ts-expect-error Array elements keep their original union instead of accepting arbitrary values.
                named.ingredients([5]);
                // @ts-expect-error The second positional argument is not optional.
                recipes.create.cutting(results);
                // @ts-expect-error There are exactly two overload shapes, not a rest-argument fallback.
                recipes.create.cutting(results, ingredients, 25, "extra");
                // @ts-expect-error Fluent aliases retain the complete field shape.
                named.output([{id: "minecraft:iron_ingot", chance: "likely"}]);
                // @ts-expect-error Object-shaped time requires numeric ticks.
                named.processingTime({ticks: "slow"});
                // @ts-expect-error No-argument declarative functions remain no-argument.
                named.heated(true);
                // @ts-expect-error No arbitrary fluent member is introduced.
                named.unknownField(1);
                // @ts-expect-error Known rejected profiles remain uncallable, not generic JSON fallbacks.
                recipes.create.sequenced_assembly({});
                """);
    }

    @Test
    void aliasesKeepDifferentShapesAndImportedHostsDistinctFromReservedLocalNames() throws Exception {
        var aliases = new TypeAliasRegistry();
        aliases.registerClassAlias(java.util.Date.class.getName(), "$Date$XInput");
        var schemas = List.of(schema("cutting", RESULTS), schema("pressing", RESULTS),
                schema("numeric", arrayOf(number())), schema("results_input", RESULTS),
                schema("cutting_options", RESULTS));
        var hostSchema = new RecipeSchemaTypeEntry("$Date", "collision", "host_collision", List.of(
                new RecipeSchemaTypeEntry.SchemaField("x", "JSON", true, false, "x",
                        object(Slot.req("value", host(java.util.Date.class))))),
                List.of(List.of("x")), Map.of(), List.of(), null);
        var namespaces = List.of(namespace("create", schemas), namespace("$Date", List.of(hostSchema)));
        var generator = new RecipeEventDeclarationGenerator(aliases);
        String output = generator.generate(namespaces, ScriptType.SERVER);
        assertTrue(output.contains("export class Create$ResultsInput extends"), output);
        assertTrue(output.contains("export class Create$CuttingOptions extends"), output);
        assertTrue(output.contains("export type Create$CuttingOptions2 = "), output);
        assertFalse(output.contains("export type $Date$XInput = "), output);
        assertTrue(output.contains("import { $Date } from"), output);
        assertFalse(output.contains("import { $Date$XInput"), "Explicit hosts do not widen to adapter aliases: " + output);
        assertEquals(output, generator.generate(namespaces, ScriptType.SERVER));
        assertEquals("$Date$XInput", aliases.getAlias(java.util.Date.class.getName()));
        writeFixture("collisions", output, """
                import type { DocumentedRecipes, Create$CuttingOptions2 } from "./recipes";
                import type { $Date as HostDate } from "java:java/util";
                declare const recipes: DocumentedRecipes;
                declare const date: HostDate;
                const options: Create$CuttingOptions2 = {results: "minecraft:iron_ingot", ingredients: ["minecraft:iron_block"]};
                recipes.create.cutting(options).results([{id: "minecraft:gold_ingot", count: 2}]);
                recipes.create.pressing("minecraft:iron_ingot", ["minecraft:iron_block"]);
                recipes.create.numeric([1, 2], ["minecraft:iron_block"]).results([3, 4]);
                recipes.create.results_input("minecraft:iron_ingot", ["minecraft:iron_block"]);
                recipes.create.cutting_options("minecraft:iron_ingot", ["minecraft:iron_block"]);
                recipes.$Date.collision({value: date}).x({value: date});
                recipes.$Date.collision({x: {value: date}});
                // @ts-expect-error The distinct numeric result shape cannot reuse the string-or-object alias.
                recipes.create.numeric("minecraft:iron_ingot", ["minecraft:iron_block"]);
                // @ts-expect-error The original result shape cannot reuse the numeric-array alias.
                recipes.create.cutting([1, 2], ["minecraft:iron_block"]);
                // @ts-expect-error Distinct fluent result shapes must not be pooled together.
                recipes.create.numeric([1], ["minecraft:iron_block"]).results([{id: "minecraft:gold_ingot"}]);
                // @ts-expect-error Host input stays bound to the imported nominal host, not the local object alias.
                recipes.$Date.collision({value: {value: date}});
                // @ts-expect-error A local alias does not make primitive values valid host inputs.
                recipes.$Date.collision({value: 1});
                """);
    }

    private static RecipeSchemaTypeEntry schema(String name, AdapterInputShape results) {
        return new RecipeSchemaTypeEntry("create", name, "create_" + name, List.of(
                new RecipeSchemaTypeEntry.SchemaField("results", "JSON", true, false, "results", results),
                new RecipeSchemaTypeEntry.SchemaField("ingredients", "JSON", true, false, "ingredients", INGREDIENTS),
                new RecipeSchemaTypeEntry.SchemaField("processing_time", "JSON", false, false, "processing_time", PROCESSING_TIME),
                new RecipeSchemaTypeEntry.SchemaField("label", "STRING", false, false, "label", string())),
                List.of(List.of("results", "ingredients"), List.of("results", "ingredients", "processing_time")),
                Map.of("output", "results", "input", "ingredients", "processingTime", "processing_time"),
                List.of("heated"), null);
    }

    private static RecipeNamespaceCatalogEntry namespace(String name, List<RecipeSchemaTypeEntry> schemas) {
        return new RecipeNamespaceCatalogEntry(name, null, schemas.stream().map(RecipeSchemaTypeEntry::type).toList(),
                true, List.of(), List.of(), schemas);
    }

    private static void writeFixture(String variant, String declarations, String exercise) throws Exception {
        Path output = Path.of("build/probe-ts-recipe-input-alias").resolve(variant);
        Files.createDirectories(output);
        Files.writeString(output.resolve("recipes.d.ts"), declarations);
        // These external dependency fixtures isolate recipe declaration semantics, not native builder inheritance.
        Files.writeString(output.resolve("dependencies.d.ts"), """
                declare module "@special/types" {
                    export namespace RegistryTypes {
                        export type Item = "minecraft:iron_ingot" | "minecraft:gold_ingot"
                            | "minecraft:iron_block" | "minecraft:copper_block" | "minecraft:gold_block";
                    }
                }
                declare module "java:com/tkisor/nekojs/api/recipe" {
                    export class $RecipeJsonBuilder {}
                }
                declare module "java:java/util" {
                    export class $Date { private readonly hostDateIdentity: never; }
                    export type $Date$XInput = $Date | { unsafe: boolean };
                }
                """);
        Files.writeString(output.resolve("exercise.ts"), exercise);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[]},
                "files":["recipes.d.ts","dependencies.d.ts","exercise.ts"]}
                """);
    }

    private static int count(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
