package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.fixtures.StaticShadowingValue;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptStaticShadowingTest {
    public static class Parent<T> {
        public static String TOKEN = "parent";
        public static String inherited = "inherited";
        public T value;
        public String getLabel() { return "label"; }
        public void setLabel(String value) {}
        public static int choose(int value) { return value; }
        public static String onlyParent() { return "parent"; }
        public static String altered(String value) { return "parent-" + value; }
    }
    public static class Child extends Parent<String> {
        public static int TOKEN = 7;
        public int childValue;
        public static String choose(String value) { return value; }
        public static String altered(String value) { return "child-" + value; }
    }
    public static class PrivateChild extends Parent<String> {
        public static int TOKEN = 8;
        private PrivateChild() {}
    }

    @BeforeAll
    static void initializePlatform() { TestPlatformInit.ensureInitialized(); }

    @Test
    void editedParentsRetainRenamesHiddenMethodsAndModuleLocalReferences() throws Exception {
        var declarations = List.of(new TypeReflector().reflect(Child.class), new TypeReflector().reflect(Parent.class));
        var edits = new ProbeModifyTypeEventJS(Map.of(declarations.getFirst().fqn, declarations.getFirst(),
                declarations.getLast().fqn, declarations.getLast()));
        edits.forClass(Child.class.getName()).renameClass("ProjectedChild").removeParam("altered", 0);
        edits.forClass(Parent.class.getName()).renameClass("StaticShadowingValue")
                .renameMethod("onlyParent", "renamedParent").changeReturnType("onlyParent", "boolean")
                .addStaticMethod("extra", StaticShadowingValue.class.getName());
        var backend = new TypeScriptProbeBackend();
        Path edited = generate("edited", declarations, backend);
        Files.writeString(edited.resolve("exercise.ts"), """
                import { $TypeScriptStaticShadowingTest$Child as Child, $ProjectedChild, $TypeScriptStaticShadowingTest$Parent as Parent } from 'java:com/tkisor/nekojs/probe/ir';
                const value: Parent<string> = new $ProjectedChild();
                const alias: Child = new $ProjectedChild();
                const renamed: boolean = Child.renamedParent();
                const imported: number = Child.extra().number;
                const editedParameters: string = Child.altered();
                // @ts-expect-error Editing a child signature must not revive the hidden parent signature.
                Child.altered('value');
                // @ts-expect-error Parent method renaming remains authoritative on the child value.
                Child.onlyParent();
                // @ts-expect-error Parent method edits preserve the replacement return type.
                const wrongReturn: string = Child.renamedParent();
                """);
        edits.forClass(Parent.class.getName()).hideMethod("onlyParent");
        Path hidden = generate("hidden", declarations, backend);
        Files.writeString(hidden.resolve("exercise.ts"), """
                import { $TypeScriptStaticShadowingTest$Child as Child } from 'java:com/tkisor/nekojs/probe/ir';
                const imported: number = Child.extra().number;
                // @ts-expect-error Hidden parent methods must not reappear through reflection.
                Child.renamedParent();
                """);
        Path reused = generate("reused", List.of(new TypeReflector().reflect(Child.class),
                new TypeReflector().reflect(Parent.class)), backend);
        String module = Files.readString(reused.resolve("@package/com/tkisor/nekojs/probe/ir/index.d.ts"));
        assertTrue(module.contains("static onlyParent(): string;"), module);
        assertFalse(module.contains("renamedParent"), module);
        assertFalse(module.contains("ProjectedChild"), module);
        assertFalse(module.contains("static extra"), module);
    }

    private Path generate(String scenario, List<TypeDecl> declarations, TypeScriptProbeBackend backend) throws Exception {
        Path output = Path.of("build/probe-ts-static-shadowing", scenario);
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        // Parent edits are supplied in IR even when the bounded primary roots contain only the child.
        var context = new ProbeContext.Of(snapshot, List.of(Child.class), config, NekoJSPaths.fromGameDir(output),
                "typescript", output, declarations);
        var files = backend.render(context);
        List<String> modules = new java.util.ArrayList<>();
        for (var entry : files.entrySet()) {
            if (!entry.getKey().startsWith("@package/") || !entry.getValue().contains("declare module ")) continue;
            Path path = output.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
            modules.add(entry.getKey());
        }
        modules.sort(String::compareTo);
        modules.add("exercise.ts");
        Files.writeString(output.resolve("tsconfig.json"), new com.google.gson.Gson().toJson(Map.of(
                "compilerOptions", Map.of("strict", true, "noEmit", true, "skipLibCheck", false,
                        "target", "ES2022", "module", "ESNext", "moduleResolution", "Bundler", "types", List.of(),
                        "baseUrl", ".", "paths", Map.of("java:*", List.of("./@package/*"))),
                "files", modules)));
        Files.writeString(output.resolve("exercise.ts"), "export {};\n");
        return output;
    }

    @Test
    void javaStaticHidingKeepsInstanceInheritanceAndInheritedStaticOverloads() throws Exception {
        List<Class<?>> classes = List.of(Child.class, PrivateChild.class, Parent.class);
        var declarations = classes.stream().map(type -> new TypeReflector().reflect(type)).toList();
        Path output = Path.of("build/probe-ts-static-shadowing/plain");
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                Map.of(), List.of());
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(getClass().getPackageName()), List.of(), List.of(), List.of(), 1, "SMART"));
        var context = new ProbeContext.Of(snapshot, classes, config, NekoJSPaths.fromGameDir(output),
                "typescript", output, declarations);
        Map<String, String> files = new TypeScriptProbeBackend().render(context);
        for (var entry : files.entrySet()) {
            if (!entry.getKey().startsWith("@package/")) continue;
            Path path = output.resolve(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
        }
        Files.writeString(output.resolve("tsconfig.json"), """
                {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[]},"files":["@package/com/tkisor/nekojs/probe/ir/index.d.ts","exercise.ts"]}
                """);
        Files.writeString(output.resolve("exercise.ts"), """
                import { $TypeScriptStaticShadowingTest$Parent as Parent, $TypeScriptStaticShadowingTest$Child as Child, $TypeScriptStaticShadowingTest$PrivateChild as PrivateChild } from 'java:com/tkisor/nekojs/probe/ir';
                const child = new Child();
                const parent: Parent<string> = child;
                parent.value = 'value';
                child.label = 'value';
                const label: string = child.label;
                const token: number = Child.TOKEN;
                const parentToken: string = Parent.TOKEN;
                const overloadNumber: number = Child.choose(7);
                const overloadString: string = Child.choose('value');
                const inheritedMethod: string = Child.onlyParent();
                // @ts-expect-error Hidden static fields keep their actual child type.
                const invalidToken: string = Child.TOKEN;
                // @ts-expect-error Parent overloads retain their parameter types.
                Child.choose(false);
                // @ts-expect-error Java static fields are not instance properties.
                child.TOKEN;
                // @ts-expect-error Graal's child class value does not expose parent static fields.
                Child.inherited;
                // @ts-expect-error Splitting static inheritance must not create a public constructor.
                new PrivateChild();
                """);
        String module = files.get("@package/com/tkisor/nekojs/probe/ir/index.d.ts");
        assertNotNull(module);
        assertTrue(module.contains("export interface $TypeScriptStaticShadowingTest$Child extends"), module);
        assertTrue(module.contains("static choose(value: number): number"), module);
        // Backend projection must not rewrite the shared declarations used by other languages.
        assertEquals(2, declarations.getFirst().methods.stream().filter(method -> method.name.equals("choose")).count());
        assertNotNull(declarations.getFirst().superType);
    }

    @Test
    void productionHostAccessExposesChildFieldsAndBothJavaStaticOverloads() {
        try (Context context = Context.newBuilder("js").allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowExperimentalOptions(true).option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .allowHostClassLookup(name -> name.equals(Child.class.getName()) || name.equals(Parent.class.getName())
                        || name.equals(PrivateChild.class.getName()))
                .allowCreateThread(false).allowCreateProcess(false).build()) {
            context.eval("js", "var Child = Java.type('" + Child.class.getName() + "'); var Parent = Java.type('"
                    + Parent.class.getName() + "'); var child = new Child();");
            assertEquals(7, context.eval("js", "Child.TOKEN").asInt());
            assertEquals("parent", context.eval("js", "Parent.TOKEN").asString());
            assertEquals("inherited", context.eval("js", "Parent.inherited").asString());
            assertEquals("undefined", context.eval("js", "typeof Child.inherited").asString());
            assertEquals(7, context.eval("js", "Child.choose(7)").asInt());
            assertEquals("value", context.eval("js", "Child.choose('value')").asString());
            assertEquals("parent", context.eval("js", "Child.onlyParent()").asString());
            assertEquals("label", context.eval("js", "child.label").asString());
            assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                    () -> context.eval("js", "new (Java.type('" + PrivateChild.class.getName() + "'))()"));
        }
    }
}
