package com.tkisor.nekojs.probe;

import static org.junit.jupiter.api.Assertions.*;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.RecipeHandlerMethodEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeSchemaTypeEntry;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeRecipeHandlerTypeScriptTest {
    @Test void writeNativeHandlerStrictOverloadConsumerFixture() throws Exception {
        var fixture = NativeRecipeHandlerDeclarationTest.fixture();
        var weak = new RecipeSchemaTypeEntry("nativefixture", "crafting_shaped", "nativefixture_crafting_shaped",
                List.of(new RecipeSchemaTypeEntry.SchemaField("ingredients", "STRING", true, false, "ingredients")),
                List.of(List.of("ingredients")), Map.of(), List.of(), null);
        var nativeNamespace = NativeRecipeHandlerDeclarationTest.namespace().withSchemaTypes(List.of(weak));
        var manual = new RecipeHandlerMethodEntry("manual", List.of(new RecipeHandlerMethodEntry.HandlerParam("result", "string", null, null, false)), 1);
        var oldNamespace = new RecipeNamespaceCatalogEntry("manualfixture", null, List.of("manual"), true, List.of(), List.of(manual));
        var generator = new RecipeEventDeclarationGenerator(fixture.aliases());
        String declarations = generator.generate(List.of(nativeNamespace, oldNamespace), ScriptType.SERVER);
        assertEquals(declarations, generator.generate(List.of(nativeNamespace, oldNamespace), ScriptType.SERVER));
        Path directory = Path.of("build/probe-ts-native-recipe-handlers");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("recipes.d.ts"), declarations);
        StringBuilder dependencies = new StringBuilder("""
                declare module "java:com/tkisor/nekojs/api/recipe" {
                    export class $RecipeJsonBuilder { private readonly baseBuilderIdentity: never; id(value: string): this; }
                }
                declare module "java:java/util" {
                    export class $List<E> { private readonly listIdentity: never; get(index: number): E; add(value: E): boolean; }
                    export type $List_<E> = %s;
                    export class $Map<K, V> { private readonly mapIdentity: never; get(key: K): V; put(key: K, value: V): V; }
                }
                declare module "java:com/tkisor/nekojs/probe" {
                    export class $NativeRecipeHandlerDeclarationTest$Payload { private readonly payloadIdentity: never; readonly name: string; }
                    export class $NativeRecipeHandlerDeclarationTest$GenericBox<T> { private readonly boxIdentity: never; get(): T; }
                    export class $NativeRecipeHandlerDeclarationTest$NativeBuilder { private readonly nativeBuilderIdentity: never; nativeOnly(energy: number): this; }
                    export class $NativeRecipeHandlerDeclarationTest$SpecificBuilder { private readonly specificBuilderIdentity: never; specificOnly(label: string): this; }
                """.formatted(fixture.aliases().getCollectionAlias("java.util.List", new String[]{"E"})));
        appendAlias(dependencies, fixture.adapters().getAlias(NativeRecipeHandlerDeclarationTest.Payload.class.getName()));
        appendAlias(dependencies, fixture.adapters().getAlias(NativeRecipeHandlerDeclarationTest.GenericBox.class.getName()));
        dependencies.append("}\n");
        Files.writeString(directory.resolve("dependencies.d.ts"), dependencies);
        Files.writeString(directory.resolve("exercise.ts"), """
                import type { DocumentedRecipes } from "./recipes";
                import type { $List, $Map } from "java:java/util";
                import type { $NativeRecipeHandlerDeclarationTest$Payload as Payload,
                    $NativeRecipeHandlerDeclarationTest$GenericBox as GenericBox,
                    $NativeRecipeHandlerDeclarationTest$NativeBuilder as NativeBuilder,
                    $NativeRecipeHandlerDeclarationTest$SpecificBuilder as SpecificBuilder
                } from "java:com/tkisor/nekojs/probe";
                declare const recipes: DocumentedRecipes;
                declare const payload: Payload;
                declare const hostPattern: $List<string>;
                declare const hostKey: $Map<string, Payload>;
                declare const hostPayloadRow: $List<Payload>;
                declare const hostNested: $List<$List<Payload>>;
                declare const wrongHostKey: $Map<string, string>;
                declare const wrongHostNested: $List<$List<string>>;
                declare const box: GenericBox<Payload>;
                declare const wrongBox: GenericBox<string>;
                const shaped: NativeBuilder = recipes.nativefixture.crafting_shaped("demo:result", ["AB", "BA"], {A: "adapted", B: payload});
                shaped.nativeOnly(200);
                recipes.nativefixture.crafting_shaped("demo:result", hostPattern, hostKey, "group").nativeOnly(1);
                recipes.nativefixture.crafting_shaped("demo:result", ["A"], {A: payload}, undefined).nativeOnly(2);
                recipes.nativefixture.crafting_shaped("demo:result", [["adapted"], [payload]]).nativeOnly(3);
                recipes.nativefixture.crafting_shaped("demo:result", hostNested, "group").nativeOnly(4);
                recipes.nativefixture.crafting_shaped("demo:result", [hostPayloadRow, ["adapted"]], undefined).nativeOnly(5);
                const specific: SpecificBuilder = recipes.nativefixture.unrelated("adapted");
                specific.specificOnly("native");
                recipes.nativefixture.unrelated(payload).specificOnly("host");
                const history: $List<NativeBuilder> = recipes.nativefixture.history();
                history.get(0).nativeOnly(6);
                const indexed: $Map<string, SpecificBuilder> = recipes.nativefixture.indexed();
                indexed.get("one").specificOnly("generic return");
                const generic: GenericBox<Payload> = recipes.nativefixture.boxed(box);
                const member: string = generic.get().name;
                recipes.nativefixture.boxed({value: payload}).get().name;
                recipes.manualfixture.manual("demo:result").id("demo:id");
                // @ts-expect-error A flat pattern requires its key map, not the nested ingredients overload.
                recipes.nativefixture.crafting_shaped("demo:result", ["AB"]);
                // @ts-expect-error A key map is required to contain native Payload or its registered string input.
                recipes.nativefixture.crafting_shaped("demo:result", ["A"], {A: false});
                // @ts-expect-error Nested guest ingredient rows retain their actual Payload adapter inputs.
                recipes.nativefixture.crafting_shaped("demo:result", [[false]]);
                // @ts-expect-error Existing host map generic arguments are not converted as guest record values.
                recipes.nativefixture.crafting_shaped("demo:result", hostPattern, wrongHostKey);
                // @ts-expect-error Existing nested host list generic arguments keep their host identities.
                recipes.nativefixture.crafting_shaped("demo:result", wrongHostNested);
                // @ts-expect-error Optional group remains a string.
                recipes.nativefixture.crafting_shaped("demo:result", [[payload]], 42);
                // @ts-expect-error Too few native arguments must not match a permissive fallback.
                recipes.nativefixture.crafting_shaped("demo:result");
                // @ts-expect-error Too many arguments must not match an accidental rest signature.
                recipes.nativefixture.crafting_shaped("demo:result", ["A"], {A: payload}, "group", true);
                // @ts-expect-error Weak discovered schema does not introduce an alternative object-options overload.
                recipes.nativefixture.crafting_shaped({ingredients: "fallback"});
                // @ts-expect-error Native return types are not schema-inferred or unrelated builder types.
                const wrongReturn: SpecificBuilder = recipes.nativefixture.crafting_shaped("demo:result", [[payload]]);
                // @ts-expect-error Generic native returns keep actual host element types.
                const wrongHistory: $List<string> = recipes.nativefixture.history();
                // @ts-expect-error Generic adapter type parameters remain nominal under native parameter projection.
                recipes.nativefixture.boxed({value: "adapted"});
                // @ts-expect-error An incompatible existing host generic box is not rebuilt by an outer adapter.
                recipes.nativefixture.boxed(wrongBox);
                // @ts-expect-error Legacy entries without return metadata promise only the common base builder.
                recipes.manualfixture.manual("demo:result").nativeOnly(7);
                """);
        Files.writeString(directory.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022",
                "module":"ESNext","moduleResolution":"Bundler","types":[]},
                "files":["recipes.d.ts","dependencies.d.ts","exercise.ts"]}
                """);
    }
    private static void appendAlias(StringBuilder target, AdapterAliasGenerator.AdapterAlias alias) {
        target.append("    export type ").append(alias.aliasName()).append(alias.typeParameters()).append(" = ").append(alias.union()).append(";\n");
    }
}
