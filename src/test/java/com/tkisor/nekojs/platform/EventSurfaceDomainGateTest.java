package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.event.EventGroup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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
 * Event/surface coverage gate: the second of the two non-processor replacement gates for the
 * deferred Fabric {@code common-api-processor}. It drives the <b>node's real event registration entry points</b>
 * and enumerates the domains and bus members registered into
 * {@link com.tkisor.nekojs.api.event.EventGroupRegistry}.
 *
 * <p><b>Owner</b>: the Managed Surface/Probe owner owns the coverage ledger and the contract
 * fixture; the build convention owner owns the wiring. <b>Input</b>: the core plugin actually
 * compiled onto this node's classpath (neoforge={@code NekoJSCorePlugin}, fabric={@code
 * FabricCorePlugin}, i.e. the node event registration entry points of
 * {@code @RegisterNekoJSPlugin(priority = CORE_PRIORITY)}) and its
 * {@code registerEvents}/{@code registerClientEvents} hooks, registered into a
 * {@link com.tkisor.nekojs.api.event.EventGroupRegistry.Impl} created by this test run (it reads
 * no process-wide global and depends on no other test's class-initialization side effects).
 * <b>Per-item output</b>: every registered domain, holder entry point, bus member and whether
 * client events are observable, compared against the read-only baseline
 * {@code /nekojs/platform-gates/event-surface-domains.txt}; the run also writes
 * {@code build/nekojs-gates/event-surface-<loader>.json} for CI to fold into the same report.
 * <b>Failure diagnosis</b>: {@code missing-binding} (a promised domain/bus was not registered at
 * runtime on this node), {@code undocumented-domain} (registered but not in the baseline),
 * {@code unexpected-domain}, {@code undeclared-evidence}, {@code client-events-not-verified}.
 *
 * <p><b>Input limits (stated, not overstated)</b>: this gate covers only the domains registered by
 * the core plugin entry points. Domains registered by other plugins (for example
 * {@code DynamicRegistryPlugin}, {@code RecipeViewerEventsPlugin}) are not gated here; the
 * baseline must record them as {@code not-verified} and keep the matching domain acceptance
 * blocked. They must not be written as {@code unavailable} or {@code partial}.
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
        String loader = EventRegistrationSurfaces.loaderId();
        assertTrue(!"unknown".equals(loader), "节点必须能判定自己的 loader（FabricPlatform/NeoForgePlatform）");
        String node = EventRegistrationSurfaces.nodeId();
        assertTrue(node != null && !node.isBlank(),
                "gate 必须知道自己在哪个节点运行（Gradle 注入 -Dnekojs.node=<node>）");

        Map<String, Registration> registrations = new LinkedHashMap<>();
        List<String> gaps = new ArrayList<>();
        Map<String, String> rows = new TreeMap<>();

        List<String> contributors = EventRegistrationSurfaces.pluginContributors(loader);
        assertTrue(!contributors.isEmpty(),
                "节点必须能发现事件注册入口（neoforge 注解扫描 / fabric 内置清单）（node=" + loader + "）");

        // Each plugin gets its own registry: this matches bootstrap's collection semantics (a duplicate
        // group name is settled by the merge policy) and avoids the duplicate bus a test would create
        // by registering the same static GROUP instance twice.
        for (String className : contributors) {
            record(registrations, className,
                    EventRegistrationSurfaces.invokeRegistration(className, REGISTER_EVENTS),
                    "server", gaps, node);
        }

        // Client-events channel: MC client classes may fail to initialize in a bare JVM, so a failure must
        // be recorded explicitly and never silently dropped.
        Map<String, String> clientFailures = new LinkedHashMap<>();
        for (String className : contributors) {
            EventRegistrationSurfaces.Registration invocation =
                    EventRegistrationSurfaces.invokeRegistration(className, REGISTER_CLIENT_EVENTS);
            if (invocation.error() != null) {
                clientFailures.put(className, invocation.error());
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

    // ---- Driving the registration surface ----

    private record Registration(String key, String group, String holder, String channel, List<String> buses) {}

    private static void record(Map<String, Registration> target, String className,
                               EventRegistrationSurfaces.Registration invocation, String channel,
                               List<String> gaps, String loader) {
        String error = invocation.error();
        if (error != null) {
            gaps.add("registration-failed holder=" + className + " node=" + loader
                    + " channel=" + channel + " owner=build convention owner"
                    + " :: 节点事件注册入口抛错，事件面无法建立：" + error);
            return;
        }
        for (Map.Entry<String, EventGroup> entry : invocation.groups().entrySet()) {
            String groupName = entry.getKey();
            List<String> merged = new ArrayList<>(entry.getValue().viewBuses().keySet().stream().toList());
            Registration existing = target.get(groupName);
            if (existing != null) {
                merged.addAll(existing.buses());
            }
            merged = new ArrayList<>(new TreeSet<>(merged));
            target.put(groupName, new Registration(groupName, groupName, className, channel, merged));
        }
    }

    // ---- Node facts (discovery and driving live in EventRegistrationSurfaces, shared with the golden test) ----

    // ---- Per-item report output ----

    private static void emitReport(String node, Map<String, Registration> registrations,
                                   Map<String, String> clientFailures,
                                   Map<String, String> rows, List<String> gaps) throws IOException {
        String loader = EventRegistrationSurfaces.loaderId();
        Path dir = Path.of(REPORT_DIR);
        Files.createDirectories(dir);
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"check\": \"event-surface\",\n");
        json.append("  \"owner\": \"Managed Surface/Probe owner; build convention owner (wiring)\",\n");
        json.append("  \"node\": \"").append(node).append("\",\n");
        json.append("  \"input\": \"").append(String.join(",", EventRegistrationSurfaces.pluginContributors(loader))).append("\",\n");
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

    // ---- Read-only baseline ----

    /**
     * Baseline format (a cross-node view; the source of truth is each node's core plugin
     * registration surface, the baseline is only a reviewed snapshot):
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
