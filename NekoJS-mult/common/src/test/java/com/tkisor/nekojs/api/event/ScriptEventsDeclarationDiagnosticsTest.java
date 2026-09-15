package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer;
import com.tkisor.nekojs.probe.backend.python.PythonEventRenderer;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 14 AC3：ScriptEvents 动态声明的可诊断失败与声明派生 parity。
 *
 * <p>可诊断失败面：
 * <ul>
 *   <li>注册冲突——组名撞内置事件组 / 撞内置 binding / 跨来源同 key 重复，均为带明确
 *       消息的 {@link IllegalArgumentException}；</li>
 *   <li>非法标识符（group/name 不是 JS identifier）直接拒绝；</li>
 *   <li>未知组/成员——内置组与动态组的脚本侧访问都给出「No such ... bus」错误，
 *       side 不适用给出「not available in SIDE」；</li>
 *   <li>side 过滤——SERVER-target 动态组在 CLIENT 环境不绑定（脚本可见性）。</li>
 * </ul>
 *
 * <p>parity 面：动态事件走与内置事件<strong>同一条</strong> catalog 派生路径
 * （{@link NekoScriptCatalog#events}）产出 {@link EventCatalogEntry}，再分别驱动
 * TS（{@link EventDeclarationGenerator}）与 Python（{@link PythonEventRenderer}）
 * 声明生成——两个后端消费同一 entry 列表，成员形状一致（any payload + post 恰好一次）。
 */
class ScriptEventsDeclarationDiagnosticsTest {

    private ScriptEventsJS scriptEvents;
    private StubRuntime runtime;
    private Context context;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        clearAllDefinitions();
        runtime = new StubRuntime();
        scriptEvents = new ScriptEventsJS();
        scriptEvents.bindRuntime(runtime);
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistryBridge.bind(context);
    }

    @AfterEach
    void tearDown() {
        clearAllDefinitions();
        ScriptContextRegistryBridge.unbind(context);
        context.close();
    }

    private static void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
    }

    // ---- 注册冲突与非法输入：可诊断失败 ----

    @Test
    void groupNameConflictingWithBuiltInEventGroupIsRejected() {
        runtime.eventGroups.put("ServerEvents", EventGroup.of("ServerEvents"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "ServerEvents", "anything", "s.js"));
        assertTrue(ex.getMessage().contains("conflicts with built-in event group"),
                "diagnostic must name the conflicting built-in group: " + ex.getMessage());
    }

    @Test
    void groupNameConflictingWithBuiltInBindingIsRejected() {
        runtime.bindings.put("NativeEvents", Binding.of("NativeEvents", new Object()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "NativeEvents", "anything", "s.js"));
        assertTrue(ex.getMessage().contains("conflicts with built-in binding"),
                "diagnostic must name the conflicting built-in binding: " + ex.getMessage());
    }

    @Test
    void crossSourceDuplicateKeyIsRejectedWithRegistrationKey() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/b.js"));
        assertTrue(ex.getMessage().contains("MyEvents.bossKilled") && ex.getMessage().contains("SERVER"),
                "diagnostic must carry the full registration key: " + ex.getMessage());
    }

    @Test
    void nonIdentifierNamesAreRejected() {
        IllegalArgumentException group = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "Not A Group", "evt", "s.js"));
        assertTrue(group.getMessage().contains("valid JS identifier"), group.getMessage());
        IllegalArgumentException name = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "MyEvents", "not-a-name", "s.js"));
        assertTrue(name.getMessage().contains("valid JS identifier"), name.getMessage());
    }

    @Test
    void reloadCleanupMakesReregistrationPossibleAfterDiagnosableConflict() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");

        // 生产序列（ScriptManager.doLoadScripts）：STARTUP reload 先全量清理定义，
        // 再重放 ScriptEvents.post。未经清理的重复注册（含同来源）在注册入口即被
        // 可诊断拒绝——不会静默叠加第二条定义。
        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js"));
        assertTrue(conflict.getMessage().contains("MyEvents.bossKilled"),
                "diagnostic must carry the full registration key: " + conflict.getMessage());

        DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(scriptEvents);
        bridge.setPluginRuntime(runtime);
        bridge.clearListeners(ScriptType.STARTUP);
        assertEquals(0, NekoScriptCatalog.events(runtime).stream()
                .filter(e -> e.group().equals("MyEvents")).count(),
                "full STARTUP sweep must remove the dynamic definitions");

        // 清理后同 key 重注册成功，目录仍恰好一条（reload 不双注册）
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        assertEquals(1, NekoScriptCatalog.events(runtime).stream()
                .filter(e -> e.group().equals("MyEvents")).count(),
                "post-cleanup re-registration yields exactly one catalog entry");
    }

    // ---- 未知组 / 未知成员 / side 错误：脚本侧可诊断 ----

    @Test
    void unknownDynamicGroupMemberIsDiagnosed() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        ScriptEventGroupJS group = new ScriptEventGroupJS("MyEvents",
                ScriptEventRegistry.groupsFor(ScriptType.SERVER).get("MyEvents"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> group.getMember("nope"));
        assertEquals("No such script event bus: MyEvents.nope", ex.getMessage());
        assertFalse(group.hasMember("nope"));
        assertTrue(group.hasMember("bossKilled"));
    }

    @Test
    void builtInGroupUnknownMemberAndWrongSideAreDiagnosed() {
        EventGroup builtIn = EventGroup.of("BuiltInEvents");
        builtIn.server("tick", String.class);
        builtIn.client("render", String.class);
        EventGroupJS serverView = new EventGroupJS(builtIn, ScriptType.SERVER);

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> serverView.getMember("nope"));
        assertEquals("No such event bus: BuiltInEvents.nope", unknown.getMessage());

        IllegalArgumentException wrongSide = assertThrows(IllegalArgumentException.class,
                () -> serverView.getMember("render"));
        assertEquals("Event 'BuiltInEvents.render' not available in SERVER", wrongSide.getMessage());
    }

    @Test
    void serverTargetDynamicGroupIsBoundOnlyOnServerSide() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        Recorder recorder = new Recorder();
        context.getBindings("js").putMember("recorder", recorder);
        DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(scriptEvents);
        bridge.setPluginRuntime(runtime);

        Value serverBindings = context.getBindings("js");
        bridge.bindEvents(serverBindings, ScriptType.SERVER);
        assertTrue(serverBindings.hasMember("MyEvents"),
                "SERVER-target dynamic group is visible to server scripts");
        // 监听 + 触发的外部行为（ScriptEventBusJS：调用即监听，post 即触发；
        // post 的 JS 对象经 as(Object.class) 成 PolyglotMap，回传 JS 后按属性读取）
        Value bus = serverBindings.getMember("MyEvents").getMember("bossKilled");
        bus.execute(context.eval("js",
                "(payload) => recorder.ledger(payload ? payload.boss : null)"));
        bus.getMember("post").execute(context.eval("js", "({ boss: 'ender_dragon' })"));
        assertEquals("ender_dragon", recorder.lastBoss,
                "declared dynamic event delivers listen + post end to end");

        try (Context clientContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistryBridge.bind(clientContext);
            Value clientBindings = clientContext.getBindings("js");
            bridge.bindEvents(clientBindings, ScriptType.CLIENT);
            assertFalse(clientBindings.hasMember("MyEvents"),
                    "SERVER-target dynamic group must not be bound in the CLIENT environment");
            ScriptContextRegistryBridge.unbind(clientContext);
        }
    }

    // ---- TS / Python declaration parity：同一 catalog 派生路径 ----

    @Test
    void runtimeMembersAndDeclarationsDeriveFromTheSameCatalogEntries() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        List<EventCatalogEntry> entries = NekoScriptCatalog.events(runtime);
        assertEquals(List.of("MyEvents.bossKilled"), entries.stream()
                .map(e -> e.group() + "." + e.name()).toList());
        assertTrue(entries.getFirst().scriptDefined());

        // 同一 entry 列表 → TS 声明
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String ts = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(entries, ScriptType.SERVER);
        // 同一 entry 列表 → Python 声明
        String py = new PythonEventRenderer(new ApiTypeRefPyRenderer(Set.of()), Set.of())
                .render(ScriptType.SERVER, entries, null);

        // TS：any payload + post 恰好一次
        assertEquals(1, countOccurrences(ts, "function bossKilled(handler: ((payload: any) => void)): void;"),
                "TS declares the script event listener exactly once");
        assertEquals(1, countOccurrences(ts, "function post(payload?: any): void;"),
                "TS declares the script event post member exactly once");
        assertFalse(ts.contains("$Object"), "no java.lang.Object leak as payload");

        // Python：Any payload + post 恰好一次（与 TS 同一 catalog 条目派生）
        assertEquals(1, countOccurrences(py,
                "def __call__(self, handler: Callable[[Any], None]) -> None: ..."),
                "Python declares the script event listener exactly once");
        assertEquals(1, countOccurrences(py,
                "def post(self, payload: Any = ...) -> None: ..."),
                "Python declares the script event post member exactly once (parity with TS/runtime)");
        assertTrue(py.contains("bossKilled"), "Python keeps the runtime member name");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    // ---- 脚本侧 ledger（监听回调记录） ----

    public static final class Recorder {
        public volatile String lastBoss;

        public void ledger(String boss) {
            lastBoss = boss;
        }
    }

    // ---- 测试基建 ----

    /** ScriptContextRegistry 的窄封装（避免测试类直接 import 内部 registry）。 */
    private static final class ScriptContextRegistryBridge {
        static void bind(Context ctx) {
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(ctx, ScriptType.SERVER);
        }

        static void unbind(Context ctx) {
            com.tkisor.nekojs.script.ScriptContextRegistry.unbind(ctx);
        }
    }

    private static final class StubRuntime implements IPluginRuntime {
        final Map<String, Binding> bindings = new java.util.HashMap<>();
        final Map<String, EventGroup> eventGroups = new java.util.LinkedHashMap<>();

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return bindings;
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return eventGroups;
        }

        @Override
        public List<JSTypeAdapter<?>> adapters() {
            return List.of();
        }

        @Override
        public List<TypeDocCatalogEntry> typeDocs() {
            return List.of();
        }

        @Override
        public List<ManualDeclarationCatalogEntry> manualDeclarations() {
            return List.of();
        }

        @Override
        public Map<String, String> nodeModules() {
            return Map.of();
        }

        @Override
        public Map<String, RecipeNamespaceEntry> recipeNamespaces() {
            return Map.of();
        }

        @Override
        public void beforeRecipeLoading(RecipeLifecycleContext context) {}

        @Override
        public void afterRecipes(RecipeLifecycleContext context) {}

        @Override
        public void fireInit() {}

        @Override
        public void fireInitStartup() {}

        @Override
        public void fireAfterInit() {}

        @Override
        public void fireBeforeScriptsLoaded(ScriptType type) {}

        @Override
        public void fireAfterScriptsLoaded(ScriptType type) {}

        @Override
        public ApiRuntimeView apiRuntime(EnvironmentKey environment) {
            return null;
        }

        @Override
        public Object managedApiImplementation(ApiSymbolId globalId) {
            return null;
        }
    }
}
