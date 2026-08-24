package com.tkisor.nekojs.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W2 护栏：每个 {@code *Extension} 宿主注入接口必须真的被应用——要么被 nekojs.mixins.json
 * 里列出的 mixin 实现，要么登记在 nekojs.interface_injection.json（26.x 双路径：interface
 * injection 覆盖 dev，mixin 覆盖 production）。
 *
 * <p>新增 Extension 接口而忘了配 mixin/注入条目时，脚本侧调用会整类静默失效——这是那种
 * 「编译全绿、probe 照常生成声明、运行期 NoSuchMethod」的错。接口清单从测试 classpath 的
 * classes 目录枚举（与编译来源树无关）。
 */
class ExtensionCoverageTest {

    private static final String INJECT_PACKAGE = "com.tkisor.nekojs.api.inject";

    @Test
    void everyExtensionIsAppliedByMixinOrInterfaceInjection() throws Exception {
        Set<String> extensions = enumerateExtensions();
        assertFalse(extensions.isEmpty(), "no *Extension classes found - enumeration is broken");

        Set<String> covered = new HashSet<>();
        for (String config : List.of("nekojs.mixins.json")) {
            collectMixinInterfaces(config, covered);
        }
        collectInterfaceInjection(covered);

        Set<String> missing = new HashSet<>(extensions);
        missing.removeAll(covered);
        assertTrue(missing.isEmpty(),
                "Extension interfaces not applied by any mixin or interface_injection entry: " + missing
                        + " (covered: " + covered + ")");
    }

    /** classpath 上 inject 包目录里的 *Extension.class（合并编译输出目录，与源码树布局无关）。 */
    private static Set<String> enumerateExtensions() throws Exception {
        Set<String> out = new HashSet<>();
        ClassLoader loader = ExtensionCoverageTest.class.getClassLoader();
        for (var url : Collections.list(loader.getResources(INJECT_PACKAGE.replace('.', '/')))) {
            if (!"file".equalsIgnoreCase(url.getProtocol())) continue;
            Path dir = Path.of(url.toURI());
            try (var files = Files.list(dir)) {
                files.filter(f -> f.getFileName().toString().endsWith("Extension.class"))
                        .forEach(f -> out.add(INJECT_PACKAGE + "."
                                + f.getFileName().toString().replace(".class", "")));
            }
        }
        return out;
    }

    /** mixin 类实现的 inject 包接口 = mixin 路径覆盖（agent 只重写注解，implements 子句不受影响）。 */
    private static void collectMixinInterfaces(String configName, Set<String> covered) throws Exception {
        JsonObject root = readConfig(configName);
        String pkg = root.get("package").getAsString();
        ClassLoader loader = ExtensionCoverageTest.class.getClassLoader();
        for (String section : List.of("mixins", "client", "server")) {
            JsonElement sectionEl = root.get(section);
            if (sectionEl == null) continue;
            JsonArray entries = sectionEl.getAsJsonArray();
            for (JsonElement e : entries) {
                Class<?> mixin = Class.forName(pkg + "." + e.getAsString(), false, loader);
                for (Class<?> iface : mixin.getInterfaces()) {
                    if (iface.getName().startsWith(INJECT_PACKAGE + ".")) {
                        covered.add(iface.getName());
                    }
                }
            }
        }
    }

    /** interface_injection.json 的 values = 注入路径覆盖（仅 26.x 有这份资源）。 */
    private static void collectInterfaceInjection(Set<String> covered) throws Exception {
        InputStream in = ExtensionCoverageTest.class.getClassLoader()
                .getResourceAsStream("nekojs.interface_injection.json");
        if (in == null) return;
        JsonObject root;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (var entry : root.entrySet()) {
            for (JsonElement iface : entry.getValue().getAsJsonArray()) {
                covered.add(iface.getAsString().replace('/', '.'));
            }
        }
    }

    private static JsonObject readConfig(String resource) throws Exception {
        InputStream in = ExtensionCoverageTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull(in, resource + " must be on the test classpath");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
