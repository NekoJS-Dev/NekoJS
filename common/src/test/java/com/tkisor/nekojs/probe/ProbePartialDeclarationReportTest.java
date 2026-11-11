package com.tkisor.nekojs.probe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class ProbePartialDeclarationReportTest {
    private static final String OMISSION = "Entire public method set unavailable: java.lang.NoClassDefFoundError: optional/Unavailable";
    private static final String PACKAGE = "probe.partialfixture";
    private static final String ROOT = PACKAGE + ".Root";
    private static final String PARTIAL = PACKAGE + ".Partial";
    private static final String MISSING = "optional.Unavailable";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void sharedIrPartialRemainsEmittedAndMakesTypeScriptReportIncomplete(@TempDir Path directory) throws Exception {
        TypeDecl declaration = partialDeclaration();
        ProbeContext context = context(directory, "typescript", List.of(Retained.class), List.of(declaration), List.of());
        ProbeBackend.GenerateResult result = new TypeScriptProbeBackend().generate(context);
        assertTrue(result.success(), result::message);
        assertPartialWarning(result, Retained.class.getName());
        JsonObject report = report(context);
        assertFalse(report.get("complete").getAsBoolean(), report::toString);
        assertTrue(report.getAsJsonObject("unavailable").isEmpty(), report::toString);
        assertEquals(OMISSION, report.getAsJsonObject("incompleteMembers").getAsJsonArray(
                Retained.class.getName()).get(0).getAsString());
        String declarationText = Files.readString(context.languageDir().resolve(
                "@package/com/tkisor/nekojs/probe/index.d.ts"));
        assertTrue(declarationText.contains("export class $ProbePartialDeclarationReportTest$Retained"), declarationText);
        assertTrue(declarationText.contains("retainedField"), declarationText);
    }

    @Test
    void pythonAggregatesOnePartialWarningPerTypeAndClearsWarningsNextRun(@TempDir Path directory) throws Exception {
        PythonProbeBackend backend = new PythonProbeBackend();
        ProbeContext partial = context(directory, "python", List.of(Retained.class), List.of(partialDeclaration()), List.of());
        ProbeBackend.GenerateResult result = backend.generate(partial);
        assertTrue(result.success(), result::message);
        assertPartialWarning(result, Retained.class.getName());
        String declarationText = Files.readString(partial.languageDir().resolve(
                "nekojs/_java/com/tkisor/nekojs/probe/__init__.pyi"));
        assertTrue(declarationText.contains("retainedField"), declarationText);
        ProbeBackend.GenerateResult healthy = backend.generate(context(directory, "python", List.of(Retained.class),
                List.of(new TypeReflector().reflect(Retained.class)), List.of()));
        assertTrue(healthy.success(), healthy::message);
        assertTrue(healthy.warnings().stream().noneMatch(warning -> warning.contains("NEKO-4066")),
                healthy.warnings()::toString);
    }

    @Test
    void closureOnlyNativePartialIsNotReportedAsUnavailable(@TempDir Path directory) throws Exception {
        ClassLoader loader = fixtureLoader();
        Class<?> root = loader.loadClass(ROOT);
        Class<?> partial = loader.loadClass(PARTIAL);
        assertThrows(NoClassDefFoundError.class, partial::getMethods);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            ProbeContext context = context(directory, "typescript", List.of(root),
                    List.of(new TypeReflector().reflect(root)), List.of());
            ProbeBackend.GenerateResult result = new TypeScriptProbeBackend().generate(context);
            assertTrue(result.success(), result::message);
            assertPartialWarning(result, PARTIAL);
            JsonObject report = report(context);
            assertFalse(report.get("complete").getAsBoolean(), report::toString);
            assertFalse(report.getAsJsonObject("unavailable").has(PARTIAL), report::toString);
            assertTrue(report.getAsJsonObject("incompleteMembers").has(PARTIAL), report::toString);
            String text = Files.readString(context.languageDir().resolve("@package/probe/partialfixture/index.d.ts"));
            assertTrue(text.contains("export class $Partial"), text);
            assertTrue(text.contains("retainedField"), text);
            assertFalse(text.contains("Unavailable"), text);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    void authoredReplacementDoesNotInheritOmissionsFromUnusedNativeDeclaration(@TempDir Path directory) throws Exception {
        var replacement = ClassDeclarationCatalogEntry.of(Retained.class,
                "export class $ProbePartialDeclarationReportTest$Retained { authored(): void; }", Set.of());
        ProbeContext context = context(directory, "typescript", List.of(Retained.class),
                List.of(partialDeclaration()), List.of(replacement));
        ProbeBackend.GenerateResult result = new TypeScriptProbeBackend().generate(context);
        assertTrue(result.success(), result::message);
        JsonObject report = report(context);
        assertTrue(report.get("complete").getAsBoolean(), report::toString);
        assertTrue(report.getAsJsonObject("incompleteMembers").isEmpty(), report::toString);
        assertTrue(result.warnings().stream().noneMatch(warning -> warning.contains("NEKO-4066")), result.warnings()::toString);
    }

    @Test
    void hiddenPartialIsNotReportedByEitherBackend(@TempDir Path directory) throws Exception {
        TypeDecl declaration = partialDeclaration();
        declaration.hidden = true;
        ProbeContext ts = context(directory, "typescript", List.of(Retained.class), List.of(declaration), List.of());
        ProbeBackend.GenerateResult tsResult = new TypeScriptProbeBackend().generate(ts);
        assertTrue(tsResult.success(), tsResult::message);
        assertTrue(report(ts).get("complete").getAsBoolean());
        ProbeBackend.GenerateResult pyResult = new PythonProbeBackend().generate(
                context(directory, "python", List.of(Retained.class), List.of(declaration), List.of()));
        assertTrue(pyResult.success(), pyResult::message);
        for (var result : List.of(tsResult, pyResult)) {
            assertTrue(result.warnings().stream().noneMatch(warning -> warning.contains("NEKO-4066")), result.warnings()::toString);
        }
    }

    private static TypeDecl partialDeclaration() {
        TypeDecl declaration = new TypeReflector().reflect(Retained.class);
        declaration.memberOmissions.add(OMISSION);
        declaration.methods.clear();
        return declaration;
    }

    private static void assertPartialWarning(ProbeBackend.GenerateResult result, String owner) {
        List<String> warnings = result.warnings().stream().filter(warning -> warning.startsWith("[NEKO-4066]")
                && warning.contains("class=" + owner + ";")).toList();
        assertEquals(1, warnings.size(), result.warnings()::toString);
        assertTrue(warnings.getFirst().contains("entire public method set unavailable"), warnings::toString);
    }

    private static ProbeContext context(Path directory, String language, List<Class<?>> roots, List<TypeDecl> ir,
                                        List<ClassDeclarationCatalogEntry> replacements) {
        var snapshot = new NekoScriptCatalogSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), replacements, List.of(), List.of(), List.of(), null, Map.of(), List.of());
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        return new ProbeContext.Of(snapshot, roots, ProbeConfig.defaultConfig(), paths, language,
                directory.resolve(".neko_probe").resolve(language), ir);
    }

    private static JsonObject report(ProbeContext context) throws Exception {
        return JsonParser.parseString(Files.readString(context.languageDir().resolve("@special/dependencies.json")))
                .getAsJsonObject();
    }

    private static ClassLoader fixtureLoader() {
        Map<String, byte[]> definitions = Map.of(ROOT, classBytes(ROOT, false), PARTIAL, classBytes(PARTIAL, true));
        return new ClassLoader(ProbePartialDeclarationReportTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    private static byte[] classBytes(String name, boolean partial) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC | ACC_SUPER, name.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        writer.visitField(ACC_PUBLIC, partial ? "retainedField" : "child",
                partial ? "I" : "L" + PARTIAL.replace('.', '/') + ";", null, null).visitEnd();
        if (partial) {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "unavailableSignature",
                    "()L" + MISSING.replace('.', '/') + ";", null, null);
            method.visitCode();
            method.visitInsn(ACONST_NULL);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    public static class Retained {
        public int retainedField;
    }
}
