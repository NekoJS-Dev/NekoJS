package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.testfixture.ClassDeclarationFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enum literal input aliases in {@link IndexFileGenerator} ({@code $Enum_ = $Enum | "A" | ...}):
 * declaring the alias (constant order matching renderEnum), widening parameters (lazy alias
 * lookup for enum FQNs in {@link TypeAliasRegistry}, input-only), and appending the cross-package
 * import (the same import mechanism as the adapter alias {@code $Foo_}).
 */
class IndexFileGeneratorTest {

    public static class ExecutorUser {
        public void schedule(java.util.concurrent.Executor executor) {}
    }

    public static class ScalarUser {
        public void accept(String label, Integer count) {}
    }

    @Test
    void scalarInputsDoNotImportJavaWrappers() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        gen.predeclareClass(ScalarUser.class.getName(), new TypeReflector().reflect(ScalarUser.class), Set.of());
        String output = gen.generate(ScalarUser.class.getPackageName(),
                List.of("IndexFileGeneratorTest$ScalarUser"), List.of(), Set.of(ScalarUser.class.getName()));
        assertTrue(output.contains("accept(label: string, count: number)"), output);
        assertFalse(output.contains("java:java/lang"), output);
    }

    @Test
    void contextualCallbackArgumentsImportTheirHostTypes() throws java.io.IOException {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        IndexFileGenerator gen = generator(registry);
        Set<String> classes = Set.of(ExecutorUser.class.getName(),
                java.util.concurrent.Executor.class.getName(), Runnable.class.getName());
        gen.prepareFunctionalInterfaceAliases(classes, Set.of());
        for (Class<?> type : List.of(ExecutorUser.class, java.util.concurrent.Executor.class, Runnable.class)) {
            gen.predeclareClass(type.getName(), new TypeReflector().reflect(type), Set.of());
        }
        String output = gen.generate(ExecutorUser.class.getPackageName(),
                List.of("IndexFileGeneratorTest$ExecutorUser"), List.of(), classes);
        assertTrue(output.contains("schedule(executor: $Executor_<$Runnable>)"), output);
        assertTrue(output.contains("from \"java:java/lang\";"), output);
        assertTrue(output.contains("import { $Runnable"), output);
        String capture = System.getenv("NEKO_SAM_MODULE_CAPTURE");
        if (capture != null) {
            for (Class<?> type : List.of(ExecutorUser.class, java.util.concurrent.Executor.class, Runnable.class)) {
                String packageName = type.getPackageName();
                String className = type.getName().substring(packageName.length() + 1);
                java.nio.file.Path path = java.nio.file.Path.of(capture, "@package",
                        packageName.replace('.', '/'), "index.d.ts");
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path,
                        gen.generate(packageName, List.of(className), List.of(), classes));
            }
        }
    }

    public static class SignatureOnly<T extends ClassDeclarationFixtures.Imported> {
        public SignatureOnly(ClassDeclarationFixtures.Adapted input) {}
    }

    @Test
    void classBoundsAndConstructorInputsRetainTheirImports() {
        var declaration = new TypeReflector().reflect(SignatureOnly.class);
        var imports = generator(new TypeAliasRegistry()).collectImportsFromIr(declaration,
                SignatureOnly.class.getPackageName());
        assertTrue(imports.contains(ClassDeclarationFixtures.Imported.class.getName()),
                imports.toString());
        assertTrue(imports.contains(ClassDeclarationFixtures.Adapted.class.getName()),
                imports.toString());
    }

    private static IndexFileGenerator generator(TypeAliasRegistry registry) {
        return new IndexFileGenerator(new TypeScriptClassRenderer(registry),
                new AdapterAliasGenerator(registry));
    }

    @Test
    void enumAliasDeclarationEmittedAfterEnumClass() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        TypeDecl color = new TypeReflector().reflect(ProbeTestColor.class);
        gen.predeclareClass(color.fqn, color, Set.of());

        String out = gen.generate("com.tkisor.nekojs.probe", List.of("ProbeTestColor"),
                List.of(), Set.of(color.fqn));
        assertTrue(out.contains("export class $ProbeTestColor {"), out);
        // Constant order follows renderEnum's static constant order (TypeReflector already sorts by name
        // stably: BLUE, GREEN, RED)
        assertTrue(out.contains("export type $ProbeTestColor_ = $ProbeTestColor | \"BLUE\" | \"GREEN\" | \"RED\";"),
                "enum literal alias declaration missing: " + out);
    }

    @Test
    void enumParamWidenedToEnumAlias() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        TypeDecl user = new TypeReflector().reflect(ProbeTestColorUser.class);
        gen.predeclareClass(user.fqn, user, Set.of());

        String out = gen.generate("com.tkisor.nekojs.probe", List.of("ProbeTestColorUser"),
                List.of(), Set.of(user.fqn));
        // The enum parameter widens to $ProbeTestColor_; the enum is in the same package, so the alias
        // declaration lives in its module and needs no import
        assertTrue(out.contains(": $ProbeTestColor_)"), "enum param should widen to alias: " + out);
        assertFalse(out.contains(": $ProbeTestColor)"), "unwidened enum param leaked: " + out);
    }

    @Test
    void crossPackageEnumAliasImportedAlongsideClass() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        TypeDecl color = new TypeReflector().reflect(ProbeTestColor.class);
        gen.predeclareClass(color.fqn, color, Set.of());

        // Synthetic cross-package class: paint(ProbeTestColor) widens to $ProbeTestColor_, so the import must
        // bring the alias along (same mechanism as the adapter aliases)
        TypeDecl user = new TypeDecl(TypeDecl.Kind.CLASS, null, "other.pkg.ColorUser");
        MethodDecl paint = new MethodDecl("paint");
        paint.returnType = TypeSlot.of(void.class, ApiTypeRef.voidType());
        paint.params.add(new MethodDecl.MethodParam("color",
                TypeSlot.of(ProbeTestColor.class,
                        ApiTypeRef.symbol(new ApiSymbolId("java", ProbeTestColor.class.getName()))),
                false));
        user.methods.add(paint);
        gen.predeclareClass(user.fqn, user, Set.of());

        String out = gen.generate("other.pkg", List.of("ColorUser"), List.of(),
                Set.of(user.fqn, color.fqn));
        assertTrue(out.contains("paint(color: $ProbeTestColor_)"), out);
        assertTrue(out.contains("import { $ProbeTestColor, $ProbeTestColor_ } from \"java:com/tkisor/nekojs/probe\";"),
                "enum alias must be imported cross-package: " + out);
    }

    @Test
    void registryResolvesEnumAliasesLazilyInputOnly() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        // Lazy lookup: every enum FQN has an input alias $<TS name>_ (a nested enum is $Parent$Child_)
        assertTrue(registry.hasAlias("java.time.DayOfWeek"));
        assertEquals("$DayOfWeek_", registry.getAlias("java.time.DayOfWeek"));
        assertEquals("$Thread$State_", registry.getAlias("java.lang.Thread$State"));
        // Not an enum and no explicit alias means no alias, so nothing is widened
        assertFalse(registry.hasAlias("java.util.ArrayList"));

        // Alias use: only input (parameter) positions widen; return values keep the full type through the renderer
        assertEquals("$DayOfWeek_", com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer.renderTypeRef(
                com.tkisor.nekojs.probe.ir.TypeReflector.toRef(java.time.DayOfWeek.class), registry, true));
        assertEquals("$DayOfWeek", com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer.renderTypeRef(
                com.tkisor.nekojs.probe.ir.TypeReflector.toRef(java.time.DayOfWeek.class), registry, false));

        // An explicitly registered alias wins over the lazy enum alias (both come out of the same registry)
        registry.registerClassAlias("java.time.DayOfWeek", "string");
        assertEquals("string", registry.getAlias("java.time.DayOfWeek"));
    }

    @Test
    void functionalAliasRetainsHostInterfaceType() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        IndexFileGenerator gen = generator(registry);
        TypeDecl function = new TypeReflector().reflect(java.util.function.Function.class);
        gen.prepareFunctionalInterfaceAliases(Set.of(function.fqn), Set.of());
        gen.predeclareClass(function.fqn, function, Set.of());

        String out = gen.generate("java.util.function", List.of("Function"), List.of(), Set.of(function.fqn));

        assertTrue(out.contains("export type $Function_<Host0 = any, Host1 = any, CallbackArg0 = Host0, CallbackResult = Host1> ="), out);
        assertTrue(out.contains("=> CallbackResult"), out);
        assertTrue(out.contains("| ($Function<Host0, Host1> & { readonly [Symbol.hasInstance]?: never })"),
                "the callback alias must also accept the Java host interface instance: " + out);
    }
}

/** Enum fixture: goes through the real reflection path, where constants sort by name to BLUE, GREEN, RED. */
enum ProbeTestColor {
    RED, GREEN, BLUE
}

/** Enum parameter fixture: the paint parameter should widen to $ProbeTestColor_. */
class ProbeTestColorUser {
    public void paint(ProbeTestColor color) {}
}
