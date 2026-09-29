package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Assumptions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Declared-event-surface golden (tickets 23/24/27 shared declaration gap, extended 2026-09-29 with
 * ticket 26's KeyBindEvents): freezes the TypeScript declarations of the recipe/data, gameplay,
 * client GUI/render and keybind event domains, derived from <b>this node's real registration entry
 * points</b> through the same production chain the probe uses — {@link NekoScriptCatalog#events}
 * (catalog derivation) → {@link EventDeclarationGenerator} (TS declaration renderer).
 *
 * <p><b>Why this golden lives in the node tree.</b> The recorded gap (ticket 23 AC1, ticket 24
 * AC8, ticket 27 AC7) is zero hits for these domains in the common declaration goldens
 * ({@code api-manifest-core.json}, probe {@code *.expected.d.ts}, {@code declaration-parity.txt}).
 * That is an input-set property, not staleness: those derivations reflect the portable-core
 * contract only ({@code CoreManagedApiBootstrap.buildContract}: 7 facades + data types +
 * {@code ScriptEventRegistrationEvent}), and the engine modules must stay free of Minecraft
 * dependencies (ADR-0007), so the MC-facing event families can never enter them. The runtime
 * reflection that does see the families ({@code EventContractReflector} from
 * {@code IPluginRuntime.eventGroups()}) feeds callback-schema validation only, never a
 * declaration golden. This test closes that wiring gap at the level where the families are
 * reachable: per node, from the real registration hooks.
 *
 * <p><b>Input</b> (identical to {@code EventSurfaceDomainGateTest}, shared through
 * {@link EventRegistrationSurfaces}): every {@code @RegisterNekoJSPlugin} class on the node
 * classpath (plus the fabric built-in list), driven through {@code registerEvents} and
 * {@code registerClientEvents} into fresh registries. Only the four ticket domains are
 * frozen: {@code ServerEvents}/{@code RecipeViewerEvents} (23), the eight gameplay families
 * (24), {@code ClientEvents} (27), {@code KeyBindEvents} (26 — its direct-registration member
 * {@code register} freezes the same way as {@code ClientEvents.hudRender}: both are
 * {@code EventBusJS} members of the group, and the family freezes what the renderer declares,
 * not the call semantics). Other domains keep their existing gates — this golden does not
 * become a second cross-domain baseline. The ticket-29 assets faces split at this boundary:
 * the event members {@code ClientEvents.generateAssets}/{@code lang} are already frozen through
 * {@code ClientEvents}, while the {@code Assets} typed binding and the plugin-only
 * {@code generatedLangs()} are not event-group members ({@link NekoScriptCatalog#events} derives
 * from {@code eventGroups()} only) and stay outside this family's charter.
 *
 * <p><b>Goldens</b>: {@code src/test/resources/golden/events-declared/<node>.<side>-events.d.ts}
 * with one file per script side ({@code startup}/{@code server}/{@code client}) — production
 * renders one declaration document per side, and each golden stays a standalone valid
 * {@code .d.ts}. One set per node: the registered surface genuinely differs across nodes (e.g.
 * fabric {@code ClientEvents} has no render members yet, STARTUP-only groups such as
 * {@code CapabilityEvents} appear in the startup file only). Ordinary runs are read-only;
 * regeneration runs through the shared explicit workflow:
 * {@code ./gradlew :<node>:platformGateTest -Dnekojs.golden.regenerate=true}, then review the
 * old/new diff per managed-surface REGENERATE.md §3.
 */
@Tag("platform-gate")
class DeclaredEventSurfaceGoldenTest {

    /** In-scope groups, grouped by owning ticket domain. Order is part of the golden layout. */
    private static final List<String> RECIPE_DATA_GROUPS = List.of("ServerEvents", "RecipeViewerEvents");
    private static final List<String> GAMEPLAY_GROUPS = List.of(
            "BlockEvents", "ItemEvents", "LevelEvents", "PlayerEvents",
            "CommandEvents", "CapabilityEvents", "GoalEvents", "EntityEvents");
    private static final List<String> CLIENT_GUI_RENDER_GROUPS = List.of("ClientEvents");
    /** Ticket 26 client input: the keybind group (the ClientEvents members of 26 are in scope via ClientEvents). */
    private static final List<String> CLIENT_INPUT_HUD_GROUPS = List.of("KeyBindEvents");

    private static final List<String> IN_SCOPE;
    static {
        List<String> all = new ArrayList<>();
        all.addAll(RECIPE_DATA_GROUPS);
        all.addAll(GAMEPLAY_GROUPS);
        all.addAll(CLIENT_GUI_RENDER_GROUPS);
        all.addAll(CLIENT_INPUT_HUD_GROUPS);
        IN_SCOPE = List.copyOf(all);
    }

    private static final String GOLDEN_DIR = "/golden/events-declared/";
    private static final String PRESENCE_FIXTURE = "/nekojs/platform-gates/event-surface-domains.txt";
    private static final String REPORT_DIR = "build/nekojs-gates";

    @org.junit.jupiter.api.BeforeAll
    static void initPlatformStub() {
        // EventDeclarationGenerator 的 import 过滤（ProbeConfigLoader.isRelevantClassDefault）读
        // Platform.defaultScanPackages()——裸 JVM 需要先装一个最小 Platform 桩（同 Ticket24 的
        // @BeforeAll 模式；已初始化时复用，不重复 init）。scan packages 必须镜像本 loader 真实
        // Platform 实现（NeoForgePlatform/FabricPlatform），否则 neoforge 侧 import 集合会随
        // 「谁先初始化了 Platform」漂移——golden 就不是确定性的了。
        final String loader = EventRegistrationSurfaces.loaderId();
        final List<String> scanPackages = "fabric".equals(loader)
                ? List.of("net.minecraft")
                : List.of("net.minecraft", "net.neoforged", "com.mojang");
        try {
            com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                @Override public boolean isClient() { return false; }
                @Override public boolean isDevelopment() { return true; }
                @Override public String getMcVersion() { return "test"; }
                @Override public java.nio.file.Path getGameDir() {
                    return com.tkisor.nekojs.TestGameDirs.unique("nekojs-declared-events-golden");
                }
                @Override public Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() { return Map.of(); }
                @Override public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) { return null; }
                @Override public String getLoaderId() { return "test"; }
                @Override public String getLoaderVersion() { return "0"; }
                @Override public List<String> defaultScanPackages() { return scanPackages; }
            });
        } catch (IllegalStateException alreadyInitialized) {
            // same-JVM reuse of an already initialized platform stub is fine
        }
    }

    @Test
    void declaredEventSurfaceMatchesFrozenGoldens() throws Exception {
        String node = EventRegistrationSurfaces.nodeId();
        assertTrue(node != null && !node.isBlank(),
                "golden 必须知道自己在哪个节点运行（platformGateTest 注入 -Dnekojs.node=<node>）");
        String loader = EventRegistrationSurfaces.loaderId();
        assertFalse("unknown".equals(loader), "节点必须能判定自己的 loader");

        // 跨节点在场事实复用票 33 只读基线：本节点「应注册」的在册组 = present 行；not-verified
        // 的组（如 fabric 的 CapabilityEvents）不属于本节点声明面，不进 golden 也不算缺失。
        Map<String, Boolean> presence = readPresenceFixture(node);
        List<String> expectedGroups = IN_SCOPE.stream()
                .filter(group -> presence.getOrDefault(group, false)).toList();
        assertFalse(expectedGroups.isEmpty(), "票 33 基线没有把任何在册组标记为本节点 present");

        Map<String, EventGroup> registered = driveRealRegistration(loader);
        assertFalse(registered.isEmpty(), "节点真实注册入口没有产出任何事件组");

        List<String> missing = expectedGroups.stream().filter(g -> !registered.containsKey(g)).toList();
        assertTrue(missing.isEmpty(),
                "四个票据域的事件组在本节点注册面缺失（成员删除＝契约变更；若整组移除请更新票 33 基线、"
                        + "本测试与 golden）: " + missing);
        List<String> undeclared = IN_SCOPE.stream()
                .filter(group -> registered.containsKey(group) && Boolean.FALSE.equals(presence.get(group))).toList();
        assertTrue(undeclared.isEmpty(),
                "运行时注册了在册组但票 33 基线记为 not-verified/未登记（先更新基线再生成 golden）: "
                        + undeclared);

        // Production renders one declaration document per script side (startup/server/client);
        // each golden file stays a standalone, valid .d.ts (no cross-side concatenation).
        Map<ScriptType, String> rendered = new TreeMap<>();
        int totalMembers = 0;
        for (ScriptType side : List.of(ScriptType.STARTUP, ScriptType.SERVER, ScriptType.CLIENT)) {
            String declaration = render(registered, expectedGroups, side);
            assertFalse(declaration.isBlank(), side + " 侧声明不能为空");
            rendered.put(side, declaration);
            totalMembers += countMembers(declaration, expectedGroups);
        }

        // 可读性断言：每个在册组都必须渲染出 namespace（防“空 golden 全绿”）。STARTUP 组
        // （如 CapabilityEvents/GoalEvents）只出现在 startup 侧文件，SERVER/CLIENT 同理按 side 过滤。
        String all = String.join("\n", rendered.values());
        for (String group : expectedGroups) {
            assertTrue(all.contains("namespace " + group + " {"),
                    "组 " + group + " 未渲染为 TS namespace（本节点注册面有它）");
        }
        assertTrue(totalMembers > 0, "声明成员数为 0——golden 会退化为空壳");

        emitReport(node, registered, expectedGroups, rendered);

        if (regenerateEnabled()) {
            Path dir = sharedGoldenDir();
            Files.createDirectories(dir);
            for (Map.Entry<ScriptType, String> entry : rendered.entrySet()) {
                Files.writeString(dir.resolve(goldenName(node, entry.getKey())), entry.getValue(),
                        StandardCharsets.UTF_8);
            }
            Assumptions.abort("goldens regenerated for node " + node + "; review the diff before committing");
        }

        for (Map.Entry<ScriptType, String> entry : rendered.entrySet()) {
            String golden = GOLDEN_DIR + goldenName(node, entry.getKey());
            assertEquals(readGolden(golden), normalize(entry.getValue()),
                    entry.getKey() + " 侧事件声明与 golden 不一致（" + golden + "）。"
                            + "事件面变更是契约变更：先改实现/注册入口，再以 -Dnekojs.golden.regenerate=true "
                            + "重跑 platformGateTest 再生成，并按 REGENERATE.md §3 留旧新 diff 与审阅记录");
        }
    }

    // ---- 真实注册面驱动（与 EventSurfaceDomainGateTest 同一发现/调用输入） ----

    /**
     * Drives every contributor's registration hooks and merges the registered groups.
     * Registration errors are hard failures (the golden must not silently shrink).
     */
    private static Map<String, EventGroup> driveRealRegistration(String loader) throws IOException {
        List<String> contributors = EventRegistrationSurfaces.pluginContributors(loader);
        assertFalse(contributors.isEmpty(), "节点必须能发现事件注册入口（node=" + loader + "）");

        Map<String, EventGroup> merged = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        for (String className : contributors) {
            for (String hook : List.of("registerEvents", "registerClientEvents")) {
                EventRegistrationSurfaces.Registration registration =
                        EventRegistrationSurfaces.invokeRegistration(className, hook);
                if (registration.error() != null) {
                    failures.add(className + "#" + hook + ": " + registration.error());
                    continue;
                }
                merged.putAll(registration.groups());
            }
        }
        assertTrue(failures.isEmpty(),
                "事件注册入口抛错，声明面输入不完整（golden 不允许在缺证据时冻结）:\n"
                        + String.join("\n", failures));
        return merged;
    }

    private static String render(Map<String, EventGroup> groups, List<String> expectedGroups, ScriptType side) {
        Map<String, EventGroup> inScope = new TreeMap<>();
        for (String name : expectedGroups) {
            EventGroup group = groups.get(name);
            if (group != null) inScope.put(name, group);
        }
        List<EventCatalogEntry> entries = NekoScriptCatalog.events(new StubRuntime(inScope), side);
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        return new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(entries, side);
    }

    private static int countMembers(String declaration, List<String> groups) {
        int count = 0;
        for (String group : groups) {
            int start = declaration.indexOf("namespace " + group + " {");
            if (start < 0) continue;
            int end = declaration.indexOf("\n    }", start);
            String body = end < 0 ? declaration.substring(start) : declaration.substring(start, end);
            for (String line : body.split("\n")) {
                if (line.trim().startsWith("function ")) count++;
            }
        }
        return count;
    }

    // ---- golden 支撑 ----

    private static String goldenName(String node, ScriptType side) {
        return node + "." + side.name.toLowerCase() + "-events.d.ts";
    }

    private static boolean regenerateEnabled() {
        return Boolean.getBoolean("nekojs.golden.regenerate");
    }

    /** Shared version-tree root, injected by the platformGateTest wiring (rootProject dir). */
    private static Path sharedGoldenDir() {
        String root = System.getProperty("nekojs.test.sharedTree");
        assertTrue(root != null && !root.isBlank(),
                "再生成模式需要 -Dnekojs.test.sharedTree=<共享树根>（platformGateTest 注入）");
        return Path.of(root).resolve("src/test/resources/golden/events-declared");
    }

    private static String readGolden(String name) {
        try (InputStream in = DeclaredEventSurfaceGoldenTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("缺少 golden " + name
                        + "。首次落盘/有意变更请跑：./gradlew :<node>:platformGateTest"
                        + " -Dnekojs.golden.regenerate=true，审阅 diff 后提交（REGENERATE.md §3）");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException error) {
            throw new IllegalStateException("读取 golden " + name + " 失败: " + error.getMessage(), error);
        }
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n");
    }

    // ---- 逐项输出落盘（与票 33 gate 同目录，供 CI 汇总） ----

    private static void emitReport(String node, Map<String, EventGroup> registered,
                                   List<String> expectedGroups, Map<ScriptType, String> rendered) throws IOException {
        Path dir = Path.of(REPORT_DIR);
        Files.createDirectories(dir);
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"check\": \"declared-event-surface\",\n");
        json.append("  \"owner\": \"Managed Surface/Probe owner; build convention owner (wiring)\",\n");
        json.append("  \"node\": \"").append(node).append("\",\n");
        json.append("  \"scope\": \"tickets 23/24/26/27 domains\",\n");
        json.append("  \"groups\": [");
        json.append(String.join(",", expectedGroups.stream().map(g -> "\"" + g + "\"").toList()));
        json.append("],\n  \"registeredInScope\": ").append(
                expectedGroups.stream().filter(registered::containsKey).count()).append(",\n");
        for (Map.Entry<ScriptType, String> entry : rendered.entrySet()) {
            json.append("  \"").append(entry.getKey().name.toLowerCase()).append("Members\": ")
                    .append(countMembers(entry.getValue(), expectedGroups)).append(",\n");
        }
        json.delete(json.length() - 2, json.length()).append("\n}\n");
        Files.writeString(dir.resolve("declared-event-surface-" + node + ".json"), json.toString());
    }

    /**
     * Reads the ticket-33 cross-node presence baseline (the reviewed snapshot of which domains
     * each node really registers) and returns {@code group -> present?} for this node. Null for
     * groups the baseline does not mention at all for this node.
     */
    private static Map<String, Boolean> readPresenceFixture(String node) throws IOException {
        try (InputStream in = DeclaredEventSurfaceGoldenTest.class.getResourceAsStream(PRESENCE_FIXTURE)) {
            assertNotNull(in, "缺少票 33 在场基线 " + PRESENCE_FIXTURE);
            Map<String, Boolean> result = new LinkedHashMap<>();
            for (String raw : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length < 2) continue;
                String group = parts[0].trim();
                String[] nodeAndState = parts[1].split("=", 2);
                if (nodeAndState.length != 2 || !node.equals(nodeAndState[0].trim())) continue;
                result.put(group, "present".equals(nodeAndState[1].trim()));
            }
            return result;
        }
    }

    /** Catalog derivation needs only the event-group view (same stub shape as ticket 24's test). */
    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> eventGroups;

        StubRuntime(Map<String, EventGroup> eventGroups) {
            this.eventGroups = eventGroups;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return eventGroups; }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }
}
