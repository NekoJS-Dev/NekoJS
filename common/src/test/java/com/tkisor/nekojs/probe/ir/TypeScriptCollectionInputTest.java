package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.probe.backend.typescript.FunctionalInterfaceAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptCollectionInputTest {
    public static class Payload {
        public String name;
        public Payload(String name) { this.name = name; }
    }
    public static class NonKey { public int id; }
    public static class Api {
        public List<String> hostList = List.of("one", "two");
        public Set<String> hostSet = new LinkedHashSet<>(hostList);
        public List<Payload> hostPayloads = List.of(new Payload("host"));
        public Map<String, Payload> hostMap = Map.of("key", new Payload("mapped-host"));
        public Map<NonKey, String> unconvertible;
        public int list(List<String> value) { return value.size(); }
        public int collection(Collection<String> value) { return value.size(); }
        public int set(Set<String> value) { return value.size(); }
        public Set<String> wrapSet(Set<String> value) { return java.util.Collections.unmodifiableSet(value); }
        public String iterable(Iterable<String> value) { return value.iterator().next(); }
        public String adapted(List<Payload> value) { return value.getFirst().name; }
        public String mapped(Map<String, Payload> value) { return value.get("key").name; }
        public void keyed(Map<NonKey, String> value) {}
        public int concreteList(java.util.ArrayList<String> value) { return value.size(); }
        public int sequenced(java.util.SequencedCollection<String> value) { return value.size(); }
        public int sequencedSet(java.util.SequencedSet<String> value) { return value.size(); }
        public String sortedMap(java.util.SortedMap<String, String> value) { return value.get("key"); }
        public String sequencedMap(java.util.SequencedMap<String, String> value) { return value.get("key"); }
    }

    @Test
    void hostListsRemainValidInputsWhenElementAdaptersWidenArrayElements() throws Exception {
        var aliases = new TypeAliasRegistry();
        aliases.registerClassAlias(Payload.class.getName(), "string");
        var source = Api.class.getMethod("adapted", List.class).getGenericParameterTypes()[0];
        String type = FunctionalInterfaceAliasGenerator.renderJavaTypeInput(source, aliases, new LinkedHashSet<>());
        assertEquals("($List<$TypeScriptCollectionInputTest$Payload> | (string)[])", type);
        TypeDecl declaration = new TypeReflector().reflect(Api.class);
        var slot = declaration.methods.stream().filter(method -> method.name.equals("adapted"))
                .findFirst().orElseThrow().params.getFirst().type;
        slot.overridden = true;
        String edited = new TypeScriptClassRenderer(aliases).render(declaration);
        assertTrue(edited.contains("adapted(value: " + type + ")"), edited);
        String reflected = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(Api.class));
        Path output = Path.of("build/probe-ts-collection-input");
        Files.createDirectories(output);
        // The fixture supplies only host operations needed to distinguish host containers from arrays.
        // Its API declaration is the actual production renderer output, not a full JDK declaration acceptance.
        Files.writeString(output.resolve("index.d.ts"), """
                declare module 'collection-input-fixture' {
                  export interface $Iterable<E> { iterator(): {next(): E}; }
                  export interface $Collection<E> extends $Iterable<E> { size(): number; add(value: E): boolean; }
                  export interface $List<E> extends $Collection<E> { get(index: number): E; }
                  export interface $Set<E> extends $Collection<E> {}
                  export interface $ArrayList<E> extends $List<E> {}
                  export interface $SequencedCollection<E> extends $Collection<E> { getFirst(): E; }
                  export interface $SequencedSet<E> extends $Set<E> { getFirst(): E; }
                  export interface $Map<K,V> { get(key: K): V; put(key: K, value: V): V; }
                  export interface $SortedMap<K,V> extends $Map<K,V> { firstKey(): K; }
                  export interface $SequencedMap<K,V> extends $Map<K,V> { firstEntry(): {getKey(): K; getValue(): V}; }
                  export interface $TypeScriptCollectionInputTest$Payload { name: string; }
                  export interface $TypeScriptCollectionInputTest$NonKey { id: number; }
                """ + reflected + "}\n");
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import type { $TypeScriptCollectionInputTest$Api as Api } from 'collection-input-fixture';
                declare const api: Api;
                api.list(api.hostList); api.list(['value']);
                api.collection(api.hostList); api.collection(['value']);
                api.iterable(api.hostList); api.iterable(['value']);
                api.set(api.hostSet);
                api.adapted(api.hostPayloads); api.adapted(['encoded']);
                api.mapped(api.hostMap); api.mapped({key:'encoded'});
                api.keyed(api.unconvertible);
                // @ts-expect-error HostAccess does not convert arrays into Java Set.
                api.set(['value']);
                // @ts-expect-error Converted element aliases must not replace invariant host arguments.
                api.adapted(api.hostList);
                // @ts-expect-error Array element conversions retain their accepted input types.
                api.adapted([false]);
                // @ts-expect-error Map values retain their actual adapter input type.
                api.mapped({key:false});
                // @ts-expect-error A non-convertible host key does not accept a record input.
                api.keyed({key:'encoded'});
                """);
        aliases.registerClassAlias(Payload.class.getName(), "string | number");
        String union = FunctionalInterfaceAliasGenerator.renderJavaTypeInput(source, aliases, new LinkedHashSet<>());
        assertEquals("($List<$TypeScriptCollectionInputTest$Payload> | (string | number)[])", union);
    }

    @Test
    void hostCollectionAndIterableInputsDoNotLoseTheirIdentity() throws Exception {
        var aliases = new TypeAliasRegistry();
        String output = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(Api.class));
        assertTrue(output.contains("list(value: ($List<string> | (string)[]))"), output);
        assertTrue(output.contains("collection(value: ($Collection<string> | (string)[]))"), output);
        assertTrue(output.contains("iterable(value: ($Iterable<string> | (string)[]))"), output);
        assertTrue(output.contains("set(value: $Set<string>)"), output);
        assertEquals("($List<any> | (any)[])", FunctionalInterfaceAliasGenerator.renderJavaTypeInput(
                List.class, aliases, new LinkedHashSet<>()));
        var scoped = aliases.withReferences((fqn, name) -> "Local" + name);
        assertEquals("(Local$List<Host> | (string | number)[])", scoped.getCollectionAlias(
                List.class.getName(), new String[]{"Host"}, new String[]{"string | number"}));
        aliases.registerCollectionAlias("fixture.Container", "$Container", "[]");
        assertEquals("Input[]", aliases.getCollectionAlias("fixture.Container",
                new String[]{"Host"}, new String[]{"Input"}));
        aliases.registerClassAlias(List.class.getName(), "boolean");
        String adapted = new TypeScriptClassRenderer(aliases).render(new TypeReflector().reflect(Api.class));
        assertTrue(adapted.contains("list(value: boolean)"), adapted);
    }

    @Test
    void fallbackCollectionAliasesRetainHostOnlyInputsOutsideSupportedConversions() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<String> classes = List.of("ArrayList", "LinkedList", "SequencedCollection", "HashSet", "TreeSet",
                "LinkedHashSet", "SequencedSet", "HashMap", "TreeMap", "LinkedHashMap", "SortedMap", "SequencedMap");
        Set<String> names = new LinkedHashSet<>();
        classes.forEach(name -> names.add("java.util." + name));
        String declarations = generator.generate("java.util", classes, List.of(), names);
        Path output = Path.of("build/probe-ts-collection-input/fallback");
        Files.createDirectories(output);
        // Minimal host operations distinguish real containers from array/record conversion inputs.
        String hosts = """
                declare module 'java:java/util' {
                  export interface $ArrayList<E> { get(index: number): E; }
                  export interface $LinkedList<E> { get(index: number): E; }
                  export interface $SequencedCollection<E> { getFirst(): E; }
                  export interface $HashSet<E> { contains(value: E): boolean; }
                  export interface $TreeSet<E> { first(): E; }
                  export interface $LinkedHashSet<E> { getFirst(): E; }
                  export interface $SequencedSet<E> { getFirst(): E; }
                  export interface $HashMap<K,V> { get(key: K): V; }
                  export interface $TreeMap<K,V> { firstKey(): K; get(key: K): V; }
                  export interface $LinkedHashMap<K,V> { get(key: K): V; }
                  export interface $SortedMap<K,V> { firstKey(): K; get(key: K): V; }
                  export interface $SequencedMap<K,V> { firstEntry(): {getKey(): K; getValue(): V}; }
                }
                """;
        Files.writeString(output.resolve("index.d.ts"), hosts + declarations);
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["index.d.ts","exercise.ts"]}
                """);
        StringBuilder exercise = new StringBuilder();
        for (String name : classes) {
            boolean map = name.endsWith("Map");
            String arguments = map ? "boolean, string" : "string";
            exercise.append("import type { $").append(name).append(", $").append(name)
                    .append("_ } from 'java:java/util';\n")
                    .append("declare const host").append(name).append(": $").append(name).append('<').append(arguments).append(">;\n")
                    .append("const input").append(name).append(": $").append(name).append("_<").append(arguments)
                    .append("> = host").append(name).append(";\n")
                    .append("// @ts-expect-error This container has no native array/record conversion.\n")
                    .append("const rejected").append(name).append(": $").append(name).append("_<").append(arguments)
                    .append("> = ").append(map ? "{key:'value'}" : "['value']").append(";\n");
        }
        Files.writeString(output.resolve("exercise.ts"), exercise);
    }

    @Test
    void productionHostAccessDistinguishesCollectionConversionsFromHostIdentity() {
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(new NekoSharedHostAccess(List.of(new JSTypeAdapter<Payload>() {
                    public Class<Payload> getTargetClass() { return Payload.class; }
                    public boolean test(Value value) { return value.isString(); }
                    public Payload apply(Value value) { return new Payload(value.asString()); }
                })).get())
                .allowHostClassLookup(name -> false).allowCreateThread(false).allowCreateProcess(false).build()) {
            context.getBindings("js").putMember("api", new Api());
            assertEquals(2, context.eval("js", "api.list(api.hostList)").asInt());
            assertEquals(2, context.eval("js", "api.list(['one', 'two'])").asInt());
            assertEquals(2, context.eval("js", "api.collection(api.hostList)").asInt());
            assertEquals(2, context.eval("js", "api.collection(['one', 'two'])").asInt());
            assertEquals(2, context.eval("js", "api.set(api.hostSet)").asInt());
            assertThrows(PolyglotException.class, () -> context.eval("js", "api.set(['one', 'two'])"));
            // Graal may construct a lazy interface proxy; exercising its operation proves the conversion usable.
            assertTrue(context.eval("js", "api.wrapSet(['one'])").isHostObject());
            assertThrows(PolyglotException.class, () -> context.eval("js", "api.wrapSet(['one']).size()"));
            assertEquals("one", context.eval("js", "api.iterable(api.hostList)").asString());
            assertEquals("one", context.eval("js", "api.iterable(['one', 'two'])").asString());
            assertEquals("host", context.eval("js", "api.adapted(api.hostPayloads)").asString());
            assertEquals("encoded", context.eval("js", "api.adapted(['encoded'])").asString());
            assertEquals("mapped-host", context.eval("js", "api.mapped(api.hostMap)").asString());
            assertEquals("encoded", context.eval("js", "api.mapped({key:'encoded'})").asString());
            var bindings = context.getBindings("js");
            bindings.putMember("concrete", new java.util.ArrayList<>(List.of("one")));
            bindings.putMember("sequencedSet", new java.util.LinkedHashSet<>(List.of("one")));
            bindings.putMember("sorted", new java.util.TreeMap<>(Map.of("key", "mapped")));
            bindings.putMember("sequencedMap", new java.util.LinkedHashMap<>(Map.of("key", "mapped")));
            assertEquals(1, context.eval("js", "api.concreteList(concrete)").asInt());
            assertEquals(1, context.eval("js", "api.sequenced(concrete)").asInt());
            assertEquals(1, context.eval("js", "api.sequencedSet(sequencedSet)").asInt());
            assertEquals("mapped", context.eval("js", "api.sortedMap(sorted)").asString());
            assertEquals("mapped", context.eval("js", "api.sequencedMap(sequencedMap)").asString());
            for (String call : List.of("api.concreteList(['one'])", "api.sequenced(['one'])",
                    "api.sequencedSet(['one'])", "api.sortedMap({key:'value'})", "api.sequencedMap({key:'value'})")) {
                assertThrows(PolyglotException.class, () -> context.eval("js", call), call);
            }
        }
    }
}
