package com.tkisor.nekojs.probe.backend.python;

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
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeGenerator;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PythonProbeBackend} 端到端：手构一份跨包引用的 IR，跑 generate()，验证模块组织、
 * 跨包 import、祖先 {@code __init__.pyi}、绑定入口、py.typed 均正确产出。
 */
class PythonProbeBackendIntegrationTest {

    /** ScriptType 静态初始化需要 Platform（NekoJSPaths.get），测试先装 TestIPlatform。 */
    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void generate_producesStubsAndCrossPackageImports(@TempDir Path temp) throws Exception {
        // IR：pkg.a.Foo.getBar() 返回 pkg.b.Bar（跨包引用）
        TypeDecl foo = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.Foo");
        MethodDecl getBar = new MethodDecl("getBar");
        getBar.returnType = new TypeSlot(null, ApiTypeRef.symbol(new ApiSymbolId("java", "pkg.b.Bar")));
        foo.methods.add(getBar);
        TypeDecl bar = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.b.Bar");
        List<TypeDecl> ir = List.of(foo, bar);

        NekoScriptCatalogSnapshot snapshot = emptySnapshot();
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of("pkg.a", "pkg.b"), List.of(), List.of(), List.of(), 5, "SMART"));
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());  // 供 pyrightconfig 写入
        Path outputDir = temp.resolve("probe-python");
        ProbeContext ctx = new ProbeContext.Of(snapshot, List.of(), cfg, paths, "python", outputDir, ir);

        ProbeGenerator.GenerateResult res = new PythonProbeBackend().generate(ctx);
        assertTrue(res.success(), "generate failed: " + res.message());

        // pkg.a 模块：跨包 import Bar + class Foo + 方法
        String a = Files.readString(outputDir.resolve("nekojs/_java/pkg/a/__init__.pyi"));
        assertTrue(a.contains("from nekojs._java.pkg.b import Bar"), "cross-package import missing: " + a);
        assertTrue(a.contains("class Foo:"), a);
        assertTrue(a.contains("def getBar(self) -> Bar"), "return type should resolve to Bar: " + a);

        // pkg.b 模块：class Bar
        String b = Files.readString(outputDir.resolve("nekojs/_java/pkg/b/__init__.pyi"));
        assertTrue(b.contains("class Bar:"), b);

        // 祖先包 marker
        assertTrue(Files.exists(outputDir.resolve("nekojs/_java/pkg/__init__.pyi")), "ancestor __init__.pyi missing");
        assertTrue(Files.exists(outputDir.resolve("nekojs/_java/__init__.pyi")), "_java root marker missing");

        // 绑定入口（空绑定 → __all__ = []）+ py.typed + README
        String init = Files.readString(outputDir.resolve("nekojs/__init__.pyi"));
        assertTrue(init.contains("__all__"), init);
        assertTrue(Files.exists(outputDir.resolve("nekojs/py.typed")));
        assertTrue(Files.exists(outputDir.resolve("nekojs/README.md")));
    }

    @Test
    void generate_emptyIr_failsCleanly(@TempDir Path temp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                List.of(), List.of(), List.of(), List.of(), 5, "SMART"));
        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(), cfg, paths, "python",
                temp.resolve("out"), List.of());
        ProbeGenerator.GenerateResult res = new PythonProbeBackend().generate(ctx);
        assertFalse(res.success(), "empty IR should fail cleanly, not produce garbage");
    }

    // -------------------- B2：绑定尊重 typeOverride --------------------

    @Test
    void generate_bindingTypeOverrideResolvesCollectedClass(@TempDir Path temp) throws Exception {
        // IR 收集了简单名为 NekoItemHelper 的类；绑定的 javaType 未被收集（会掉到 Any），
        // typeOverride 命中该简单名时应优先使用它
        TypeDecl helper = new TypeDecl(TypeDecl.Kind.CLASS, null, "com.example.NekoItemHelper");
        List<TypeDecl> ir = List.of(helper);

        BindingCatalogEntry item = new BindingCatalogEntry(
                "Item", ScriptType.SERVER, String.class, false, false, true,
                "NekoItemHelper", "item binding", List.of(), List.of());
        NekoScriptCatalogSnapshot snapshot = snapshotWith(List.of(item), List.of(), List.of());

        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of("com.example"));
        assertTrue(res.success(), "generate failed: " + res.message());

        String init = Files.readString(temp.resolve("probe-python/nekojs/__init__.pyi"));
        assertTrue(init.contains("Item: NekoItemHelper"), "binding should resolve override class: " + init);
        assertTrue(init.contains("from nekojs._java.com.example import NekoItemHelper"),
                "override class should be imported: " + init);
    }

    // -------------------- B3：事件声明 .pyi + 事件组绑定 --------------------

    @Test
    void generate_events_produceEventStubsAndGroupBindings(@TempDir Path temp) throws Exception {
        TypeDecl event = new TypeDecl(TypeDecl.Kind.CLASS, FakeProbeEvent.class, FakeProbeEvent.class.getName());
        List<TypeDecl> ir = List.of(event);

        // dispatch 型事件（recipes，key = FakeProbeEvent → 有适配器别名时放宽为 FakeProbeEvent_）
        EventCatalogEntry recipes = new EventCatalogEntry(
                "ServerEvents", "recipes", ScriptType.SERVER, FakeProbeEvent.class, FakeProbeEvent.class,
                false, true, "ServerEvents.recipes(event => {\n  $0\n})");
        // 无 dispatch key 的普通事件（另一组、另一 side）
        EventCatalogEntry tick = new EventCatalogEntry(
                "ClientEvents", "tick", ScriptType.CLIENT, FakeProbeEvent.class, null,
                false, false, "ClientEvents.tick(event => {\n  $0\n})");

        BindingCatalogEntry serverEvents = new BindingCatalogEntry(
                "ServerEvents", ScriptType.SERVER, null, false, false, true,
                null, null, List.of(), List.of());

        AdapterCatalogEntry adapter = new AdapterCatalogEntry(
                FakeProbeEvent.class, List.of(AdapterInputShape.string(), AdapterInputShape.self()),
                ConversionPrecedence.HIGH, Optional.empty());

        NekoScriptCatalogSnapshot snapshot = snapshotWith(
                List.of(serverEvents), List.of(recipes, tick), List.of(adapter));

        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of());
        assertTrue(res.success(), "generate failed: " + res.message());

        Path out = temp.resolve("probe-python");
        // server 事件模块：组类 + 普通签名 + dispatch key 重载（key 用适配器别名放宽）
        String server = Files.readString(out.resolve("nekojs/_events/server/__init__.pyi"));
        assertTrue(server.contains("class ServerEventsType:"), server);
        assertTrue(server.contains("def recipes(handler: Callable[[FakeProbeEvent], None]) -> None: ..."), server);
        assertTrue(server.contains("def recipes(extra: FakeProbeEvent_, handler: Callable[[FakeProbeEvent], None]) -> None: ..."),
                "dispatch overload should widen key via adapter alias: " + server);
        assertTrue(server.contains("from nekojs._java.com.tkisor.nekojs.probe.backend.python import FakeProbeEvent"), server);
        assertTrue(server.contains("import FakeProbeEvent_"), "alias import for widened key missing: " + server);

        // client 事件模块
        String client = Files.readString(out.resolve("nekojs/_events/client/__init__.pyi"));
        assertTrue(client.contains("class ClientEventsType:"), client);
        assertTrue(client.contains("def tick(handler: Callable[[FakeProbeEvent], None]) -> None: ..."), client);
        // 命名空间 marker
        assertTrue(Files.exists(out.resolve("nekojs/_events/__init__.pyi")), "_events marker missing");

        // 绑定入口：ServerEvents 绑定指向 Type 类；ClientEvents 无绑定条目也补发入口
        String init = Files.readString(out.resolve("nekojs/__init__.pyi"));
        assertTrue(init.contains("from nekojs._events.server import ServerEventsType"), init);
        assertTrue(init.contains("from nekojs._events.client import ClientEventsType"), init);
        assertTrue(init.contains("ServerEvents: ServerEventsType"), init);
        assertTrue(init.contains("ClientEvents: ClientEventsType"), init);
        assertTrue(init.contains("\"ServerEvents\""), "event groups should be in __all__: " + init);
        assertTrue(init.contains("\"ClientEvents\""), "event groups should be in __all__: " + init);
    }

    // -------------------- B4：适配器输入别名 --------------------

    @Test
    void generate_adapterAlias_emitsInPackageModule(@TempDir Path temp) throws Exception {
        TypeDecl target = new TypeDecl(TypeDecl.Kind.CLASS, FakeProbeEvent.class, FakeProbeEvent.class.getName());
        List<TypeDecl> ir = List.of(target);

        AdapterCatalogEntry adapter = new AdapterCatalogEntry(
                FakeProbeEvent.class, List.of(AdapterInputShape.string(), AdapterInputShape.self()),
                ConversionPrecedence.HIGH, Optional.empty());
        NekoScriptCatalogSnapshot snapshot = snapshotWith(List.of(), List.of(), List.of(adapter));

        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of());
        assertTrue(res.success(), "generate failed: " + res.message());

        // 别名声明在目标类的包模块里
        String module = Files.readString(temp.resolve(
                "probe-python/nekojs/_java/com/tkisor/nekojs/probe/backend/python/__init__.pyi"));
        assertTrue(module.contains("FakeProbeEvent_ = FakeProbeEvent | str"),
                "adapter input alias missing: " + module);
    }

    // -------------------- helpers --------------------

    private static NekoScriptCatalogSnapshot snapshotWith(List<BindingCatalogEntry> bindings,
                                                          List<EventCatalogEntry> events,
                                                          List<AdapterCatalogEntry> adapters) {
        return new NekoScriptCatalogSnapshot(
                List.of(), bindings, events, adapters, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }

    private static ProbeGenerator.GenerateResult runGenerate(Path temp, NekoScriptCatalogSnapshot snapshot,
                                                             List<TypeDecl> ir, List<String> includes) throws Exception {
        ProbeConfig cfg = new ProbeConfig(true, ".neko_probe", new ProbeConfig.ScanConfig(
                includes, List.of(), List.of(), List.of(), 5, "SMART"));
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Files.createDirectories(paths.root());  // 供 pyrightconfig 写入
        Path outputDir = temp.resolve("probe-python");
        ProbeContext ctx = new ProbeContext.Of(snapshot, List.of(), cfg, paths, "python", outputDir, ir);
        return new PythonProbeBackend().generate(ctx);
    }

    private static NekoScriptCatalogSnapshot emptySnapshot() {
        return new NekoScriptCatalogSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of(), List.of());
    }
}

/** 集成测试用事件类：稳定 FQN，供事件声明与适配器别名断言。 */
class FakeProbeEvent {}
