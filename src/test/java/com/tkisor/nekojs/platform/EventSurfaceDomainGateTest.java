package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单 33（W9）event/surface 覆盖 gate —— Fabric {@code common-api-processor} 延期后的
 * 非 processor 替代门禁之二：驱动<b>节点真实的事件注册入口</b>，枚举注册进
 * {@link EventGroupRegistry} 的 domain 与 bus 成员。
 *
 * <p><b>owner</b>：Managed Surface/Probe owner（coverage ledger + contract fixture）；
 * build convention owner 负责接线。<b>输入</b>：本节点 classpath 上真实编译出的核心插件
 * （neoforge={@code NekoJSCorePlugin}，fabric={@code FabricCorePlugin}，即
 * {@code @RegisterNekoJSPlugin(priority = CORE_PRIORITY)} 的节点事件注册入口）与其
 * {@code registerEvents}/{@code registerClientEvents} 钩子，注册进本次测试新建的
 * {@link EventGroupRegistry.Impl}（不读进程级全局，也不依赖其它测试的类初始化副作用）。
 * <b>逐项输出</b>：注册到的每个 domain、holder 入口、bus 成员、client-events 是否可观测，
 * 与只读基线 {@code /nekojs/platform-gates/event-surface-domains.txt} 对比；另落
 * {@code build/nekojs-gates/event-surface-<loader>.json} 供 CI 汇总进同一报告。
 * <b>失败诊断</b>：{@code missing-binding}（承诺的 domain/bus 在本节点运行时未注册）、
 * {@code undocumented-domain}（注册了但基线未登记）、{@code unexpected-domain}、
 * {@code undeclared-evidence}、{@code client-events-not-verified}。
 *
 * <p><b>输入边界（明示，不夸大覆盖）</b>：本 gate 只覆盖核心插件入口注册的 domain。由其它
 * 插件注册的 domain（如 {@code DynamicRegistryPlugin}、{@code RecipeViewerEventsPlugin}）
 * 不在本 gate 输入内；基线中它们必须记为 {@code not-verified} 并保持对应域验收阻塞，不得写成
 * {@code unavailable}/{@code partial}（工单 33 AC3）。
 */
@Tag("platform-gate")
class EventSurfaceDomainGateTest {

    private static final String FIXTURE = "/nekojs/platform-gates/event-surface-domains.txt";
    private static final String REPORT_DIR = "build/nekojs-gates";

    private static final String REGISTER_EVENTS = "registerEvents";
    private static final String REGISTER_CLIENT_EVENTS = "registerClientEvents";

    private static final String PRESENT = "present";
    private static final String NOT_VERIFIED = "not-verified";

    @Test
    void eventSurfaceMatchesFixtureAndReportsMissingDomains() throws Exception {
        String loader = loaderId();
        assertTrue(!"unknown".equals(loader), "节点必须能判定自己的 loader（FabricPlatform/NeoForgePlatform）");
        String node = nodeId();
        assertTrue(node != null && !node.isBlank(),
                "gate 必须知道自己在哪个节点运行（Gradle 注入 -Dnekojs.node=<node>）");

        Map<String, Registration> registrations = new LinkedHashMap<>();
        List<String> gaps = new ArrayList<>();
        Map<String, String> rows = new TreeMap<>();

        List<String> contributors = pluginContributors(loader);
        assertTrue(!contributors.isEmpty(),
                "节点必须能发现事件注册入口（neoforge 注解扫描 / fabric 内置清单）（node=" + loader + "）");

        // 每个插件用独立 registry：与 bootstrap 的收集语义一致（同名组冲突由 merge policy 裁决），
        // 也避免"同一静态 GROUP 实例被注册两次"这种测试自身制造的重复 bus。
        for (String className : contributors) {
            record(registrations, className,
                    invokeAndRecord(className, REGISTER_EVENTS, new EventGroupRegistry.Impl()),
                    "server", gaps, node);
        }

        // client-events 通道：MC client 类在裸 JVM 可能无法初始化——失败必须显式记录，不能静默省略。
        Map<String, String> clientFailures = new LinkedHashMap<>();
        for (String className : contributors) {
            Map<String, String> invocation = invokeAndRecord(className, REGISTER_CLIENT_EVENTS,
                    new EventGroupRegistry.Impl());
            String error = invocation.get("error");
            if (error != null) {
                if (isMissingHook(invocation)) continue;
                clientFailures.put(className, error);
                continue;
            }
            record(registrations, className, invocation, "client", gaps, node);
        }

        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            Registration registration = entry.getValue();
            rows.put(entry.getKey(), "domain=" + registration.group() + " node=" + node
                    + " state=" + PRESENT + " channel=" + registration.channel()
                    + " buses=" + String.join(",", registration.buses()));
        }
        for (Map.Entry<String, String> failure : clientFailures.entrySet()) {
            rows.put("client-events@" + failure.getKey(),
                    "channel=client node=" + node + " state=" + NOT_VERIFIED
                            + " detail=" + failure.getValue());
        }

