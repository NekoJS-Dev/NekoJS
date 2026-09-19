package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.spec.PlatformAvailability;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 33（W9）contract/spec 覆盖 gate —— Fabric {@code common-api-processor} 延期后的
 * 非 processor 替代门禁之一：在<b>节点真实编译产物</b>上运行，不依赖 annotation processor。
 *
 * <p><b>owner</b>：Managed Surface/Probe owner 定义规范 spec；build convention owner 负责接线。
 * <b>输入</b>：本节点 classpath 上真实编译出的
 * {@code com.tkisor.nekojs.api.spec.inject.*}（规范声明：{@code @PlatformAvailability} scope +
 * {@code neko$} 方法）与 {@code com.tkisor.nekojs.api.inject.*Extension}（平台实现面）。
 * <b>逐项输出</b>：每个 spec 的 scope / 本节点是否 required / 实现面 / 每个 {@code neko$} 方法的
 * override 状态，与只读基线 {@code /nekojs/platform-gates/spec-coverage-<loader>.txt} 逐行对比；
 * 同时落 {@code build/nekojs-gates/spec-coverage-<loader>.json} 供 CI 汇总进同一报告。
 * <b>失败诊断</b>：逐条指出缺失的 <i>contract</i>（哪个 spec 在本节点没有任何实现面）与
 * <i>method</i>（哪个 {@code spec.neko$xxx} 未被实现面自行声明），并带 node/scope/impl。
 *
 * <p>与 {@code common-api-processor} 的关系：processor 只接受 {@code nf26/nf121/cr}，没有
 * Fabric platform option/scope，1.2.0 明确延期（见工单 33 延期说明）。本 gate 用编译后反射面
 * 覆盖同一契约，<b>不是 processor 等价物</b>，也不改变支持矩阵或 Fabric 已声明能力的验证范围。
 */
@Tag("platform-gate")
class PlatformSpecContractGateTest {

    private static final String SPEC_PACKAGE = "com.tkisor.nekojs.api.spec.inject";
    private static final String IMPL_PACKAGE = "com.tkisor.nekojs.api.inject";
    private static final String FIXTURE_DIR = "/nekojs/platform-gates/";
    private static final String REPORT_DIR = "build/nekojs-gates";

    @Test
    void specCoverageMatchesReadonlyFixtureAndReportsGaps() throws Exception {
        String loader = loaderId();
        String node = nodeId();
        String mcPredicate = minecraftPredicate();
        assertTrue(!"unknown".equals(loader),
                "节点必须能从 classpath 判定自己的 loader（FabricPlatform/NeoForgePlatform）");
        assertTrue(node != null && !node.isBlank(),
                "gate 必须知道自己在哪个节点运行（Gradle 注入 -Dnekojs.node=<node>）");
        assertTrue(mcPredicate != null,
                "节点必须能从打包 metadata 读出 minecraft 依赖谓词（fabric.mod.json / neoforge.mods.toml）");

        List<Class<?>> specs = specs();
        assertTrue(specs.size() >= 10, "规范 spec 数量异常（期望 >=10）：" + specs.size());

        Map<String, String> rows = new TreeMap<>();
        List<String> gaps = new ArrayList<>();

        for (Class<?> spec : specs) {
            PlatformAvailability.Scope scope = scopeOf(spec);
            boolean required = requiredOn(scope, loader);
            Class<?> impl = implementor(spec);

            if (impl == null) {
                rows.put(spec.getName() + "|impl", spec.getSimpleName() + " | scope=" + scope
                        + " | required=" + required + " | impl=NONE | "
                        + (required ? "required on " + loader + " but no *Extension extends it"
                                    : "not required on " + loader));
                if (required) {
                    gaps.add("missing-contract spec=" + spec.getName() + " node=" + loader
                            + " scope=" + scope + " owner=Managed Surface/Probe owner"
                            + " :: 规范 scope 要求本节点实现，但 api.inject 里没有任何 *Extension 直接 extends 该 spec");
                }
                continue;
            }

            rows.put(spec.getName() + "|impl", spec.getSimpleName() + " | scope=" + scope
                    + " | required=" + required + " | impl=" + impl.getSimpleName()
                    + " | extends=" + spec.getSimpleName());

            for (Method method : spec.getDeclaredMethods()) {
                if (!method.getName().startsWith("neko$")) continue;
                boolean declared = declares(impl, method);
                String methodName = method.getName() + "(" + method.getParameterCount() + ")";
                rows.put(spec.getName() + "#" + methodName, spec.getSimpleName() + "#" + methodName
                        + " | impl=" + impl.getSimpleName() + " | "
                        + (declared ? "declared-by-impl" : "inherits-sentinel-default"));
                if (!declared) {
                    gaps.add("missing-method spec=" + spec.getName() + " method=" + methodName
                            + " node=" + loader + " impl=" + impl.getName()
                            + " owner=Managed Surface/Probe owner"
                            + " :: 实现面未自行声明该方法，只会继承 spec 的 UnsupportedOperationException 哨兵");
                }
            }
        }

        emitReport(node, loader, mcPredicate, rows, gaps);

        String fixturePath = FIXTURE_DIR + "spec-coverage-" + loader + ".txt";
        assertEquals(normalize(readFixture(fixturePath)), normalize(String.join("\n", rows.values())),
                "spec 覆盖基线漂移（fixture=" + fixturePath
                        + "）。确认是规范/实现的有意变更后更新 fixture 并在工单 33 REPORT 的 golden 差异小节留记录");

        assertTrue(gaps.isEmpty(), "contract/spec gate 失败（node=" + loader
                + "，owner=Managed Surface/Probe owner；完整逐项输出见 "
                + REPORT_DIR + "/spec-coverage-" + loader + ".json）:\n" + String.join("\n", gaps));
    }

