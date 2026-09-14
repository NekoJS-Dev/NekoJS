package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 13 AC7：Python 的 declaration/probe <strong>语言与 module 归属</strong>不得被 JS/TS
 * declaration 覆盖。
 *
 * <p>同一份共享 IR 分别交给 {@link PythonProbeBackend}（`python`）与
 * {@link TypeScriptProbeBackend}（`typescript`）渲染：两条产物必须落在<strong>各自的</strong>
 * language 目录与文件类型下，module 归属（Java 包 → 模块文件）一致，且互不覆盖。
 */
class PythonDeclarationOwnershipTest {

    public static final class Owned {
        public String getLabel() { return "label"; }
        public int compute(int base, int delta) { return base + delta; }
    }

    @TempDir
    Path temp;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void pythonDeclarationKeepsItsOwnLanguageAndModuleOwnership() throws Exception {
        // 真实反射路径：member parity（方法 / getter 语义）需要类的实际成员，手构空 TypeDecl 覆盖不到
        List<TypeDecl> ir = List.of(new com.tkisor.nekojs.probe.ir.TypeReflector().reflect(Owned.class));
        NekoScriptCatalogSnapshot snapshot = emptySnapshot();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());

        ProbeBackend.GenerateResult pythonResult = generate(new PythonProbeBackend(), snapshot, paths, ir, "python");
        ProbeBackend.GenerateResult tsResult = generate(new TypeScriptProbeBackend(), snapshot, paths, ir, "typescript");
        assertTrue(pythonResult.success(), "python probe failed: " + pythonResult.message());
        assertTrue(tsResult.success(), "typescript probe failed: " + tsResult.message());

        Map<String, String> pythonTree = tree(paths, "python");
        Map<String, String> tsTree = tree(paths, "typescript");

        // 1) 各自的 language id（backend 自称的语言，不得被对方覆盖）
        assertEquals("python", new PythonProbeBackend().languageId());
        assertEquals("typescript", new TypeScriptProbeBackend().languageId());
        assertNotEquals(new PythonProbeBackend().languageId(), new TypeScriptProbeBackend().languageId(),
                "python 与 typescript declaration 必须是两种语言身份");

        // 2) 文件类型与输出目录互不相同：.pyi 只出现在 python 目录，.d.ts 只出现在 typescript 目录
        assertTrue(pythonTree.keySet().stream().anyMatch(k -> k.endsWith(".pyi")),
                "python 归属必须产出 .pyi：\n" + pythonTree.keySet());
        assertFalse(pythonTree.keySet().stream().anyMatch(k -> k.endsWith(".d.ts")),
                "python 归属不得混入 .d.ts 产物");
        assertEquals(pythonTree.keySet(), pythonTree.keySet().stream().filter(k -> k.endsWith(".pyi")
                        || k.endsWith("py.typed") || k.endsWith("README.md")).collect(java.util.stream.Collectors.toSet()),
                "python 产物只能是 .pyi / py.typed / README.md");
        assertTrue(tsTree.keySet().stream().noneMatch(k -> k.endsWith(".pyi")),
                "typescript 归属不得混入 .pyi 产物");
        // 目录互不重叠（各自 outputDir 下）
        assertTrue(pythonTree.keySet().stream().noneMatch(tsTree::containsKey)
                        || java.util.Collections.disjoint(pythonTree.keySet(), tsTree.keySet()),
                "两条归属不得写同一批相对路径");

        // 3) module 归属一致：同一个 Java 类的声明落在各自语言的同一 Java 包模块里
        String pkgPath = Owned.class.getPackageName().replace('.', '/');
        assertTrue(pythonTree.containsKey("nekojs/_java/" + pkgPath + "/__init__.pyi"),
                "python 侧必须在运行时包对应的模块里声明该类：\n" + pythonTree.keySet());
        String pythonModulePath = "nekojs/_java/" + pkgPath + "/__init__.pyi";
        String pythonModule = pythonTree.get(pythonModulePath);
        assertTrue(pythonModule != null && pythonModule.contains("class PythonDeclarationOwnershipTest_Owned"),
                "python module 必须声明该类型：\n" + pythonModule);
        // TS 侧同一 Java 包的模块文件：@package/<java 包路径>/index.d.ts
        // （与 Python 侧同等强度：精确路径断言，不做「找不到就兜底搜索」）
        String tsModulePath = "@package/" + pkgPath + "/index.d.ts";
        assertTrue(tsTree.containsKey(tsModulePath),
                "typescript 侧必须在同一 Java 包路径下产出模块文件 " + tsModulePath
                        + "（module 归属按 Java 包一致）：\n" + tsTree.keySet());
        String tsModule = tsTree.get(tsModulePath);
        // TS 侧的类名按 TS 自己的嵌套类拼写（$Outer$Inner）——这是 TS declaration 的既有约定，
        // 不是「归属被覆盖」；断言精确到该语言自己的符号拼写。
        String tsClassName = "\u0024PythonDeclarationOwnershipTest\u0024Owned";
        assertTrue(tsModule.contains("class " + tsClassName),
                "typescript 模块必须声明同一类型（TS 拼写 " + tsClassName + "）：\n" + tsModule);
        // 反向：不得把该类声明到别的包路径下（归属唯一）
        String strayTs = tsTree.entrySet().stream()
                .filter(e -> !e.getKey().equals(tsModulePath))
                .filter(e -> e.getKey().endsWith(".d.ts"))
                .filter(e -> e.getValue().contains("class " + tsClassName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        assertTrue(strayTs == null,
                "同一个类不得出现在第二个 TS 归属模块里，实际额外出现在: " + strayTs);
        assertFalse(pythonModule.contains("interface "),
                "python 归属不得被 TS 语法覆盖：\n" + pythonModule);

        // 4) runtime member parity 在 python 侧保持（getter 语义 + 方法）
        assertTrue(pythonModule.contains("def compute(self, base: int, delta: int) -> int"),
                "python runtime member 必须保留：\n" + pythonModule);
        assertTrue(pythonModule.contains("@property"),
                "python getter 语义必须保留：\n" + pythonModule);
    }

    private ProbeBackend.GenerateResult generate(ProbeBackend backend, NekoScriptCatalogSnapshot snapshot,
                                                 NekoJSPaths paths, List<TypeDecl> ir, String dir) throws Exception {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs.core.module"), List.of(), List.of(), List.of(), 5, "SMART"));
        Path outputDir = paths.gameDir().resolve("probe-" + dir);
        // collectedClasses 必须与 backends 的包树/收集输入一致（生产由 ProbeCoordinator 传入；
        // 这里显式给同一份 fixture 类，否则 TS 侧的 PackageTree 为空、只产出根 index.d.ts）
        ProbeContext ctx = new ProbeContext.Of(snapshot, List.of(Owned.class), cfg, paths, dir, outputDir, ir);
        return backend.generate(ctx);
    }

    private static Map<String, String> tree(NekoJSPaths paths, String dir) throws Exception {
        Path outputDir = paths.gameDir().resolve("probe-" + dir);
        Map<String, String> out = new TreeMap<>();
        if (!Files.exists(outputDir)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(outputDir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.put(outputDir.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        }
        return out;
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}
