package com.tkisor.nekojs.probe;

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
import com.tkisor.nekojs.probe.types.TypeConverter;
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
 * IR 渲染路径（Strategy B）的三个保证：
 * <ol>
 *   <li>（C1）**未编辑** IR 与旧 ClassDeclGenerator 路径产出逐字一致：同一收集结果分别在
 *       {@code ir=null} 与 {@code ir=未编辑列表} 下生成，两棵输出树文件集合与内容必须完全一致。</li>
 *   <li>（A1）getter 覆盖（RecipeEventJS.recipes → DocumentedRecipes）在 IR 重渲染路径同样生效：
 *       renderer 级（直接渲染 mutated TypeDecl）与 backend 级（带 mutated IR 走完整 generate）。
 *       另有一个机制级测试（不依赖 RecipeEventJS，用测试 classpath 上必然存在的 fixture 类）：
 *       override 键是「类名 + getter 属性名」，与被覆盖类无关，任何类都可注册覆盖。</li>
 *   <li>（A3）TypeDecl 的 docs 列表渲染为 JSDoc 块。</li>
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
     * 照抄 {@link ProbeOutputCompatibilityTest#generateAt} 的 ProbeConfig 构造：
     * 显式 5 前缀白名单 + maxDepth 5 + SMART。
     */
    private static ProbeConfig goldenConfig() {
        return new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("java", "net.minecraft", "net.minecraftforge", "net.neoforged", "com.tkisor.nekojs"),
                List.of(), List.of(), List.of("minecraft"), 5, "SMART"));
    }

    // ------------------------------------------------------------------
    //  C1：未编辑 IR 与旧路径的产出树完全一致
    // ------------------------------------------------------------------

    @Test
    void uneditedIrTreeMatchesLegacyTreeExactly() throws Exception {
        NekoScriptCatalogSnapshot snapshot = LegacyProbeFixture.snapshot();
        ProbeConfig cfg = goldenConfig();
        List<Class<?>> collected = new ArrayList<>(ProbeCoordinator.collectClasses(snapshot, cfg));
        assertFalse(collected.isEmpty(), "shared collection must not be empty");

        // 未编辑 IR：每个收集到的类反射成 TypeDecl；反射失败的类跳过（两棵树仍应一致，
        // 因为未被触及的 IR 对 TS 产物零影响）。
        TypeReflector reflector = new TypeReflector();
        List<TypeDecl> ir = new ArrayList<>();
        for (Class<?> cls : collected) {
            try {
                ir.add(reflector.reflect(cls));
            } catch (Throwable ignored) {
                // 反射失败跳过：旧路径 pregenerateDeclarations 同样按类容错
            }
        }
        assertFalse(ir.isEmpty(), "at least one class must be reflectable");

        Path outA = tempDir.resolve("legacy").resolve("probe-types");
        Path outB = tempDir.resolve("ir").resolve("probe-types");

        // 每次 generate 用独立的 backend 实例，避免缓存/状态串扰
        ProbeContext ctxA = ctx(snapshot, collected, cfg, outA, null);
        ProbeContext ctxB = ctx(snapshot, collected, cfg, outB, ir);

        ProbeGenerator.GenerateResult resultA = new TypeScriptProbeBackend().generate(ctxA);
        ProbeGenerator.GenerateResult resultB = new TypeScriptProbeBackend().generate(ctxB);
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
    //  A1：getter 覆盖在 IR 重渲染路径生效
    // ------------------------------------------------------------------

    /** renderer 级：mutated TypeDecl 经注册了 override 的 renderer 渲染，recipes getter 返回 DocumentedRecipes。 */
    @Test
    void getterOverrideSurvivesIrRerender() throws Exception {
        Class<?> recipeEventClass = recipeEventClassOrSkip();
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        TypeConverter tc = new TypeConverter(aliases);

        TypeDecl decl;
        try {
            decl = new TypeReflector().reflect(recipeEventClass);
        } catch (Throwable t) {
            Assumptions.assumeTrue(false, "RecipeEventJS reflection failed: " + t);
            return;
        }

        // 找 getter property == "recipes"，并触碰一个无关方法模拟 modify_type 编辑
        MethodDecl recipes = null;
        MethodDecl other = null;
        for (MethodDecl m : decl.methods) {
            if (m.isGetter && "recipes".equals(m.property)) recipes = m;
            else if (other == null && !m.isGetter) other = m;
        }
        Assumptions.assumeTrue(recipes != null, "recipes getter not found on RecipeEventJS");
        if (other != null) other.hidden = true;
        decl.mutated = true;

        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(tc);
        renderer.overrideGetter(recipeEventClass, "recipes", "DocumentedRecipes",
                "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";");

        String out = renderer.render(decl);
        assertTrue(out.contains("get recipes(): DocumentedRecipes;"),
                "override must survive IR re-render:\n" + out);
        // 镜像 ClassDeclGenerator：命中覆盖时只发射 get 行，不双发射原方法名
        assertFalse(out.contains("getRecipes():"), out);
    }

    /** backend 级：带 mutated RecipeEventJS 的 IR 走完整 generate()，产物含覆盖后的 getter 类型与 import。 */
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
        decl.mutated = true; // mutated → applyMutatedOverrides 走 renderer 重渲染

        Path out = tempDir.resolve("probe-types");
        ProbeContext ctx = ctx(emptySnapshot(), List.of(recipeEventClass), goldenConfig(),
                out, List.of(decl));

        ProbeGenerator.GenerateResult result = new TypeScriptProbeBackend().generate(ctx);
        assertTrue(result.success(), result.message());

        // 包路径 = fqn 包名（$ 内部类按 fqn 写入该包）
        Path declFile = out.resolve("@package/com/tkisor/nekojs/wrapper/event/server/index.d.ts");
        assertTrue(Files.exists(declFile), "declaration file missing: " + declFile);
        String content = Files.readString(declFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("get recipes(): DocumentedRecipes;"),
                "recipes getter must be DocumentedRecipes after IR re-render:\n" + content);
        assertTrue(content.contains("import { DocumentedRecipes } from \"@side-only/server/events/recipes\";"),
                "extra import must be merged by IndexFileGenerator:\n" + content);
    }

    /**
     * A1 机制级（不依赖 RecipeEventJS，避免 Assume 跳过）：override 键是「类名 + getter 属性名」，
     * 与被覆盖的类无关。用测试 classpath 上必然存在的 fixture 类验证：
     * ClassEditor 触碰一个无关成员把 TypeDecl 标记 mutated 后，renderer 上注册的
     * override（label → MyCustomType）在重渲染时替换 getter 返回类型，且不双发射原 getLabel()。
     */
    @Test
    void getterOverrideAppliesToArbitraryClassOnTestClasspath() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        TypeConverter tc = new TypeConverter(aliases);

        TypeDecl decl = new TypeReflector().reflect(OverrideMechanismFixture.class);

        // 经 ClassEditor 的公开入口（ProbeModifyTypeEventJS.forClass）隐藏一个无关成员，
        // 模拟 probe.modify_type 的参数级编辑 → decl 被标记 mutated
        var event = new ProbeModifyTypeEventJS(Map.of(decl.fqn, decl));
        var editor = event.forClass(decl.fqn);
        assertNotNull(editor, "forClass 必须返回 fixture 的 ClassEditor");
        assertTrue(editor.hasMethod("ping"), "fixture 应有 ping 成员供隐藏");
        editor.hideMethod("ping");
        assertTrue(decl.mutated, "ClassEditor 触碰后 TypeDecl 必须标记 mutated");

        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(tc);
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
     * A1 机制测试夹具：含一个 getter（getLabel → property "label"）与一个无关成员（ping）。
     * 必须是 public static 类，TypeReflector 才会反射其公共成员。
     */
    public static class OverrideMechanismFixture {
        public String getLabel() {
            return "label";
        }

        public void ping() {
            // 无关成员：仅用于被 ClassEditor.hideMethod 触碰，把 TypeDecl 标记为 mutated
        }
    }

    // ------------------------------------------------------------------
    //  A3：docs 渲染为 JSDoc 块
    // ------------------------------------------------------------------

    @Test
    void rendererEmitsJsDocBlocksForDocs() {
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        TypeConverter tc = new TypeConverter(aliases);

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

        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(tc);
        String out = renderer.render(decl);

        // 单行 doc → 一行块注释
        assertTrue(out.contains("    /** Class-level doc */\n    export class $Unknown"), out);
        assertTrue(out.contains("        /** field doc */\n        value: number"), out);
        assertTrue(out.contains("        /** ctor doc */\n        constructor();"), out);
        assertTrue(out.contains("        /** getter doc */\n        get x(): string"), out);
        // 多行 doc → 逐行星号前缀
        assertTrue(out.contains("        /**\n"
                + "         * method line one\n"
                + "         * method line two\n"
                + "         */\n"
                + "        run(): void"), out);
    }

    // ------------------------------------------------------------------
    //  内部工具
    // ------------------------------------------------------------------

    /** forName 失败（RecipeEventJS 不在测试 classpath）时跳过当前测试。 */
    private static Class<?> recipeEventClassOrSkip() {
        try {
            return Class.forName(RECIPE_EVENT_JS_FQN, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            Assumptions.assumeTrue(false, "RecipeEventJS not on test classpath");
            return null; // unreachable（assumeTrue(false) 抛 TestAbortedException）
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
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    /** 照抄 {@link ProbeOutputCompatibilityTest#readTree}：相对路径归一化 + 行尾归一化。 */
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