    // ---- 逐项输出落盘 ----

    private static void emitReport(String node, String loader, String mcPredicate,
                                   Map<String, String> rows, List<String> gaps) throws IOException {
        Path dir = Path.of(REPORT_DIR);
        Files.createDirectories(dir);
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"check\": \"spec-coverage\",\n");
        json.append("  \"owner\": \"Managed Surface/Probe owner; build convention owner (wiring)\",\n");
        json.append("  \"node\": \"").append(node).append("\",\n");
        json.append("  \"loader\": \"").append(loader).append("\",\n");
        json.append("  \"minecraftDependency\": \"").append(mcPredicate).append("\",\n");
        json.append("  \"rows\": [\n");
        int i = 0;
        for (String value : rows.values()) {
            json.append("    ").append(jsonString(value)).append(++i < rows.size() ? ",\n" : "\n");
        }
        json.append("  ],\n  \"failures\": [\n");
        for (int j = 0; j < gaps.size(); j++) {
            json.append("    ").append(jsonString(gaps.get(j))).append(j + 1 < gaps.size() ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        Files.writeString(dir.resolve("spec-coverage-" + node + ".json"), json.toString());
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---- 节点事实源（packaged metadata，非硬编码） ----

    /** 节点身份（Gradle 注入 -Dnekojs.node=<node>）：报告按节点落盘，同 loader 的不同 MC 节点不互相覆盖。 */
    private static String nodeId() {
        return System.getProperty("nekojs.node");
    }

    private static String loaderId() {
        if (loadQuiet("com.tkisor.nekojs.platform.FabricPlatform") != null) return "fabric";
        if (loadQuiet("com.tkisor.nekojs.platform.NeoForgePlatform") != null) return "neoforge";
        return "unknown";
    }

    /** 节点打包 metadata 里的 minecraft 依赖谓词：节点受支持 MC 范围的真实声明。 */
    private static String minecraftPredicate() {
        String fabric = readResource("/fabric.mod.json");
        if (fabric != null) {
            Matcher depends = Pattern.compile("\"depends\"\\s*:\\s*\\{([^}]*)\\}").matcher(fabric);
            if (depends.find()) {
                Matcher mc = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"").matcher(depends.group(1));
                if (mc.find()) return "fabric:minecraft " + mc.group(1);
            }
        }
        String neo = readResource("/META-INF/neoforge.mods.toml");
        if (neo != null) {
            Matcher blocks = Pattern.compile(
                    "(?s)\\[\\[dependencies\\.\"nekojs\"\\]\\](.*?)(?=\\[\\[|\\z)").matcher(neo);
            while (blocks.find()) {
                String block = blocks.group(1);
                if (block.contains("modId = \"minecraft\"")) {
                    Matcher range = Pattern.compile("versionRange\\s*=\\s*\"([^\"]+)\"").matcher(block);
                    if (range.find()) return "neoforge:minecraft " + range.group(1);
                }
            }
        }
        return null;
    }

    private static String readResource(String path) {
        try (InputStream in = PlatformSpecContractGateTest.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
    }

    private static Class<?> loadQuiet(String name) {
        try {
            return Class.forName(name, false, PlatformSpecContractGateTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError error) {
            return null;
        }
    }

    // ---- spec 发现 ----

    private static List<Class<?>> specs() throws Exception {
        List<Class<?>> result = new ArrayList<>();
        for (String simple : classNamesInPackage(SPEC_PACKAGE)) {
            Class<?> type = loadQuiet(SPEC_PACKAGE + "." + simple);
            if (type == null || !type.isInterface()) continue;
            if (type.getAnnotation(PlatformAvailability.class) == null) continue;
            result.add(type);
        }
        result.sort((a, b) -> a.getName().compareTo(b.getName()));
        return result;
    }

    private static final Map<String, List<String>> PACKAGE_CACHE = new java.util.HashMap<>();

    /**
     * 枚举包内成员：直接扫 test 运行 classpath（jar 与目录两种形态都覆盖），
     * 不依赖 ClassLoader 目录枚举（:common 产物可能是 jar 形式）。
     */
    private static List<String> classNamesInPackage(String packagePath) throws Exception {
        List<String> cached = PACKAGE_CACHE.get(packagePath);
        if (cached != null) return cached;
        String relative = packagePath.replace('.', '/') + "/";
        List<String> names = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) continue;
            java.io.File path = new java.io.File(entry);
            if (!path.exists()) continue;
            if (path.isDirectory()) {
                java.io.File dir = new java.io.File(path, relative);
                if (!dir.isDirectory()) continue;
                java.io.File[] files = dir.listFiles();
                if (files == null) continue;
                for (java.io.File file : files) {
                    if (file.getName().endsWith(".class")) {
                        names.add(file.getName().replace(".class", ""));
                    }
                }
            } else if (entry.endsWith(".jar")) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(path)) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (!name.startsWith(relative) || !name.endsWith(".class")) continue;
                        String rest = name.substring(relative.length());
                        if (rest.contains("/")) continue;
                        names.add(rest.replace(".class", ""));
                    }
                }
            }
        }
        names.sort(String::compareTo);
        assertTrue(!names.isEmpty(), "找不到 " + packagePath
                + " 的编译产物——节点 test classpath 不完整（gate 不能静默通过）");
        PACKAGE_CACHE.put(packagePath, names);
        return names;
    }

    /** 平台实现面：{@code api.inject.*} 里直接 extends 该 spec 的接口。 */
    private static Class<?> implementor(Class<?> spec) throws Exception {
        for (String simple : classNamesInPackage(IMPL_PACKAGE)) {
            Class<?> type = loadQuiet(IMPL_PACKAGE + "." + simple);
            if (type == null || !type.isInterface()) continue;
            for (Class<?> parent : type.getInterfaces()) {
                if (parent.getName().equals(spec.getName())) return type;
            }
        }
        return null;
    }

    /** 覆盖 = 实现面<b>自己声明</b>同签名方法，不是继承 spec 的哨兵 default。 */
    private static boolean declares(Class<?> impl, Method specMethod) {
        for (Method candidate : impl.getDeclaredMethods()) {
            if (!candidate.getName().equals(specMethod.getName())) continue;
            if (candidate.isBridge() || candidate.isSynthetic()) continue;
            if (candidate.getParameterCount() != specMethod.getParameterCount()) continue;
            boolean same = true;
            for (int i = 0; i < candidate.getParameterCount(); i++) {
                if (!candidate.getParameterTypes()[i].getName()
                        .equals(specMethod.getParameterTypes()[i].getName())) {
                    same = false;
                    break;
                }
            }
            if (same) return true;
        }
        return false;
    }

    private static PlatformAvailability.Scope scopeOf(Class<?> spec) {
        PlatformAvailability availability = spec.getAnnotation(PlatformAvailability.class);
        return availability == null ? PlatformAvailability.Scope.ALL : availability.value();
    }

    /** scope 语义（与 SpecCoverageProcessor.isRequiredOn 一致，平台轴换成节点真实 loader）。 */
    private static boolean requiredOn(PlatformAvailability.Scope scope, String loader) {
        return switch (scope) {
            case ALL -> true;
            case NF_ONLY, NF26_ONLY -> "neoforge".equals(loader);
            case CR_ONLY -> "cleanroom".equals(loader);
        };
    }

    // ---- 只读基线 ----

    private static String readFixture(String path) throws IOException {
        try (InputStream stream = PlatformSpecContractGateTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "缺少只读基线 " + path
                    + "；先把 build/nekojs-gates/spec-coverage-<node>.json 的 rows 落盘为基线"
                    + "（由 Managed Surface/Probe owner 审阅），不要用放宽断言代替");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String value) {
        return String.join("\n", value.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).sorted().toList());
    }
}
