package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 25 declaration/Probe parity fixture（09 deterministic fixture 模式在查询域的落地）：
 *
 * <p>runtime classes（{@code EntitySelectorsJS}/{@code EntitySelectorBuilderJS}/neoforge 上的
 * {@code DataMapJS}）复刻生产链路：ProbeCoordinator BFS 的种子类 → {@link TypeReflector}
 * 反射为共享 IR → 真实 TS/Python probe backend 渲染（产物只写 {@code @TempDir}，不触碰
 * 任何 golden）。断言：
 * <ol>
 *   <li>同一输入重复生成整棵树逐字节一致（确定性，09 AC2 模式）；</li>
 *   <li>每个 runtime public member（名单由反射派生，非手写）都出现在 TS 与 Python 产物中
 *       （行级锚定，避免单字母方法的子串误匹配）；不存在的名字不出现（phantom 负样本）。</li>
 * </ol>
 *
 * <p>裸 JVM 约束：类经 initialize=false 加载（不触发 MC 注册表类初始化链）；
 * {@code DataMapJS} 仅在 neoforge 类路径上纳入（fabric 节点自动缺席）。
 */
class QueryToolDeclarationParityTest {

    private static final String PHANTOM = "thisMemberDoesNotExistAtRuntime";

    @TempDir
    Path tempA;

    @TempDir
    Path tempB;

    @TempDir
    Path tempC;

    QueryToolDeclarationParityTest() {
        // 根测试树的最小 Platform 注入（Platform.init 自带防御：已初始化则不动）
        try {
            Platform.init(new StubPlatform());
        } catch (RuntimeException ignored) {
            // 其它测试已初始化过 Platform
        }
    }

    // ---- TS ----

    @Test
    void tsDeclarationIsDeterministicAndMatchesRuntimeMembers() throws Exception {
        Map<String, String> first = generate("typescript", tempA);
        Map<String, String> second = generate("typescript", tempA);
        assertEquals(first.keySet(), second.keySet(), "TS file set must be deterministic");
        for (String key : first.keySet()) {
            assertEquals(first.get(key), second.get(key), "TS byte drift: " + key);
        }
        assertMembersPresent(first, runtimeMethodNames());
    }

    // ---- Python ----

    @Test
    void pythonDeclarationIsDeterministicAndMatchesRuntimeMembers() throws Exception {
        Map<String, String> first = generate("python", tempB);
        Map<String, String> second = generate("python", tempB);
        assertEquals(first.keySet(), second.keySet(), "Python file set must be deterministic");
        for (String key : first.keySet()) {
            assertEquals(first.get(key), second.get(key), "Python byte drift: " + key);
        }
        assertMembersPresent(first, runtimeMethodNames());
    }

    // ---- generation（复刻生产派生链路）----

    private Map<String, String> generate(String language, Path outputDir) throws IOException {
        List<Class<?>> collected = collectedClasses();
        TypeReflector reflector = new TypeReflector();
        List<TypeDecl> ir = new ArrayList<>();
        for (Class<?> cls : collected) {
            ir.add(reflector.reflect(cls));
        }

        NekoScriptCatalogSnapshot snapshot = new NekoScriptCatalogSnapshot(
                List.of(ScriptType.values()),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                Map.of(), List.of());
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("com.tkisor.nekojs"), List.of(), List.of(), List.of(), 5, "SMART"));
        NekoJSPaths paths = NekoJSPaths.fromGameDir(tempC);
        Files.createDirectories(paths.root());
        ProbeContext ctx = new ProbeContext.Of(snapshot, collected, cfg, paths, language, outputDir, ir);

        ProbeBackend backend = language.equals("typescript")
                ? new TypeScriptProbeBackend() : new PythonProbeBackend();
        ProbeBackend.GenerateResult result = backend.generate(ctx);
        assertTrue(result.success(), language + " probe generate failed: " + result.message());

        Map<String, String> tree = new TreeMap<>();
        try (Stream<Path> files = Files.walk(outputDir)) {
            List<Path> sorted = new ArrayList<>(files.filter(Files::isRegularFile).sorted().toList());
            for (Path file : sorted) {
                tree.put(outputDir.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file));
            }
        }
        assertFalse(tree.isEmpty(), language + " declaration tree must not be empty");
        return tree;
    }

    // ---- member parity（runtime 反射派生，行级锚定）----

    /** 种子类的 runtime public member 名单（与 generate 同一 collectedClasses 派生）。 */
    private static List<String> runtimeMethodNames() {
        List<String> names = new ArrayList<>();
        for (Class<?> cls : collectedClasses()) {
            for (Method method : cls.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())
                        && !method.isSynthetic() && method.getDeclaringClass() == cls) {
                    names.add(method.getName());
                }
            }
        }
        assertFalse(names.isEmpty(), "runtime member list must not be empty");
        return names;
    }

    private static void assertMembersPresent(Map<String, String> tree, List<String> memberNames) {
        // 只看声明文件（.d.ts / .pyi），按行锚定：TS 成员行以 name( 开头；Python 以 def name( 开头
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : tree.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(".d.ts") || key.endsWith(".pyi")) {
                for (String line : entry.getValue().split("\n")) {
                    lines.add(line.trim());
                }
            }
        }
        assertFalse(lines.isEmpty(), "declaration lines must not be empty");
        for (String name : memberNames) {
            boolean present = lines.stream().anyMatch(line ->
                    line.startsWith(name + "(")
                            || line.startsWith("def " + name + "(")
                            // Python getter 语义：isXxx/getXxx 渲染为 @property def xxx
                            || line.startsWith("def " + dePrefixed(name) + "("));
            assertTrue(present, "runtime member missing from declaration: " + name);
        }
        assertFalse(lines.stream().anyMatch(line -> line.startsWith(PHANTOM)),
                "phantom member leaked into declaration");
    }

    private static String dePrefixed(String name) {
        if (name.startsWith("is") && name.length() > 2) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        if (name.startsWith("get") && name.length() > 3) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        return name;
    }

    // ---- helpers ----

    /** 生产 probe 的种子类（本域的 script-facing 面）；DataMapJS 仅 neoforge 类路径存在。 */
    private static List<Class<?>> collectedClasses() {
        List<Class<?>> collected = new ArrayList<>();
        for (String name : new String[]{
                "com.tkisor.nekojs.util.selector.EntitySelectorsJS",
                "com.tkisor.nekojs.util.selector.EntitySelectorBuilderJS",
                "com.tkisor.nekojs.bindings.static_access.DataMapJS"}) {
            Class<?> cls = load(name);
            if (cls != null) {
                collected.add(cls);
            }
        }
        assertFalse(collected.isEmpty(), "seed classes must not be empty");
        return collected;
    }

    /** 裸 JVM 安全加载（initialize=false）：反射不触发 MC 注册表类初始化。 */
    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, QueryToolDeclarationParityTest.class.getClassLoader());
        } catch (ClassNotFoundException error) {
            return null;
        }
    }

    private static final class StubPlatform implements IPlatform {
        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "0.0.0";
        }

        @Override
        public Path getGameDir() {
            return com.tkisor.nekojs.TestGameDirs.unique("nekojs-query-tools-test");
        }

        @Override
        public Map<String, IModInfo> getMods() {
            return Map.of();
        }

        @Override
        public IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0.0.0";
        }
    }
}