        Map<String, Map<String, NodeState>> fixture = readFixture();
        if (fixture == null) {
            emitReport(node, registrations, clientFailures, rows, gaps);
            assertTrue(false, "缺少只读基线 " + FIXTURE + "；本节点注册到 " + registrations.size()
                    + " 个 domain（" + String.join(", ", registrations.keySet()) + "）。"
                    + "请先跑五节点 test，把 build/nekojs-gates/event-surface-<node>.json 汇总为跨节点全景，"
                    + "经 Managed Surface/Probe owner 审阅后落盘为基线");
        }
        assertTrue(!fixture.isEmpty(), "只读基线不能为空");

        for (Map.Entry<String, Map<String, NodeState>> entry : fixture.entrySet()) {
            String key = entry.getKey();
            NodeState state = entry.getValue().get(node);
            if (state == null) {
                gaps.add(failure("missing-binding", key, node, "-",
                        "基线未登记本节点 —— 跨节点全景必须覆盖全部五个节点"));
                continue;
            }
            if (!PRESENT.equals(state.status()) && !NOT_VERIFIED.equals(state.status())) {
                gaps.add(failure("undeclared-evidence", key, node, "-",
                        "基线状态词 '" + state.status() + "' 非法：只允许 " + PRESENT + " / " + NOT_VERIFIED
                                + "；缺证据不得改判 unavailable/partial，也不得用改表掩盖规范 ALL 与实际能力的差异"));
                continue;
            }
            Registration runtime = registrations.get(key);
            if (PRESENT.equals(state.status()) && runtime == null) {
                gaps.add(failure("missing-binding", key, node, "-",
                        "基线声明本节点注册了该 domain，但本次通过插件注册入口实际没有注册到"
                                + "（input=" + String.join(",", contributors) + " 的 registerEvents/registerClientEvents 钩子）"));
                rows.put(key, "domain=" + key + " node=" + node + " state=MISSING");
                continue;
            }
            if (NOT_VERIFIED.equals(state.status())) {
                if (runtime != null) {
                    gaps.add(failure("unexpected-domain", key, node, runtime.holder(),
                            "基线登记 " + NOT_VERIFIED + "，但本节点实际注册了该 domain 运行时面"
                                    + " —— 必须补运行时/声明证据并把状态改为 " + PRESENT));
                } else {
                    rows.put(key, "domain=" + key + " node=" + node + " state=" + NOT_VERIFIED + " buses=-");
                }
                continue;
            }
            List<String> promised = state.buses();
            for (String bus : promised) {
                if (!runtime.buses().contains(bus)) {
                    gaps.add(failure("missing-binding", key, node, "buses=" + bus,
                            "holder=" + runtime.holder()
                                    + " :: 基线承诺的 bus 成员在本节点注册面不存在"));
                }
            }
            for (String bus : runtime.buses()) {
                if (!promised.contains(bus)) {
                    gaps.add(failure("member-drift", key, node, "buses=" + bus,
                            "holder=" + runtime.holder() + " :: 运行时有该 bus 但基线未记录"
                                    + "（新增成员必须补基线并审阅）"));
                }
            }
        }

        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            if (!fixture.containsKey(entry.getKey())) {
                gaps.add(failure("undocumented-domain", entry.getKey(), node, entry.getValue().holder(),
                        "本节点实际注册了该 domain，但跨节点基线未登记（补齐基线并审阅）"));
            }
        }

        emitReport(node, registrations, clientFailures, rows, gaps);

        assertTrue(gaps.isEmpty(), "event/surface gate 失败（node=" + node
                + "，owner=Managed Surface/Probe owner；逐项输出见 "
                + REPORT_DIR + "/event-surface-" + node + ".json）:\n" + String.join("\n", gaps));
    }

    private static String failure(String check, String domain, String node, String binding, String detail) {
        return check + " domain=" + domain + " node=" + node + " binding=" + binding
                + " owner=Managed Surface/Probe owner :: " + detail;
    }

    // ---- 注册面驱动 ----

    private record Registration(String key, String group, String holder, String channel, List<String> buses) {}

    private static void record(Map<String, Registration> target, String className,
                               Map<String, String> invocation, String channel,
                               List<String> gaps, String loader) {
        String error = invocation.get("error");
        if (error != null) {
            gaps.add("registration-failed holder=" + className + " node=" + loader
                    + " channel=" + channel + " owner=build convention owner"
                    + " :: 节点事件注册入口抛错，事件面无法建立：" + error);
            return;
        }
        for (Map.Entry<String, String> entry : invocation.entrySet()) {
            String groupName = entry.getKey();
            List<String> merged = entry.getValue().isBlank()
                    ? new ArrayList<>() : new ArrayList<>(List.of(entry.getValue().split(",")));
            Registration existing = target.get(groupName);
            if (existing != null) {
                merged.addAll(existing.buses());
                merged = new ArrayList<>(new TreeSet<>(merged));
            }
            target.put(groupName, new Registration(groupName, groupName, className, channel, merged));
        }
    }

    /**
     * 调用节点的某个事件注册钩子，返回 {@code group 名 -> "buses csv"}；
     * 钩子不存在（该节点无此通道）返回空表，钩子抛错返回 {@code {"error": ...}}。
     */
    private static Map<String, String> invokeAndRecord(String className, String methodName,
                                                       EventGroupRegistry registry) {
        Map<String, String> out = new LinkedHashMap<>();
        Object instance;
        Method method;
        try {
            Class<?> type = Class.forName(className, true,
                    EventSurfaceDomainGateTest.class.getClassLoader());
            instance = type.getDeclaredConstructor().newInstance();
            method = findMethod(type, methodName);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            out.put("error", describe(error));
            return out;
        }
        if (method == null) return out;
        try {
            method.invoke(instance, registry);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException error) {
            Throwable cause = error instanceof InvocationTargetException ite && ite.getCause() != null
                    ? ite.getCause() : error;
            out.clear();
            out.put("error", cause.getClass().getName() + ": " + cause.getMessage());
            return out;
        }
        for (Map.Entry<String, EventGroup> entry : registry.view().entrySet()) {
            List<String> buses = new ArrayList<>(entry.getValue().viewBuses().keySet());
            buses.sort(String::compareTo);
            out.put(entry.getKey(), String.join(",", buses));
        }
        return out;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() != 1) continue;
            if (!EventGroupRegistry.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            return method;
        }
        return null;
    }

    /** 无该钩子（非 Contributor）不是失败；只有实例化/调用抛错才是。 */
    private static boolean isMissingHook(Map<String, String> invocation) {
        return invocation.isEmpty();
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return error.getClass().getName() + ": " + error.getMessage()
                + (cause != error ? " (root: " + cause + ")" : "");
    }

    // ---- 节点事实源 ----

    /** 节点身份（Gradle 注入 -Dnekojs.node=<node>）；基线按节点而非 loader 组织，
     *  因为同 loader 的不同 MC 节点可以有合法的不同事件面。 */
    private static String nodeId() {
        return System.getProperty("nekojs.node");
    }

    private static String loaderId() {
        if (loadQuiet("com.tkisor.nekojs.platform.FabricPlatform") != null) return "fabric";
        if (loadQuiet("com.tkisor.nekojs.platform.NeoForgePlatform") != null) return "neoforge";
        return "unknown";
    }

    private static String corePluginClass(String loader) {
        String name = "fabric".equals(loader)
                ? "com.tkisor.nekojs.fabric.FabricCorePlugin"
                : "com.tkisor.nekojs.core.NekoJSCorePlugin";
        return loadQuiet(name) != null ? name : null;
    }

    /**
     * 事件注册入口清单：节点真实插件发现输入的并集 ——
     * neoforge = classpath 上带 {@code @RegisterNekoJSPlugin} 的类（{@code NeoForgePluginLoader} 的扫描口径）；
     * fabric = 上述注解类 ∪ {@code FabricPluginLoader.BUILTIN_PLUGINS}（内置清单，与
     * {@code QueryToolCapabilityMatrixTest} 同款事实源）。
     */
    @SuppressWarnings("unchecked")
    private static List<String> pluginContributors(String loader) throws IOException {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String className : classNamesInPackage("com.tkisor.nekojs")) {
            Class<?> type = loadQuiet(className);
            if (type == null) continue;
            if (type.getAnnotation(com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin.class) != null) {
                names.add(className);
            }
        }
        if ("fabric".equals(loader)) {
            Class<?> fabricLoader = loadQuiet("com.tkisor.nekojs.fabric.FabricPluginLoader");
            if (fabricLoader != null) {
                try {
                    var field = fabricLoader.getDeclaredField("BUILTIN_PLUGINS");
                    field.setAccessible(true);
                    for (Class<?> type : (List<Class<?>>) field.get(null)) names.add(type.getName());
                } catch (ReflectiveOperationException | RuntimeException error) {
                    throw new IllegalStateException("无法读取 FabricPluginLoader.BUILTIN_PLUGINS："
                            + describe(error));
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static final Map<String, List<String>> PACKAGE_CACHE = new java.util.HashMap<>();

    /** 递归枚举 classpath 上某个包前缀下的全部类名（目录与 jar 两种形态）。 */
    private static List<String> classNamesInPackage(String packagePrefix) throws IOException {
        List<String> cached = PACKAGE_CACHE.get(packagePrefix);
        if (cached != null) return cached;
        String prefix = packagePrefix.replace('.', '/') + "/";
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.isBlank()) continue;
            java.io.File path = new java.io.File(entry);
            if (!path.exists()) continue;
            if (path.isDirectory()) {
                Path base = path.toPath().resolve(packagePrefix.replace('.', '/'));
                if (!Files.isDirectory(base)) continue;
                try (var stream = Files.walk(base)) {
                    stream.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                        String relative = path.toPath().relativize(p).toString()
                                .replace(java.io.File.separatorChar, '/');
                        names.add(relative.substring(0, relative.length() - ".class".length())
                                .replace('/', '.'));
                    });
                }
            } else if (entry.endsWith(".jar")) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(path)) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (!name.startsWith(prefix) || !name.endsWith(".class")) continue;
                        names.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                    }
                }
            }
        }
        List<String> result = new ArrayList<>(names);
        PACKAGE_CACHE.put(packagePrefix, result);
        return result;
    }

    private static Class<?> loadQuiet(String name) {
        try {
            return Class.forName(name, false, EventSurfaceDomainGateTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError error) {
            return null;
        }
    }

    // ---- 逐项输出落盘 ----

    private static void emitReport(String node, Map<String, Registration> registrations,
                                   Map<String, String> clientFailures,
                                   Map<String, String> rows, List<String> gaps) throws IOException {
        String loader = loaderId();
        Path dir = Path.of(REPORT_DIR);
        Files.createDirectories(dir);
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"check\": \"event-surface\",\n");
        json.append("  \"owner\": \"Managed Surface/Probe owner; build convention owner (wiring)\",\n");
        json.append("  \"node\": \"").append(node).append("\",\n");
        json.append("  \"input\": \"").append(String.join(",", pluginContributors(loader))).append("\",\n");
        json.append("  \"domains\": ").append(registrations.size()).append(",\n");
        json.append("  \"clientEventsNotVerified\": [\n");
        int c = 0;
        for (Map.Entry<String, String> failure : clientFailures.entrySet()) {
            json.append("    ").append(jsonString(failure.getKey() + " :: " + failure.getValue()))
                    .append(++c < clientFailures.size() ? ",\n" : "\n");
        }
        json.append("  ],\n  \"rows\": [\n");
        int i = 0;
        for (String value : rows.values()) {
            json.append("    ").append(jsonString(value)).append(++i < rows.size() ? ",\n" : "\n");
        }
        json.append("  ],\n  \"failures\": [\n");
        for (int j = 0; j < gaps.size(); j++) {
            json.append("    ").append(jsonString(gaps.get(j))).append(j + 1 < gaps.size() ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");
        Files.writeString(dir.resolve("event-surface-" + node + ".json"), json.toString());
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---- 只读基线 ----

    /**
     * 基线格式（跨节点全景；唯一事实源是各节点核心插件注册面，基线只是经审阅的快照）：
     * {@code <domain> | <node> = <present|not-verified> | buses=<a,b,c>}
     */
    private record NodeState(String status, List<String> buses) {}

    private static Map<String, Map<String, NodeState>> readFixture() throws IOException {
        InputStream stream = EventSurfaceDomainGateTest.class.getResourceAsStream(FIXTURE);
        if (stream == null) return null;
        Map<String, Map<String, NodeState>> result = new LinkedHashMap<>();
        for (String raw : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\|");
            assertTrue(parts.length >= 3, "基线行格式错误（期望 domain | node = state | buses=...）：" + line);
            String domain = parts[0].trim();
            String[] nodeAndState = parts[1].split("=", 2);
            assertTrue(nodeAndState.length == 2, "基线行缺少状态： " + line);
            String busSpec = parts[2].trim();
            assertTrue(busSpec.startsWith("buses="), "基线行缺少 buses= 段： " + line);
            List<String> buses = new ArrayList<>();
            for (String part : busSpec.substring("buses=".length()).split(",")) {
                if (!part.isBlank() && !"-".equals(part.trim())) buses.add(part.trim());
            }
            result.computeIfAbsent(domain, k -> new LinkedHashMap<>())
                    .put(nodeAndState[0].trim(), new NodeState(nodeAndState[1].trim(), buses));
        }
        return result;
    }
}
