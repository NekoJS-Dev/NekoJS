package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Three guarantees of the IR render path:
 * <ol>
 *   <li>(C1) An <b>unedited</b> IR produces the same output as the old ClassDeclGenerator path: the
 *       same collection result is generated once with {@code ir=null} and once with a non-null
 *       unedited IR list; both output trees must have the same file set and the same content.
 *   </li>
 *   <li>(A1) The getter override (RecipeEventJS.recipes -> DocumentedRecipes) also applies on the IR
 *       re-render path: at renderer level (render the mutated TypeDecl directly) and at backend level
 *       (run the full generate with the mutated IR). A third mechanism-level test does not depend on
 *       RecipeEventJS and uses a fixture class that is always present on the test classpath: the
 *       override key is the class name plus the getter property name, independent of the class, so
 *       any class can register an override.
 *   </li>
 *   <li>The docs list of a TypeDecl renders as a JSDoc block.</li>
 * </ol>
 */
class TypeScriptNoopIrGoldenTest {

    private static final String RECIPE_EVENT_JS_FQN = "com.tkisor.nekojs.wrapper.event.server.RecipeEventJS";

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    /**
     * Builds the same ProbeConfig as {@link ProbeOutputCompatibilityTest#generateAt}: the explicit
     * five-prefix whitelist, maxDepth 5 and SMART mode.
     */
    private static ProbeConfig goldenConfig() {
        return new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("java", "net.minecraft", "net.minecraftforge", "net.neoforged", "com.tkisor.nekojs"),
                List.of(), List.of(), List.of("minecraft"), 5, "SMART"));
    }

    // ------------------------------------------------------------------
    //  C1: an unedited IR produces the same tree as the old path
    // ------------------------------------------------------------------

    @Test
    void uneditedIrTreeMatchesLegacyTreeExactly() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        ProbeConfig cfg = goldenConfig();
        List<Class<?>> collected = new ArrayList<>(ProbeCoordinator.collectClasses(snapshot, cfg));
        assertFalse(collected.isEmpty(), "shared collection must not be empty");

        // Unedited IR: every collected class is reflected into a TypeDecl. A class that fails reflection is
        // skipped, and the two trees should still match, because an IR that is not touched has no effect
        // on the TS output.
        TypeReflector reflector = new TypeReflector();
        List<TypeDecl> ir = new ArrayList<>();
        for (Class<?> cls : collected) {
            try {
                ir.add(reflector.reflect(cls));
            } catch (Throwable ignored) {
                // Reflection failures are skipped: the old path, pregenerateDeclarations, also tolerates them per class.
            }
        }
        assertFalse(ir.isEmpty(), "at least one class must be reflectable");

        Path outA = tempDir.resolve("legacy").resolve("probe-types");
        Path outB = tempDir.resolve("ir").resolve("probe-types");

        // Each generate call uses its own backend instance, so cache entries or state cannot leak between them.
        ProbeContext ctxA = ctx(snapshot, collected, cfg, outA, null);
        ProbeContext ctxB = ctx(snapshot, collected, cfg, outB, ir);

        ProbeBackend.GenerateResult resultA = new TypeScriptProbeBackend().generate(ctxA);
        ProbeBackend.GenerateResult resultB = new TypeScriptProbeBackend().generate(ctxB);
        assertTrue(resultA.success(), "legacy path failed: " + resultA.message());
        assertTrue(resultB.success(), "IR path failed: " + resultB.message());

        Map<String, String> treeA = readTree(outA);
        Map<String, String> treeB = readTree(outB);
        assertFalse(treeA.isEmpty(), "legacy tree must not be empty");

        assertEquals(treeA.keySet(), treeB.keySet(),
                "unedited-IR path must produce the same file set as legacy path");
        for (String relPath : treeA.keySet()) {
            assertEquals(treeA.get(relPath), treeB.get(relPath),
                    "unedited-IR path must byte-match legacy path for " + relPath);
        }
    }

    // ------------------------------------------------------------------
    //  A1: the getter override applies on the IR re-render path
    // ------------------------------------------------------------------

    /**
     * Renderer level: a mutated TypeDecl is rendered by a renderer with the override registered, so the
     * recipes getter returns {@code DocumentedRecipes}.
     */
    @Test
    void getterOverrideSurvivesIrRerender() throws Exception {
        Class<?> recipeEventClass = recipeEventClassOrSkip();
        TypeAliasRegistry aliases = new TypeAliasRegistry();

        TypeDecl decl;
        try {
            decl = new TypeReflector().reflect(recipeEventClass);
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "RecipeEventJS reflection failed: " + t);
            return;
        }

        // Finds the getter whose property is "recipes" and touches an unrelated method to simulate a
        // modify_type edit.
        MethodDecl recipes = null;
        MethodDecl other = null;
        for (MethodDecl m : decl.methods) {
            if (m.isGetter && "recipes".equals(m.property)) recipes = m;
            else if (other == null && !m.isGetter) other = m;
        }
        Assumptions.assumeTrue(recipes != null, "recipes getter not found on RecipeEventJS");
        if (other != null) other.hidden = true;
        decl.mutated = true;

        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(aliases);
        renderer.overrideGetter(recipeEventClass, "recipes", "DocumentedRecipes",
                "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";");

        String out = renderer.render(decl);
        assertTrue(out.contains("get recipes(): DocumentedRecipes;"),
                "override must survive IR re-render:\n" + out);
        // Mirrors ClassDeclGenerator: when an override matches, only the get line is emitted; the original
        // method name is not emitted as well.
        assertFalse(out.contains("getRecipes():"), out);
    }

    /**
     * Backend level: an IR with a mutated RecipeEventJS goes through the full {@code generate()}, and the
     * output carries the overridden getter type and its import.
     */
    @Test
    void getterOverrideSurvivesFullGenerateWithMutatedIr() throws Exception {
        Class<?> recipeEventClass = recipeEventClassOrSkip();

        TypeDecl decl;
        try {
            decl = new TypeReflector().reflect(recipeEventClass);
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "RecipeEventJS reflection failed: " + t);
            return;
        }
        decl.mutated = true; // The mutated IR goes through applyMutatedOverrides, which re-renders with the renderer.

        Path out = tempDir.resolve("probe-types");
        ProbeContext ctx = ctx(emptySnapshot(), List.of(recipeEventClass), goldenConfig(),
                out, List.of(decl));

        ProbeBackend.GenerateResult result = new TypeScriptProbeBackend().generate(ctx);
        assertTrue(result.success(), result.message());

        // The package path is the FQN's package; a nested class (with $) is written into that same package.
        Path declFile = out.resolve("@package/com/tkisor/nekojs/wrapper/event/server/index.d.ts");
        assertTrue(Files.exists(declFile), "declaration file missing: " + declFile);
        String content = Files.readString(declFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("get recipes(): DocumentedRecipes;"),
                "recipes getter must be DocumentedRecipes after IR re-render:\n" + content);
        assertTrue(content.contains("import { DocumentedRecipes } from \"@side-only/server/events/recipes\";"),
                "extra import must be merged by IndexFileGenerator:\n" + content);
    }

    /**
     * Mechanism level, without RecipeEventJS so an assumption cannot skip the test: the override key is
     * the class name plus the getter property name and is independent of the overridden class. A fixture
     * class that is always on the test classpath proves it: after a ClassEditor touches an unrelated
     * member and marks the TypeDecl as mutated, the override registered on the renderer
     * (label -> MyCustomType) replaces the getter return type on re-render, and getLabel() is not
     * emitted as well.
     */
    @Test
    void getterOverrideAppliesToArbitraryClassOnTestClasspath() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();

        TypeDecl decl = new TypeReflector().reflect(OverrideMechanismFixture.class);

        // Hides an unrelated member through the public ClassEditor entry point
        // (ProbeModifyTypeEventJS.forClass) to simulate a probe.modify_type edit, which marks decl as mutated.
        var event = new ProbeModifyTypeEventJS(Map.of(decl.fqn, decl));
        var editor = event.forClass(decl.fqn);
        assertNotNull(editor, "forClass 必须返回 fixture 的 ClassEditor");
        assertTrue(editor.hasMethod("ping"), "fixture 应有 ping 成员供隐藏");
        editor.hideMethod("ping");
        assertTrue(decl.mutated, "ClassEditor 触碰后 TypeDecl 必须标记 mutated");

        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(aliases);
        renderer.overrideGetter(OverrideMechanismFixture.class, "label", "MyCustomType",
                "import { MyCustomType } from \"@side-only/server/custom\";");

        String out = renderer.render(decl);
        assertTrue(out.contains("get label(): MyCustomType;"),
                "override 必须在 IR 重渲染时替换 getter 返回类型:\n" + out);
        assertFalse(out.contains("getLabel():"),
                "命中覆盖时只发射 get 行，不双发射原 getLabel() 方法:\n" + out);
        assertFalse(out.contains("ping()"),
                "被 hideMethod 隐藏的成员不应出现在重渲染输出:\n" + out);
    }

    /**
     * Fixture for the mechanism test: one getter (getLabel becomes the property "label") and one
     * unrelated member (ping). It must be a public static class for TypeReflector to reflect its public
     * members.
     */
    public static class OverrideMechanismFixture {
        public String getLabel() {
            return "label";
        }

        public void ping() {
            // Unrelated member: only there to be touched by ClassEditor.hideMethod, which marks the TypeDecl as
            // mutated.
        }
    }

    // ------------------------------------------------------------------
    //  Docs render as JSDoc blocks
    // ------------------------------------------------------------------

    @Test
    void rendererEmitsJsDocBlocksForDocs() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();

        TypeDecl decl = new TypeDecl(TypeDecl.Kind.CLASS, null, "com.example.Documented");
        decl.docs.add("Class-level doc");

        FieldDecl field = new FieldDecl("value", TypeSlot.of(int.class, ApiTypeRef.primitive("int")));
        field.docs.add("field doc");
        decl.fields.add(field);

        MethodDecl ctor = new MethodDecl("Documented");
        ctor.isConstructor = true;
        ctor.docs.add("ctor doc");
        decl.constructors.add(ctor);

        MethodDecl method = new MethodDecl("run");
        method.returnType = TypeSlot.of(void.class, ApiTypeRef.voidType());
        method.docs.add("method line one");
        method.docs.add("method line two");
        decl.methods.add(method);

        MethodDecl getter = new MethodDecl("getX");
        getter.isGetter = true;
        getter.property = "x";
        getter.returnType = TypeSlot.of(String.class, ApiTypeRef.primitive("string"));
        getter.docs.add("getter doc");
        decl.methods.add(getter);

        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(aliases);
        String out = renderer.render(decl);

        // A single-line doc becomes a one-line block comment
        assertTrue(out.contains("    /** Class-level doc */\n    export class $Unknown"), out);
        assertTrue(out.contains("        /** field doc */\n        value: number"), out);
        assertTrue(out.contains("        /** ctor doc */\n        constructor();"), out);
        assertTrue(out.contains("        /** getter doc */\n        get x(): string"), out);
        // A multi-line doc becomes one star-prefixed line per doc line
        assertTrue(out.contains("        /**\n"
                + "         * method line one\n"
                + "         * method line two\n"
                + "         */\n"
                + "        run(): void"), out);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Skips the current test when {@code forName} fails because RecipeEventJS is not on the test classpath. */
    private static Class<?> recipeEventClassOrSkip() {
        try {
            return Class.forName(RECIPE_EVENT_JS_FQN, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            Assumptions.assumeTrue(false, "RecipeEventJS not on test classpath");
            return null; // Unreachable: {@code assumeTrue(false)} throws TestAbortedException
        }
    }

    private static ProbeContext ctx(NekoScriptCatalogSnapshot snapshot, List<Class<?>> collected,
                                    ProbeConfig cfg, Path outputDir, List<TypeDecl> ir) {
        return new ProbeContext.Of(snapshot, collected, cfg, NekoJSPaths.fromGameDir(
                Path.of(System.getProperty("java.io.tmpdir"), "nekojs-ir-golden-test")),
                "typescript", outputDir, ir);
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    /** Mirrors {@link ProbeOutputCompatibilityTest#readTree}: keys are paths relative to the root with
     * '/' separators, and values have normalized line endings. */
    private Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.exists(root)) return files;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = root.relativize(file).toString().replace('\\', '/');
                files.put(rel, normalize(Files.readString(file, StandardCharsets.UTF_8)));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
