package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedAdapterInputShapeTest {
    public static class Target<T> {}
    public static class Payload { public String getValue() { return "payload"; } }
    public static class LocalCollision {}
    private static final String PREFIX = "$NamedAdapterInputShapeTest$Target";

    private static AdapterCatalogEntry entry(List<AdapterInputShape> shapes) {
        return new AdapterCatalogEntry(Target.class, shapes, ConversionPrecedence.LOW, Optional.empty());
    }

    private static AdapterAliasGenerator.AdapterAlias alias(List<AdapterInputShape> shapes) {
        var generator = new AdapterAliasGenerator(new TypeAliasRegistry());
        generator.prepare(List.of(entry(shapes)), Set.of(Target.class.getName()));
        return generator.getAlias(Target.class.getName());
    }

    @Test void repeatedNamedPartsShareOneGenericHelperWithoutChangingRecursiveInputMeaning() {
        var body = object(Slot.req("child", self()), Slot.opt("children", arrayOf(self())), Slot.opt("id", registry("Item")));
        var part = named("Conditions", body);
        var generated = alias(List.of(self(), part, part));
        assertEquals(1, generated.namedAliases().size());
        var helper = generated.namedAliases().getFirst();
        assertEquals(PREFIX + "Conditions_", helper.aliasName());
        assertEquals("<T = any>", helper.typeParameters());
        assertEquals(PREFIX + "<T> | " + PREFIX + "Conditions_<T>", generated.union());
        assertTrue(helper.type().contains("child: " + PREFIX + "_<T>"), helper.type());
        assertTrue(helper.type().contains("children?: " + PREFIX + "_<T>[]"), helper.type());
        assertTrue(helper.type().contains("RegistryTypes.Item"), helper.type());
        assertTrue(generated.usesRegistry());
        String declarations = generated.declarations("    ");
        assertEquals(1, declarations.split("export type " + Pattern.quote(PREFIX + "Conditions_"), -1).length - 1);
        assertTrue(declarations.contains("export type " + PREFIX + "_<T = any> ="), declarations);
        assertThrows(UnsupportedOperationException.class, () -> generated.namedAliases().clear());
        var ordinary = alias(List.of(self(), string(), arrayOf(self())));
        assertEquals(PREFIX + "<T> | string | " + PREFIX + "_<T>[]", ordinary.union());
        assertTrue(ordinary.namedAliases().isEmpty());
        assertEquals("    export type " + PREFIX + "_<T = any> = " + ordinary.union() + ";\n", ordinary.declarations("    "));
        String opaque = "`first\nsecond`";
        var rawOrdinary = alias(List.of(raw(opaque)));
        assertEquals("    export type " + PREFIX + "_<T = any> = " + opaque + ";\n", rawOrdinary.declarations("    "));
        assertTrue(alias(List.of(named("Literal", raw(opaque)))).declarations("    ").contains(opaque));
    }

    @Test void invalidNamesAndConflictingRepeatedDefinitionsAreRejectedRatherThanOverwritten() {
        for (String invalid : List.of("", "bad-name", "2Start", "space name")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> named(invalid, string()));
            assertTrue(failure.getMessage().contains("NEKO-4063"), failure.getMessage());
        }
        assertThrows(RuntimeException.class, () -> named("Valid", null));
        var failure = assertThrows(RuntimeException.class, () -> alias(List.of(named("Same", string()), named("Same", number()))));
        assertTrue(failure.getMessage().contains("Same"), failure.getMessage());
    }

    @Test void ownerlessNamedShapesRenderInlineAndRetainTypedHostAndRegistryDependencies() {
        var body = object(Slot.req("pattern", host(Pattern.class)), Slot.opt("id", registry("Item")));
        var rendered = AdapterAliasGenerator.renderShape(named("Fields", body), new TypeAliasRegistry(), true);
        var plain = AdapterAliasGenerator.renderShape(body, new TypeAliasRegistry(), true);
        assertEquals(plain.type(), rendered.type());
        assertEquals(plain.referenceFqns(), rendered.referenceFqns());
        assertEquals(Set.of(Pattern.class.getName()), rendered.referenceFqns());
        assertTrue(rendered.usesRegistry());
        assertFalse(rendered.type().contains("Fields_"), rendered.type());
        var generated = alias(List.of(named("Fields", object(Slot.req("local", host(Payload.class)), Slot.req("external", host(Pattern.class))))));
        assertTrue(generated.referenceFqns().containsAll(Set.of(Payload.class.getName(), Pattern.class.getName())));
        assertTrue(generated.importFqns().contains(Pattern.class.getName()));
        assertFalse(generated.importFqns().contains(Payload.class.getName()), "Same-package hosts need closure but no cross-package import");
    }

    @Test void backendReservesLocalHelperNamesForImportsAndRejectsActualSamePackageCollisions(@TempDir Path temp) throws Exception {
        TestPlatformInit.ensureInitialized();
        var target = new TypeReflector().reflect(Target.class);
        Class<?> importedType = com.tkisor.nekojs.probe.fixtures.named.NamedAdapterInputShapeTest.TargetConditions_.class;
        var imported = new TypeReflector().reflect(importedType);
        var adapter = entry(List.of(self(), named("Conditions", object(Slot.req("pattern", host(importedType))))));
        var backend = new TypeScriptProbeBackend();
        var files = backend.render(context(temp, List.of(adapter), List.of(target, imported)));
        String module = files.get("@package/com/tkisor/nekojs/probe/backend/typescript/index.d.ts");
        assertNotNull(module);
        assertTrue(module.contains("export type " + PREFIX + "Conditions_<T = any>"), module);
        assertTrue(module.contains(" as "), "An imported class must not shadow the local helper: " + module);
        assertFalse(Pattern.compile("pattern: " + Pattern.quote(PREFIX + "Conditions_") + "(?![A-Za-z0-9_$])").matcher(module).find(), module);
        var local = new TypeDecl(TypeDecl.Kind.CLASS, LocalCollision.class, LocalCollision.class.getName());
        local.renameTo = "NamedAdapterInputShapeTest$TargetConditions_";
        var failure = assertThrows(RuntimeException.class, () -> backend.render(context(temp, List.of(adapter), List.of(target, imported, local))));
        assertTrue(failure.getMessage().contains("Package generation failed"), failure.getMessage());
        var aliases = new TypeAliasRegistry();
        var adapters = new AdapterAliasGenerator(aliases);
        var renderer = new com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer(aliases);
        renderer.setClassDeclarations(Map.of(target.fqn, target, imported.fqn, imported, local.fqn, local));
        var index = new IndexFileGenerator(renderer, adapters);
        Set<String> fqns = Set.of(target.fqn, imported.fqn, local.fqn);
        adapters.prepare(List.of(adapter), fqns, List.of(target, imported, local));
        for (TypeDecl declaration : List.of(target, imported, local)) index.predeclareClass(declaration.fqn, declaration, Set.of());
        String pkg = Target.class.getPackageName();
        var conflict = assertThrows(IllegalArgumentException.class, () -> index.generate(pkg,
                List.of(target.fqn.substring(pkg.length() + 1), local.fqn.substring(pkg.length() + 1)), List.of(), fqns));
        assertTrue(conflict.getMessage().contains("NEKO-4063"), conflict.getMessage());
        assertTrue(conflict.getMessage().contains(PREFIX + "Conditions_"), conflict.getMessage());
    }

    @Test void pythonBackendUnwrapsNamedPartsWithoutChangingItsEmittedModule(@TempDir Path temp) throws Exception {
        var body = union(string(), host(Payload.class), callable(List.of(Slot.req("payload", host(Payload.class))), bool()));
        String plain = python(temp.resolve("plain"), List.of(self(), body));
        String named = python(temp.resolve("named"), List.of(self(), named("Expression", body)));
        assertEquals(plain, named);
        assertTrue(named.contains("Callable[[NamedAdapterInputShapeTest_Payload], bool]"), named);
        assertFalse(named.contains("TargetExpression_"), named);
    }

    private static ProbeContext context(Path temp, List<AdapterCatalogEntry> adapters, List<TypeDecl> declarations) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), adapters, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
        var config = new ProbeConfig(true, ".probe", new ProbeConfig.ScanConfig(List.of("com.tkisor.nekojs", "java.util.regex"), List.of(), List.of(), List.of(), 1, "SMART"));
        return new ProbeContext.Of(snapshot, declarations.stream().<Class<?>>map(declaration -> declaration.sourceClass).toList(), config,
                NekoJSPaths.fromGameDir(temp), "typescript", temp, declarations);
    }

    private static String python(Path output, List<AdapterInputShape> shapes) throws Exception {
        Files.createDirectories(output);
        var reflector = new TypeReflector();
        ProbeContext base = context(output, List.of(entry(shapes)), List.of(reflector.reflect(Target.class), reflector.reflect(Payload.class)));
        var probe = new ProbeContext.Of(base.snapshot(), base.collectedClasses(), base.config(), base.paths(), "python", output, base.ir());
        var result = new PythonProbeBackend().generate(probe);
        assertTrue(result.success(), result.message());
        return Files.readString(output.resolve("nekojs/_java/com/tkisor/nekojs/probe/backend/typescript/__init__.pyi"));
    }
}
