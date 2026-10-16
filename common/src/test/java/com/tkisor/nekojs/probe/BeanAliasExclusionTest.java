package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer;
import com.tkisor.nekojs.probe.backend.python.PythonClassRenderer;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BeanAliasExclusionTest {
    public static class Parent {
        public boolean isAttackable() { return true; }
        public boolean isOther() { return false; }
    }
    public static class Child extends Parent {
        public boolean attackable() { return false; }
    }
    public static class Independent {
        public boolean isAttackable() { return true; }
    }
    public static class OwnGetter extends Parent {
        @Override
        public boolean isAttackable() { return false; }
    }
    public static class Bounded<T extends Parent> { public T entity; }

    private static final List<Class<?>> TYPES = List.of(Parent.class, Child.class, Independent.class,
            OwnGetter.class, Bounded.class);
    private static final BeanAliasExclusionCatalogEntry RULE = new BeanAliasExclusionCatalogEntry(
            Parent.class, "isAttackable", "attackable");
    private static final String MODULE = "@package/com/tkisor/nekojs/probe/index.d.ts";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void exactRuleRetainsRealMethodsOtherAliasesAndSharedIrAcrossBackendReuse(@TempDir Path temp) {
        List<TypeDecl> ir = TYPES.stream().map(type -> new TypeReflector().reflect(type)).toList();
        var python = new PythonClassRenderer(new ApiTypeRefPyRenderer(java.util.Set.of()));
        List<String> beforePython = ir.stream().map(python::render).toList();
        var backend = new TypeScriptProbeBackend();
        String before = backend.render(context(List.of(), ir, temp)).get(MODULE);
        String after = backend.render(context(List.of(RULE), ir, temp)).get(MODULE);
        int start = before.indexOf("export class $BeanAliasExclusionTest$Parent");
        int alias = before.indexOf("        get attackable(): boolean;\n", start);
        assertTrue(alias > start, before);
        assertEquals(before.substring(0, alias) + before.substring(alias + "        get attackable(): boolean;\n".length()), after);
        assertTrue(after.contains("isAttackable(): boolean;"), after);
        assertTrue(after.contains("attackable(): boolean;"), after);
        assertEquals(beforePython, ir.stream().map(python::render).toList());
        assertTrue(ir.getFirst().methods.stream().anyMatch(method -> method.isGetter
                && method.name.equals("isAttackable") && !method.hidden));
        assertEquals(before, backend.render(context(List.of(), ir, temp)).get(MODULE));
        assertEquals(before, backend.render(context(List.of(new BeanAliasExclusionCatalogEntry(
                Parent.class, "getAttackable", "attackable")), ir, temp)).get(MODULE));
        assertEquals(before, backend.render(context(List.of(new BeanAliasExclusionCatalogEntry(
                Parent.class, "isAttackable", "other")), ir, temp)).get(MODULE));
    }

    @Test
    void generatedStrictProjectRetainsTheBoundAndExactMigration(@TempDir Path temp) throws Exception {
        var backend = new TypeScriptProbeBackend();
        for (String variant : List.of("before", "after")) {
            Path output = Path.of("build/probe-ts-bean-exclusion", variant);
            Files.createDirectories(output);
            var files = backend.render(context(variant.equals("after") ? List.of(RULE) : List.of(), null, temp));
            for (var file : files.entrySet()) {
                Path destination = output.resolve(file.getKey());
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, file.getValue());
            }
            Files.writeString(output.resolve("tsconfig.json"), """
                    {"compilerOptions":{"strict":true,"noEmit":true,"skipLibCheck":false,"target":"ES2022","module":"ESNext","moduleResolution":"Bundler","types":[],"baseUrl":".","paths":{"java:*": ["@package/*"]}},"include":["@package/**/*.d.ts","exercise.ts"]}
                    """);
            String exercise = """
                    import type { $BeanAliasExclusionTest$Parent as Parent, $BeanAliasExclusionTest$Child as Child,
                      $BeanAliasExclusionTest$Independent as Independent, $BeanAliasExclusionTest$OwnGetter as OwnGetter,
                      $BeanAliasExclusionTest$Bounded as Bounded } from 'java:com/tkisor/nekojs/probe';
                    declare const parent: Parent;
                    declare const child: Child;
                    declare const independent: Independent;
                    declare const own: OwnGetter;
                    const bounded: Bounded<Child> = {entity: child};
                    const base: Parent = child;
                    const result: boolean = parent.isAttackable();
                    const childResult: boolean = child.attackable();
                    const independentResult: boolean = independent.attackable;
                    const ownResult: boolean = own.attackable;
                    const unrelated: boolean = parent.other;
                    // @ts-expect-error The declared bound remains precise.
                    type Invalid = Bounded<number>;
                    """;
            if (variant.equals("after")) exercise += """
                    // @ts-expect-error Only the approved Parent property is omitted.
                    parent.attackable;
                    """;
            Files.writeString(output.resolve("exercise.ts"), exercise);
        }
        Files.writeString(Path.of("build/probe-ts-bean-exclusion/tsconfig.json"), """
                {"extends":"./after/tsconfig.json"}
                """);
    }

    @Test
    void productionHostAccessStillExposesTheOriginalBeanAndRealMethods() {
        try (Context context = Context.newBuilder("js").allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false).option("js.nashorn-compat", "true").build()) {
            assertTrue(context.eval("js", "var Parent = Java.type('" + Parent.class.getName()
                    + "'); var Child = Java.type('" + Child.class.getName()
                    + "'); var Independent = Java.type('" + Independent.class.getName()
                    + "'); var parent = new Parent(); var child = new Child();"
                    + "parent.attackable === true && parent.isAttackable() === true"
                    + " && child.attackable() === false && child.isAttackable() === true"
                    + " && new Independent().attackable === true").asBoolean());
        }
    }

    private static ProbeContext context(List<BeanAliasExclusionCatalogEntry> exclusions,
                                        List<TypeDecl> ir, Path directory) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null, Map.of(), List.of(), exclusions);
        var paths = NekoJSPaths.fromGameDir(directory);
        var config = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(), List.of(), 5, "FULL"));
        return new ProbeContext.Of(snapshot, TYPES, config, paths, "typescript",
                paths.gameDir().resolve(".neko_probe/typescript"), ir);
    }
}
