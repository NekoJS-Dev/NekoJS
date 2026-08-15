package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.RegistryTypeCatalogEntry;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.EditorConfigContributor;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.probe.ProbeContext;
import com.tkisor.nekojs.probe.ProbeGenerator;
import com.tkisor.nekojs.probe.events.Snippet;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
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

        // 绑定入口：ServerEvents 绑定指向 Type 类；ClientEvents 无绑定条目也补发入口。
        // 未覆盖组名取 ScriptType.all() 中第一个匹配 side（client 事件也匹配 startup，startup 版本是超集）
        String init = Files.readString(out.resolve("nekojs/__init__.pyi"));
        assertTrue(init.contains("from nekojs._events.server import ServerEventsType"), init);
        assertTrue(init.contains("from nekojs._events.startup import ClientEventsType"), init);
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

    // -------------------- 枚举字面量输入别名（镜像 TS $Enum_）--------------------

    @Test
    void generate_enumAlias_emitsLiteralUnionInPackageModule(@TempDir Path temp) throws Exception {
        // 合成枚举 IR：pkg.c.Color{RED,GREEN,BLUE} + 无别名的对照包 pkg.d.Plain
        TypeDecl color = new TypeDecl(TypeDecl.Kind.ENUM, null, "pkg.c.Color");
        for (String c : new String[]{"RED", "GREEN", "BLUE"}) {
            FieldDecl f = new FieldDecl(c, new TypeSlot(null, ApiTypeRef.symbol(new ApiSymbolId("java", "pkg.c.Color"))));
            f.isStatic = true;
            f.isEnumConstant = true;
            color.fields.add(f);
        }
        TypeDecl plain = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.d.Plain");
        List<TypeDecl> ir = List.of(color, plain);

        ProbeGenerator.GenerateResult res = runGenerate(temp, emptySnapshot(), ir, List.of("pkg.c", "pkg.d"));
        assertTrue(res.success(), "generate failed: " + res.message());

        String cMod = Files.readString(temp.resolve("probe-python/nekojs/_java/pkg/c/__init__.pyi"));
        assertTrue(cMod.contains("class Color:"), cMod);
        assertTrue(cMod.contains("Color_ = Color | Literal[\"RED\", \"GREEN\", \"BLUE\"]"),
                "enum literal alias missing: " + cMod);
        assertTrue(cMod.contains("from typing import Any, Callable, ClassVar, Literal"),
                "Literal must be imported when the module uses literal unions: " + cMod);

        // 无别名的模块不引入 Literal
        String dMod = Files.readString(temp.resolve("probe-python/nekojs/_java/pkg/d/__init__.pyi"));
        assertFalse(dMod.contains("Literal"), "plain module should not import Literal: " + dMod);
    }

    @Test
    void generate_enumDispatchKeyEventKeyWidenedToEnumAlias(@TempDir Path temp) throws Exception {
        // 真实反射的枚举作为 dispatch key：事件重载的 extra 参数应放宽为 FakeProbeEnum_
        TypeDecl keyEnum = new TypeReflector().reflect(FakeProbeEnum.class);
        TypeDecl event = new TypeDecl(TypeDecl.Kind.CLASS, FakeProbeEvent.class, FakeProbeEvent.class.getName());
        List<TypeDecl> ir = List.of(keyEnum, event);

        EventCatalogEntry dispatch = new EventCatalogEntry(
                "SampleEvents", "dispatch", ScriptType.SERVER, FakeProbeEvent.class, FakeProbeEnum.class,
                false, true, "SampleEvents.dispatch(key, event => {\n  $0\n})");
        NekoScriptCatalogSnapshot snapshot = snapshotWith(List.of(), List.of(dispatch), List.of());

        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of());
        assertTrue(res.success(), "generate failed: " + res.message());

        String server = Files.readString(temp.resolve("probe-python/nekojs/_events/server/__init__.pyi"));
        assertTrue(server.contains("extra: FakeProbeEnum_"),
                "enum dispatch key should widen to enum alias: " + server);
        assertTrue(server.contains("import FakeProbeEnum_"),
                "enum alias import missing in event module: " + server);
    }

    // -------------------- RegistryValue 形状 → Literal[...]（小注册表）/ str（大注册表）--------------------

    @Test
    void generate_registryValueAlias_emitsSortedLiteralUnion(@TempDir Path temp) throws Exception {
        TypeDecl target = new TypeDecl(TypeDecl.Kind.CLASS, FakeProbeEvent.class, FakeProbeEvent.class.getName());
        List<TypeDecl> ir = List.of(target);

        // 快照条目乱序 → Literal 必须排序输出（确定性）
        RegistryTypeCatalogEntry registry = new RegistryTypeCatalogEntry(
                "SampleBlock", List.of("testcraft:zeta", "testcraft:alpha"), List.of());
        AdapterCatalogEntry adapter = new AdapterCatalogEntry(
                FakeProbeEvent.class, List.of(AdapterInputShape.self(), AdapterInputShape.registry("SampleBlock")),
                ConversionPrecedence.HIGH, Optional.empty());

        NekoScriptCatalogSnapshot snapshot = snapshotWith(List.of(), List.of(), List.of(adapter), List.of(registry));
        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of());
        assertTrue(res.success(), "generate failed: " + res.message());

        String module = Files.readString(temp.resolve(
                "probe-python/nekojs/_java/com/tkisor/nekojs/probe/backend/python/__init__.pyi"));
        assertTrue(module.contains(
                        "FakeProbeEvent_ = FakeProbeEvent | Literal[\"testcraft:alpha\", \"testcraft:zeta\"]"),
                "registry literal union missing: " + module);
        assertTrue(module.contains("from typing import Any, Callable, ClassVar, Literal"), module);
    }

    @Test
    void generate_largeRegistryValueAlias_abbreviatesToStrWithComment(@TempDir Path temp) throws Exception {
        TypeDecl target = new TypeDecl(TypeDecl.Kind.CLASS, FakeProbeEvent.class, FakeProbeEvent.class.getName());
        List<TypeDecl> ir = List.of(target);

        // >=512 条目 → 缩略为 str，行尾注释标注注册表名
        List<String> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 512; i++) entries.add("minecraft:block_" + i);
        RegistryTypeCatalogEntry big = new RegistryTypeCatalogEntry("BigBlock", entries, List.of());
        AdapterCatalogEntry adapter = new AdapterCatalogEntry(
                FakeProbeEvent.class, List.of(AdapterInputShape.self(), AdapterInputShape.registry("BigBlock")),
                ConversionPrecedence.HIGH, Optional.empty());

        NekoScriptCatalogSnapshot snapshot = snapshotWith(List.of(), List.of(), List.of(adapter), List.of(big));
        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of());
        assertTrue(res.success(), "generate failed: " + res.message());

        String module = Files.readString(temp.resolve(
                "probe-python/nekojs/_java/com/tkisor/nekojs/probe/backend/python/__init__.pyi"));
        assertTrue(module.contains("FakeProbeEvent_ = FakeProbeEvent | str  # BigBlock registry ids"),
                "large registry should abbreviate to str with a naming comment: " + module);
        assertFalse(module.contains("Literal["), "large registry must not emit a literal union: " + module);
        assertFalse(module.contains(", Literal"), "no Literal import when nothing uses it: " + module);
    }

    @Test
    void generate_registryValueUnknownRegistry_skipsShape(@TempDir Path temp) throws Exception {
        // 快照缺失该注册表 → 形状跳过（与旧行为一致），别名退化为其余形状
        TypeDecl target = new TypeDecl(TypeDecl.Kind.CLASS, FakeProbeEvent.class, FakeProbeEvent.class.getName());
        List<TypeDecl> ir = List.of(target);

        AdapterCatalogEntry adapter = new AdapterCatalogEntry(
                FakeProbeEvent.class, List.of(AdapterInputShape.self(), AdapterInputShape.registry("Missing")),
                ConversionPrecedence.HIGH, Optional.empty());

        NekoScriptCatalogSnapshot snapshot = snapshotWith(List.of(), List.of(), List.of(adapter), List.of());
        ProbeGenerator.GenerateResult res = runGenerate(temp, snapshot, ir, List.of());
        assertTrue(res.success(), "generate failed: " + res.message());

        String module = Files.readString(temp.resolve(
                "probe-python/nekojs/_java/com/tkisor/nekojs/probe/backend/python/__init__.pyi"));
        assertFalse(module.contains("FakeProbeEvent_"), "no alias when all shapes are dropped: " + module);
    }

    // -------------------- C5a：隐藏类残留清理 --------------------

    @Test
    void generate_hiddenClassNotImportedAndNotRendered(@TempDir Path temp) throws Exception {
        // A 引用 pkg.b.Hidden；Hidden 被 hide()（mutated + hidden）
        TypeDecl a = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.a.A");
        MethodDecl getH = new MethodDecl("getH");
        getH.returnType = new TypeSlot(null, ApiTypeRef.symbol(new ApiSymbolId("java", "pkg.b.Hidden")));
        a.methods.add(getH);
        TypeDecl hidden = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.b.Hidden");
        hidden.hidden = true;
        hidden.mutated = true;
        List<TypeDecl> ir = List.of(a, hidden);

        ProbeGenerator.GenerateResult res = runGenerate(temp, emptySnapshot(), ir, List.of("pkg.a", "pkg.b"));
        assertTrue(res.success(), "generate failed: " + res.message());

        // A 的模块：不 import Hidden；对 Hidden 的引用降级为 Any
        String aMod = Files.readString(temp.resolve("probe-python/nekojs/_java/pkg/a/__init__.pyi"));
        assertFalse(aMod.contains("import Hidden"), "hidden class must not be imported: " + aMod);
        assertTrue(aMod.contains("def getH(self) -> Any"), "hidden SYMBOL should degrade to Any: " + aMod);

        // Hidden 的模块：该类不出现
        String bMod = Files.readString(temp.resolve("probe-python/nekojs/_java/pkg/b/__init__.pyi"));
        assertFalse(bMod.contains("class Hidden"), "hidden class must not be rendered: " + bMod);
    }

    // -------------------- pyrightconfig: nested Python script dirs --------------------

    @Test
    void contributeEditorConfig_writesPyrightExtraPathsForEveryPythonDir(@TempDir Path temp) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(temp);
        Path out = temp.resolve(".neko_probe").resolve("python");
        Path src = paths.serverScripts().resolve("src");
        Path deep = src.resolve("deep");
        Files.createDirectories(deep);
        Files.writeString(src.resolve("ma.py"), "from nekojs import *\n");
        Files.writeString(deep.resolve("tool.py"), "from nekojs import *\n");
        // JS-only dir must NOT get a pyrightconfig contribution from the python backend.
        Path jsOnly = paths.serverScripts().resolve("js_only");
        Files.createDirectories(jsOnly);
        Files.writeString(jsOnly.resolve("main.js"), "console.log(1)\n");

        Map<Path, List<String>> merged = new LinkedHashMap<>();
        EditorConfigContributor recorder = new EditorConfigContributor() {
            @Override public void mergePyrightExtraPaths(Path file, List<String> extraPaths) {
                merged.put(file.normalize().toAbsolutePath(), List.copyOf(extraPaths));
            }
            @Override public void mergeJsConfigPaths(Path file, Map<String, List<String>> aliases) {}
            @Override public void mergeJsConfigIncludes(Path file, List<String> includes) {}
            @Override public void mergeJsConfigTypeRoots(Path file, List<String> typeRoots) {}
            @Override public void mergeVscodeSnippets(Path file, List<Snippet> snippets) {}
        };

        ProbeContext ctx = new ProbeContext.Of(emptySnapshot(), List.of(), new ProbeConfig(true, ".neko_probe",
                new ProbeConfig.ScanConfig(List.of(), List.of(), List.of(), List.of(), 5, "SMART")),
                paths, "python", out, List.of());
        new PythonProbeBackend().contributeEditorConfig(recorder, ctx);

        assertTrue(merged.containsKey(src.resolve("pyrightconfig.json").normalize().toAbsolutePath()),
                "nested python dir src must get pyrightconfig: " + merged.keySet());
        assertTrue(merged.containsKey(deep.resolve("pyrightconfig.json").normalize().toAbsolutePath()),
                "deep python dir must get pyrightconfig: " + merged.keySet());
        assertFalse(merged.containsKey(jsOnly.resolve("pyrightconfig.json").normalize().toAbsolutePath()),
                "js-only dir must not get a python pyrightconfig: " + merged.keySet());
        assertEquals(List.of("../../../.neko_probe/python"),
                merged.get(src.resolve("pyrightconfig.json").normalize().toAbsolutePath()));
    }

    // -------------------- helpers --------------------

    private static NekoScriptCatalogSnapshot snapshotWith(List<BindingCatalogEntry> bindings,
                                                          List<EventCatalogEntry> events,
                                                          List<AdapterCatalogEntry> adapters) {
        return snapshotWith(bindings, events, adapters, List.of());
    }

    /** 带 registryTypes 的快照构造（RegistryValue 形状 → Literal[...] 的数据源）。 */
    private static NekoScriptCatalogSnapshot snapshotWith(List<BindingCatalogEntry> bindings,
                                                          List<EventCatalogEntry> events,
                                                          List<AdapterCatalogEntry> adapters,
                                                          List<RegistryTypeCatalogEntry> registries) {
        return new NekoScriptCatalogSnapshot(
                List.of(), bindings, events, adapters, List.of(), List.of(), List.of(),
                List.of(), List.of(), registries, null, Map.of(), List.of());
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

/** 集成测试用枚举：真实反射路径，供枚举别名与 dispatch key 放宽断言。 */
enum FakeProbeEnum {
    RED, GREEN
}
