package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptSameNameHeritageTest {
    private static final Class<?> BASE = com.tkisor.nekojs.probe.testfixture.samebase.PersistentData.class;
    private static final Class<?> CHILD = com.tkisor.nekojs.probe.testfixture.samechild.PersistentData.class;

    @Test
    void privateNamesAvoidExistingGetterImportsWithoutChangingTheirText() throws Exception {
        var aliases = new TypeAliasRegistry();
        var renderer = new TypeScriptClassRenderer(aliases);
        var generator = new IndexFileGenerator(renderer, new AdapterAliasGenerator(aliases));
        Class<?> usage = com.tkisor.nekojs.probe.testfixture.samechild.GetterUsage.class;
        Class<?> other = com.tkisor.nekojs.probe.testfixture.sameother.PersistentData.class;
        String module = "java:" + other.getPackageName().replace('.', '/');
        String namedImport = "import { $PersistentData as $NekoImport$PersistentData } from \"" + module + "\";";
        String escapedImport = "import { $PersistentData as " + "\\" + "u0024NekoImport$PersistentData1 } from \"" + module + "\";";
        String namespaceImport = "import * as $NekoImport$PersistentData2 from \"" + module + "\";";
        renderer.overrideGetter(usage, "named", "$NekoImport$PersistentData", namedImport);
        renderer.overrideGetter(usage, "escaped", "$NekoImport$PersistentData1", escapedImport);
        renderer.overrideGetter(usage, "namespace", "$NekoImport$PersistentData2.$PersistentData", namespaceImport);
        List<Class<?>> classes = List.of(BASE, CHILD, other, usage);
        Set<String> fqns = classes.stream().map(Class::getName).collect(java.util.stream.Collectors.toSet());
        for (Class<?> type : classes) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        String child = generator.generate(CHILD.getPackageName(), List.of("PersistentData", "GetterUsage"), List.of(), fqns);
        Path output = Path.of("build/probe-ts-same-name-heritage-getter-imports");
        Files.createDirectories(output);
        Files.writeString(output.resolve("child.d.ts"), child);
        for (Class<?> type : List.of(BASE, other)) {
            Files.writeString(output.resolve(type == BASE ? "base.d.ts" : "other.d.ts"),
                    generator.generate(type.getPackageName(), List.of("PersistentData"), List.of(), fqns));
        }
        assertTrue(child.contains(namedImport) && child.contains(escapedImport) && child.contains(namespaceImport), child);
        assertTrue(child.contains("get named(): $NekoImport$PersistentData;"), child);
        assertTrue(child.contains("get escaped(): $NekoImport$PersistentData1;"), child);
        assertTrue(child.contains("get namespace(): $NekoImport$PersistentData2.$PersistentData;"), child);
        assertFalse(child.contains("extends $NekoImport$PersistentData<string>"), child);
        assertFalse(child.contains("extends $NekoImport$PersistentData1<string>"), child);
        assertFalse(child.contains("extends $NekoImport$PersistentData2<string>"), child);
    }

    @Test
    void moduleReferencesKeepImportedHomonymsAndLocalDeclarationsDistinct() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> classes = List.of(BASE, CHILD,
                com.tkisor.nekojs.probe.testfixture.sameother.PersistentData.class,
                com.tkisor.nekojs.probe.testfixture.samechild.Usage.class);
        Set<String> fqns = classes.stream().map(Class::getName).collect(java.util.stream.Collectors.toSet());
        for (Class<?> type : classes) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        String child = generator.generate(CHILD.getPackageName(), List.of("PersistentData", "Usage"), List.of(), fqns);
        Path output = Path.of("build/probe-ts-same-name-heritage-multiple");
        Files.createDirectories(output);
        Files.writeString(output.resolve("child.d.ts"), child);
        for (Class<?> type : List.of(BASE, classes.get(2))) {
            Files.writeString(output.resolve(type == BASE ? "base.d.ts" : "other.d.ts"),
                    generator.generate(type.getPackageName(), List.of("PersistentData"), List.of(), fqns));
        }
        assertFalse(child.contains("export class $PersistentData extends $PersistentData<"), child);
        assertTrue(child.contains("self: $PersistentData;"), child);
        assertFalse(child.contains("base: $PersistentData<string>;"), child);
        assertFalse(child.contains("other: $PersistentData;"), child);
        assertFalse(child.contains("acceptBase(value: $PersistentData<string>)"), child);
    }

    @Test
    void adapterInputsKeepImportedAliasesSeparateFromTheLocalAlias() throws Exception {
        Class<?> base = com.tkisor.nekojs.probe.testfixture.samebase.Token.class;
        Class<?> local = com.tkisor.nekojs.probe.testfixture.samechild.Token.class;
        var aliases = new TypeAliasRegistry();
        var adapters = new AdapterAliasGenerator(aliases);
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), adapters);
        Set<String> fqns = Set.of(base.getName(), local.getName());
        adapters.prepare(List.of(new AdapterCatalogEntry(base, List.of(AdapterInputShape.self()),
                        ConversionPrecedence.LOWEST, Optional.empty()),
                new AdapterCatalogEntry(local, List.of(AdapterInputShape.self(), AdapterInputShape.host(base)),
                        ConversionPrecedence.LOWEST, Optional.empty())), fqns);
        for (Class<?> type : List.of(base, local)) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        String child = generator.generate(local.getPackageName(), List.of("Token"), List.of(), fqns);
        Path output = Path.of("build/probe-ts-same-name-heritage-adapter");
        Files.createDirectories(output);
        Files.writeString(output.resolve("child.d.ts"), child);
        Files.writeString(output.resolve("base.d.ts"), generator.generate(base.getPackageName(), List.of("Token"), List.of(), fqns));
        assertTrue(child.contains("export type $Token_ = $Token | "), child);
        assertFalse(child.contains("acceptBase(value: $Token_)"), child);
        assertFalse(child.contains("export type $Token_ = $Token | $Token;"), child);
    }

    @Test
    void authoredBodiesKeepTheirTextAndModuleNamesDoNotDependOnClassOrder() {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        String authored = "export class $PersistentData { authored(): string; "
                + "base(): import(\"java:com/tkisor/nekojs/probe/testfixture/samebase\").$PersistentData<string>; }";
        generator.predeclareClassDeclaration(CHILD.getName(), authored, Set.of(BASE.getName()), null);
        Class<?> usage = com.tkisor.nekojs.probe.testfixture.samechild.Usage.class;
        generator.predeclareClass(usage.getName(), new TypeReflector().reflect(usage), Set.of());
        Set<String> all = Set.of(BASE.getName(), CHILD.getName(), usage.getName());
        String first = generator.generate(CHILD.getPackageName(), List.of("PersistentData", "Usage"), List.of(), all);
        String reversed = generator.generate(CHILD.getPackageName(), List.of("Usage", "PersistentData"), List.of(), all);
        assertTrue(first.contains(authored), first);
        assertTrue(first.contains("import { $PersistentData as "), first);
        assertFalse(first.contains("import { $PersistentData, "), first);
        assertEquals(first.substring(0, first.indexOf("declare module")), reversed.substring(0, reversed.indexOf("declare module")));
        generator.clearCaches();
        generator.predeclareClass(CHILD.getName(), new TypeReflector().reflect(CHILD), Set.of());
        String reflected = generator.generate(CHILD.getPackageName(), List.of("PersistentData"), List.of(), all);
        assertFalse(reflected.contains("authored()"), reflected);
        assertFalse(reflected.contains("import { $PersistentData, "), reflected);
    }

    @Test
    void functionalAliasesKeepCallbackArgumentsResultsAndHeritageInTheSameView() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        List<Class<?>> classes = List.of(com.tkisor.nekojs.probe.testfixture.samebase.Callback.class,
                com.tkisor.nekojs.probe.testfixture.samebase.Token.class,
                com.tkisor.nekojs.probe.testfixture.samechild.Callback.class,
                com.tkisor.nekojs.probe.testfixture.samechild.Token.class,
                com.tkisor.nekojs.probe.testfixture.samechild.CallbackUser.class);
        Set<String> all = classes.stream().map(Class::getName).collect(java.util.stream.Collectors.toSet());
        generator.prepareFunctionalInterfaceAliases(all, Set.of());
        for (Class<?> type : classes) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        Path output = Path.of("build/probe-ts-same-name-heritage-functional");
        Files.createDirectories(output);
        String base = generator.generate(classes.get(0).getPackageName(), List.of("Callback", "Token"), List.of(), all);
        String local = generator.generate(classes.get(2).getPackageName(), List.of("Callback", "Token", "CallbackUser"), List.of(), all);
        Files.writeString(output.resolve("base.d.ts"), base);
        Files.writeString(output.resolve("child.d.ts"), local);
        assertFalse(local.contains("export interface $Callback extends $Callback"), local);
        assertFalse(local.contains("invokeBase(callback: $Callback_"), local);
        assertFalse(local.contains("CallbackArg0 = $Token"), local);
        assertNull(aliases.getRegisteredAlias(classes.get(0).getName()));
        assertEquals("$Callback_", aliases.getFunctionalInterfaceAlias(classes.get(0).getName()).aliasName());
    }

    @Test
    void editedTypeSlotsRetainTheirQualifiedTargetsInTheModuleView() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases), new AdapterAliasGenerator(aliases));
        Class<?> usage = com.tkisor.nekojs.probe.testfixture.samechild.Usage.class;
        Class<?> other = com.tkisor.nekojs.probe.testfixture.sameother.PersistentData.class;
        var declaration = new TypeReflector().reflect(usage);
        var edited = declaration.methods.stream().filter(method -> method.name.equals("acceptBase")).findFirst().orElseThrow();
        edited.returnType.ref = TypeReflector.toRef(other);
        edited.returnType.overridden = true;
        edited.params.getFirst().type.ref = TypeReflector.toRef(other);
        edited.params.getFirst().type.overridden = true;
        declaration.mutated = true;
        generator.predeclareClass(usage.getName(), declaration, Set.of(other.getName()));
        for (Class<?> type : List.of(BASE, CHILD, other)) generator.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        Set<String> all = Set.of(usage.getName(), BASE.getName(), CHILD.getName(), other.getName());
        Path output = Path.of("build/probe-ts-same-name-heritage-edited");
        Files.createDirectories(output);
        String child = generator.generate(CHILD.getPackageName(), List.of("PersistentData", "Usage"), List.of(), all);
        Files.writeString(output.resolve("child.d.ts"), child);
        for (Class<?> type : List.of(BASE, other)) Files.writeString(output.resolve(type == BASE ? "base.d.ts" : "other.d.ts"),
                generator.generate(type.getPackageName(), List.of("PersistentData"), List.of(), all));
        assertFalse(child.contains("acceptBase(value: $PersistentData<string>):"), child);
    }

    @Test
    void actualReflectionRetainsDistinctBaseIdentityAndCompleteModules() throws Exception {
        var aliases = new TypeAliasRegistry();
        var generator = new IndexFileGenerator(new TypeScriptClassRenderer(aliases),
                new AdapterAliasGenerator(aliases));
        var reflector = new TypeReflector();
        var child = reflector.reflect(CHILD);
        assertNotEquals(BASE.getName(), CHILD.getName());
        assertEquals(BASE, CHILD.getSuperclass());
        assertEquals(CHILD.getGenericSuperclass(), child.superType.sourceType);
        assertTrue(generator.collectImportsFromIr(child, CHILD.getPackageName()).contains(BASE.getName()));
        generator.predeclareClass(BASE.getName(), reflector.reflect(BASE), Set.of());
        generator.predeclareClass(CHILD.getName(), child, Set.of());
        Path output = Path.of("build/probe-ts-same-name-heritage");
        Files.createDirectories(output);
        for (Class<?> type : List.of(BASE, CHILD)) {
            String declaration = generator.generate(type.getPackageName(), List.of(type.getSimpleName()),
                    List.of(), Set.of(BASE.getName(), CHILD.getName()));
            Files.writeString(output.resolve(type == BASE ? "base.d.ts" : "child.d.ts"), declaration);
            assertTrue(declaration.contains("export class $PersistentData"), declaration);
        }
    }

    @Test
    void actualHostInheritanceAndBaseParameterKeepTheirCapabilities() {
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(new NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(new ClassFilter(SandboxConfig.defaultConfig()))
                .allowCreateProcess(false)
                .option("js.nashorn-compat", "true")
                .build()) {
            assertTrue(context.eval("js", "var Base = Java.type('" + BASE.getName()
                    + "'); var Child = Java.type('" + CHILD.getName()
                    + "'); var base = new Base('base'); var child = new Child('child');"
                    + " child.baseValue() === 'child' && child.acceptBase(base) === 'base'"
                    + " && child.acceptBase(child) === 'child' && child instanceof Base").asBoolean());
        }
    }
}
