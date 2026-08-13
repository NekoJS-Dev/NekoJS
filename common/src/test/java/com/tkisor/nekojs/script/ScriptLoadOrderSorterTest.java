package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ScriptLoadOrderSorter} 的单元测试：直接构造 {@link ScriptContainer}（无需 Graal Context），
 * 覆盖 after 拓扑排序、priority 主序、宽松引用解析与确定性回退。
 */
class ScriptLoadOrderSorterTest {

    private static ScriptPropertyRegistry registry;
    private static Path root;

    @BeforeAll
    static void init() {
        TestPlatformInit.ensureInitialized();
        var impl = new ScriptPropertyRegistry.Impl();
        // 与 NekoJSCorePlugin 的注册顺序保持一致，保证 ordinal 语义相同
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        registry = impl;
        root = ScriptType.SERVER.path;
    }

    private static ScriptContainer script(String rel, int priority, String... after) {
        Path path = root.resolve(rel);
        ScriptContainer container = new ScriptContainer(ScriptType.SERVER.makeId(path), ScriptType.SERVER, path, registry);
        if (priority != 0) {
            container.properties.put(ScriptProperty.PRIORITY, priority);
        }
        if (after.length > 0) {
            container.properties.put(ScriptProperty.AFTER, List.of(after));
        }
        return container;
    }

    private static ScriptLoadOrderSorter.Result sort(List<ScriptContainer> scripts) {
        return ScriptLoadOrderSorter.applyAfterOrder(scripts, ScriptContainer::shouldRun);
    }

    private static List<String> keys(List<ScriptContainer> scripts) {
        return scripts.stream().map(s -> root.relativize(s.path).toString().replace('\\', '/')).toList();
    }

    private static List<String> sortedKeys(ScriptContainer... scripts) {
        List<ScriptContainer> batch = new ArrayList<>(List.of(scripts));
        ScriptLoadOrderSorter.Result result = sort(batch);
        assertFalse(result.hasProblems(), "unexpected problems: " + result.describe());
        return keys(batch);
    }

    // ---- (a) after 在同 priority 内反转字典序 ----

    @Test
    void afterReversesOrderWithinSamePriority() {
        // 发现顺序（字典序）为 a.js → z.js；after: z.js 使 z.js 先于 a.js
        assertIterableEquals(
                List.of("z.js", "a.js"),
                sortedKeys(script("a.js", 0, "z.js"), script("z.js", 0)));
    }

    // ---- (b) priority 不同时 after 被忽略 ----

    @Test
    void afterIgnoredWhenPrioritiesDiffer() {
        // b.js priority 更高且声明 after: a.js，但跨 priority 不建边，b 仍先加载
        assertIterableEquals(
                List.of("b.js", "a.js"),
                sortedKeys(script("a.js", 0), script("b.js", 10, "a.js")));
    }

    // ---- (c) 未知引用回退且不报错 ----

    @Test
    void missingAfterReferenceFallsBackWithoutError() {
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("a.js", 0, "nope/missing.js"),
                script("b.js", 0)));

        ScriptLoadOrderSorter.Result result = sort(batch);

        assertIterableEquals(List.of("a.js", "b.js"), keys(batch));
        assertTrue(result.hasProblems());
        assertTrue(result.describe().contains("nope/missing.js"),
                "warning should mention the unresolved reference: " + result.describe());
    }

    // ---- (d) 循环不抛异常且顺序确定 ----

    @Test
    void afterCycleDoesNotThrowAndKeepsStableOrder() {
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("a.js", 0, "b.js"),
                script("b.js", 0, "a.js")));

        ScriptLoadOrderSorter.Result result = sort(batch);

        assertIterableEquals(List.of("a.js", "b.js"), keys(batch));
        assertTrue(result.hasProblems());
        assertTrue(result.describe().contains("循环"), "warning should mention the cycle: " + result.describe());

        // 确定性：重排另一批相同输入，结果一致
        List<ScriptContainer> again = new ArrayList<>(List.of(
                script("a.js", 0, "b.js"),
                script("b.js", 0, "a.js")));
        sort(again);
        assertIterableEquals(keys(batch), keys(again));
    }

    // ---- 部分环：整组回退原始稳定顺序 ----

    @Test
    void partialCycleRestoresWholeGroupToOriginalOrder() {
        // 原始顺序 a b c d；a after b、b after a 成环；c after b 依赖环中成员。
        // 文档承诺「循环依赖回退到原始稳定顺序」应作用于整组，而不是把环成员
        // 追加到已排序节点之后（后者会把无关的 d 挤到最前，且破坏 after 语义）。
        List<ScriptContainer> batch = new ArrayList<>(List.of(
                script("a.js", 0, "b.js"),
                script("b.js", 0, "a.js"),
                script("c.js", 0, "b.js"),
                script("d.js", 0)));

        ScriptLoadOrderSorter.Result result = sort(batch);

        assertIterableEquals(List.of("a.js", "b.js", "c.js", "d.js"), keys(batch));
        assertTrue(result.hasProblems());
        assertTrue(result.describe().contains("循环"),
                "warning should mention the cycle: " + result.describe());
    }

    // ---- 引用解析的宽松处理 ----

    @Test
    void afterDotSlashResolvesRelativeToDeclaringDirectory() {
        // ./init.js 相对 sub/main.js 所在目录 → sub/init.js
        assertIterableEquals(
                List.of("sub/init.js", "sub/main.js"),
                sortedKeys(script("sub/main.js", 0, "./init.js"), script("sub/init.js", 0)));
    }

    @Test
    void afterAcceptsNekojsAndTypePrefixes() {
        // nekojs/server/b.js 与 server/b.js 都应解析为根目录下的 b.js
        assertIterableEquals(
                List.of("b.js", "a.js"),
                sortedKeys(script("a.js", 0, "nekojs/server/b.js"), script("b.js", 0)));
        assertIterableEquals(
                List.of("b.js", "a.js"),
                sortedKeys(script("a.js", 0, "server/b.js"), script("b.js", 0)));
    }

    @Test
    void afterGlobMatchesAllFilesInDirectory() {
        assertIterableEquals(
                List.of("lib/x.js", "lib/y.js", "main.js"),
                sortedKeys(
                        script("lib/x.js", 0),
                        script("lib/y.js", 0),
                        script("main.js", 0, "lib/*")));
    }

    @Test
    void afterReferenceToDisabledScriptIsSilentlyIgnored() {
        ScriptContainer disabled = script("lib/init.js", 0);
        disabled.properties.put(ScriptProperty.DISABLE, true);
        // 引用已知但目标不会执行 → 不建边、不告警
        assertIterableEquals(
                List.of("main.js", "lib/init.js"),
                sortedKeys(script("main.js", 0, "lib/init.js"), disabled));
    }

    // ---- 稳定性 ----

    @Test
    void unrelatedScriptsKeepDiscoveryOrder() {
        assertIterableEquals(
                List.of("a.js", "b.js", "c.js"),
                sortedKeys(script("a.js", 0), script("b.js", 0), script("c.js", 0)));
    }
}
