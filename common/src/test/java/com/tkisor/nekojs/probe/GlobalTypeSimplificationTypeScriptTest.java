package com.tkisor.nekojs.probe;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.RecipeEventDeclarationGenerator;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class GlobalTypeSimplificationTypeScriptTest {
    public static final class Payload { public final String name; public Payload(String name) { this.name = name; } }
    @FunctionalInterface public interface StringSource { List<String> get(); }
    @FunctionalInterface public interface PayloadSource { List<Payload> get(); }
    public static final class NativeBuilder { public NativeBuilder finish() { return this; } }
    public static final class Handler {
        public NativeBuilder assemble(List<String> pattern, Map<String, Payload> key, Optional<String> group) { return new NativeBuilder(); }
    }
    public static final class Api {
        public int strings(List<String> value) { return value.size(); }
        public int numbers(Collection<Integer> value) { return value.size(); }
        public String iterable(Iterable<String> value) { return value.iterator().next(); }
        public int set(Set<String> value) { return value.size(); }
        public String adapted(List<Payload> value) { return value.getFirst().name; }
        public String nested(List<List<String>> value) { return value.getFirst().getFirst(); }
        public String mapped(Map<String, Payload> value) { return value.get("key").name; }
        public String numericMap(Map<Integer, Payload> value) { return value.get(1).name; }
        public String source(StringSource value) { return value.get().getFirst(); }
        public String adaptedSource(PayloadSource value) { return value.get().getFirst().name; }
        public String sources(StringSource[] value) { return value[0].get().getFirst(); }
    }

    @Test void writeGlobalCompactInputSemanticEquivalenceFixture() throws Exception {
        var aliases = new TypeAliasRegistry();
        var adapters = new AdapterAliasGenerator(aliases);
        adapters.prepare(List.of(new AdapterCatalogEntry(Payload.class, List.of(self(), string()), ConversionPrecedence.LOW, Optional.empty())), Set.of(Payload.class.getName()));
        var functions = new FunctionalInterfaceAliasGenerator(aliases);
        // The fixture emits these signature dependencies below; mirror the backend's completed declaration closure.
        functions.prepare(Set.of(StringSource.class.getName(), PayloadSource.class.getName(),
                List.class.getName(), Payload.class.getName()), Set.of());
        assertFalse(functions.declaration(StringSource.class.getName()).contains("CallbackResult = any"),
                "A declared callback return container must not fall back to missing-type any");
        assertFalse(functions.declaration(PayloadSource.class.getName()).contains("CallbackResult = any"),
                "Declared adapted callback payloads must retain their input contract");
        String api = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(Api.class));
        assertTrue(api.contains("$List_<string>"), api);
        Path directory = Path.of("build/probe-ts-global-type-simplification");
        Files.createDirectories(directory);
        StringBuilder types = new StringBuilder("""
                declare module "java:java/util" {
                    export class $List<E> { private readonly listIdentity: never; get(index: number): E; add(value: E): boolean; }
                    export class $Collection<E> { private readonly collectionIdentity: never; add(value: E): boolean; }
                    export class $Set<E> { private readonly setIdentity: never; add(value: E): boolean; }
                    export class $Map<K,V> { private readonly mapIdentity: never; get(key: K): V; put(key: K, value: V): V; }
                """);
        for (Class<?> type : List.of(List.class, Collection.class, Set.class)) {
            String body = aliases.getCollectionAlias(type.getName(), new String[]{"E"});
            types.append("    export type ").append(TypeAliasRegistry.collectionInputAlias(type.getName())).append("<E> = ").append(body).append(";\n");
        }
        types.append("    export type $Map_<K,V> = ").append(aliases.getCollectionAlias(Map.class.getName(), new String[]{"K", "V"})).append(";\n}\n");
        types.append("""
                declare module "java:java/lang" {
                    export class $Iterable<E> { private readonly iterableIdentity: never; iterator(): {next(): E}; }
                """);
        types.append("    export type $Iterable_<E> = ").append(aliases.getCollectionAlias(Iterable.class.getName(), new String[]{"E"})).append(";\n}\n");
        types.append("""
                declare module "java:com/tkisor/nekojs/probe" {
                    import { $List, $List_, $Collection, $Collection_, $Set, $Map, $Map_ } from "java:java/util";
                    import { $Iterable, $Iterable_ } from "java:java/lang";
                    export class $GlobalTypeSimplificationTypeScriptTest$Payload { private readonly payloadIdentity: never; readonly name: string; }
                    export interface $GlobalTypeSimplificationTypeScriptTest$StringSource { get(): $List<string>; }
                    export interface $GlobalTypeSimplificationTypeScriptTest$PayloadSource { get(): $List<$GlobalTypeSimplificationTypeScriptTest$Payload>; }
                    export class $GlobalTypeSimplificationTypeScriptTest$NativeBuilder { private readonly builderIdentity: never; finish(): this; }
                """);
        var payload = adapters.getAlias(Payload.class.getName());
        types.append("    export type ").append(payload.aliasName()).append(payload.typeParameters()).append(" = ").append(payload.union()).append(";\n");
        types.append(functions.declaration(StringSource.class.getName())).append(functions.declaration(PayloadSource.class.getName()));
        types.append(api);
        types.append("    export type GenericMap<K> = ").append(inputMap(ApiTypeRef.typeVariable("K"), aliases)).append(";\n");
        types.append("    export type MixedMap = ").append(inputMap(ApiTypeRef.union(List.of(ApiTypeRef.primitive("string"), symbol(Payload.class))), aliases)).append(";\n");
        types.append("    export type BoolMap = ").append(inputMap(ApiTypeRef.primitive("boolean"), aliases)).append(";\n}\n");
        var scoped = aliases.withReferences((fqn, name) -> {
            if (fqn.equals(List.class.getName())) return name.equals("$List_") ? "CollisionListInput" : "CollisionListHost";
            return name;
        });
        types.append("""
                declare module "global-input-collision" {
                    import { $List as CollisionListHost, $List_ as CollisionListInput, $Collection, $Collection_, $Set, $Map, $Map_ } from "java:java/util";
                    import { $Iterable, $Iterable_ } from "java:java/lang";
                    import { $GlobalTypeSimplificationTypeScriptTest$Payload, $GlobalTypeSimplificationTypeScriptTest$Payload_,
                        $GlobalTypeSimplificationTypeScriptTest$StringSource, $GlobalTypeSimplificationTypeScriptTest$StringSource_,
                        $GlobalTypeSimplificationTypeScriptTest$PayloadSource, $GlobalTypeSimplificationTypeScriptTest$PayloadSource_ } from "java:com/tkisor/nekojs/probe";
                """);
        types.append(new TypeScriptClassRenderer(scoped).render(new TypeReflector().reflect(Api.class))).append("}\n");
        Files.writeString(directory.resolve("types.d.ts"), types);
        var namespace = RecipeNamespaceCatalogEntry.withHandlerMethods("unrelated", Handler.class, List.of("assemble"), true, List.of());
        Files.writeString(directory.resolve("recipes.d.ts"), new RecipeEventDeclarationGenerator(aliases).generate(List.of(namespace), ScriptType.SERVER));
        Files.writeString(directory.resolve("exercise.ts"), """
                import type { $List, $Collection, $Set, $Map } from "java:java/util";
                import type { $Iterable } from "java:java/lang";
                import type { $GlobalTypeSimplificationTypeScriptTest$Api as Api,
                    $GlobalTypeSimplificationTypeScriptTest$Payload as Payload,
                    $GlobalTypeSimplificationTypeScriptTest$Payload_ as PayloadInput,
                    GenericMap, MixedMap, BoolMap } from "java:com/tkisor/nekojs/probe";
                import type { $GlobalTypeSimplificationTypeScriptTest$Api as CollisionApi } from "global-input-collision";
                import type { DocumentedRecipes } from "./recipes";
                declare const api: Api, collision: CollisionApi, recipes: DocumentedRecipes;
                declare const payload: Payload, hostPayloads: $List<Payload>, hostStrings: $List<string>;
                declare const hostNested: $List<$List<string>>, hostMap: $Map<string, Payload>, hostNumberMap: $Map<number, Payload>;
                declare const hostSet: $Set<string>;
                type OldList = $List<string> | string[];
                declare let compactList: Parameters<Api["strings"]>[0], expandedList: OldList;
                compactList = expandedList; expandedList = compactList;
                declare let compactCollection: Parameters<Api["numbers"]>[0], expandedCollection: $Collection<number> | number[];
                compactCollection = expandedCollection; expandedCollection = compactCollection;
                declare let compactIterable: Parameters<Api["iterable"]>[0], expandedIterable: $Iterable<string> | string[];
                compactIterable = expandedIterable; expandedIterable = compactIterable;
                type OldAdapted = $List<Payload> | PayloadInput[];
                declare let compactAdapted: Parameters<Api["adapted"]>[0], expandedAdapted: OldAdapted;
                compactAdapted = expandedAdapted; expandedAdapted = compactAdapted;
                type OldNested = $List<$List<string>> | ($List<string> | string[])[];
                declare let compactNested: Parameters<Api["nested"]>[0], expandedNested: OldNested;
                compactNested = expandedNested; expandedNested = compactNested;
                type OldMap<K, HostV, InputV> = $Map<K,HostV> | (([K] extends [PropertyKey] ? unknown : never) & Partial<Record<Extract<K,PropertyKey>,InputV>>);
                declare let compactMap: Parameters<Api["mapped"]>[0], expandedMap: OldMap<string,Payload,PayloadInput>;
                compactMap = expandedMap; expandedMap = compactMap;
                declare let compactNumberMap: Parameters<Api["numericMap"]>[0], expandedNumberMap: OldMap<number,Payload,PayloadInput>;
                compactNumberMap = expandedNumberMap; expandedNumberMap = compactNumberMap;
                function genericEquivalent<K>(compact: GenericMap<K>, expanded: OldMap<K,Payload,PayloadInput>) { compact = expanded; expanded = compact; }
                declare let compactMixed: MixedMap, expandedMixed: OldMap<string | Payload,Payload,PayloadInput>;
                compactMixed = expandedMixed; expandedMixed = compactMixed;
                declare let compactBool: BoolMap, expandedBool: OldMap<boolean,Payload,PayloadInput>;
                compactBool = expandedBool; expandedBool = compactBool;
                api.strings(["one"]); api.strings(hostStrings); api.numbers([1]); api.iterable(["one"]);
                api.adapted(["encoded", payload]); api.adapted(hostPayloads);
                api.nested(hostNested); api.nested([hostStrings, ["one"]]);
                api.mapped({}); api.mapped({key: "encoded"}); api.mapped(hostMap);
                api.numericMap({}); api.numericMap({1: payload}); api.numericMap(hostNumberMap);
                api.source(() => ["one"]); api.adaptedSource(() => ["encoded", payload]); api.sources([() => ["one"]]);
                api.set(hostSet);
                collision.strings(["one"]); collision.strings(hostStrings); collision.adapted(["encoded"]); collision.mapped({key: payload});
                recipes.unrelated.assemble(["A"], {A: "encoded"}).finish();
                recipes.unrelated.assemble(hostStrings, hostMap, undefined).finish();
                // @ts-expect-error Host List<string> is not a host List<Payload>, despite guest string adaptation.
                api.adapted(hostStrings);
                declare const wrongHostMap: $Map<string,string>;
                // @ts-expect-error Host map value arguments retain nominal identity.
                api.mapped(wrongHostMap);
                // @ts-expect-error Guest map values keep their adapter input contract.
                api.mapped({key: false});
                // @ts-expect-error Guest arrays do not implement native Set operations.
                api.set(["one"]);
                // @ts-expect-error A boolean map key has no supported guest record representation.
                compactBool = {};
                // @ts-expect-error Mixed property/nonproperty key domains retain the guard, not a false record widening.
                compactMixed = {key: payload};
                // @ts-expect-error Function-array precedence must not turn an array parameter into a callback returning an array.
                api.sources(() => ["one"]);
                // @ts-expect-error Callback return element input contracts are still checked.
                api.source(() => [false]);
                // @ts-expect-error Native handler shares the same nominal host map rule.
                recipes.unrelated.assemble(hostStrings, wrongHostMap);
                """);
        Files.writeString(directory.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},
                "files":["types.d.ts","recipes.d.ts","exercise.ts"]}
                """);
    }
    private static ApiTypeRef symbol(Class<?> type, ApiTypeRef... arguments) {
        return ApiTypeRef.symbol(new ApiSymbolId("java", type.getName()), List.of(arguments));
    }
    private static String inputMap(ApiTypeRef key, TypeAliasRegistry aliases) {
        return TypeScriptClassRenderer.renderTypeRef(symbol(Map.class, key, symbol(Payload.class)), aliases, true);
    }
}
