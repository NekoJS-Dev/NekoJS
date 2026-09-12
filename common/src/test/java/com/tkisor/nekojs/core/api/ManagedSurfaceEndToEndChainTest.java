package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.api.ApiSurfaceTestSupport;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.probe.backend.typescript.ManagedApiDeclarationGenerator;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC11（ticket 09）：一个真实脚本调用穿透完整派生链的证据——
 * contract 反射 → manifest → Probe TS declaration → 运行时调用（Python declaration 的
 * IR 侧证据由 {@code PythonDeclarationDeterminismParityTest} 承载）。
 *
 * <p>脚本只使用契约反射承诺的 managed symbol（Text/ID/NBT facade），每个用到的符号
 * 都被断言存在于 manifest 与 TS declaration——runtime 调用、契约承诺与派生产物三段一致。
 * Context 按 {@code NekoSandboxFactory} 的生产接线（真实 {@link NekoSharedHostAccess} +
 * {@link ClassFilter}）+ 契约 {@link ApiFacadeProxy} 门面。
 */
class ManagedSurfaceEndToEndChainTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-nekojs-chain.jar");

    /**
     * 最小可运行示例（AC7）：只使用已通过 gate 且经契约 invoker 验证的 managed 能力
     * （ID / Text / NBT 值构造与序列化）。与
     * docs/architecture-refactor/baseline/2026-09-12-managed-surface/minimal-example.js 保持一致。
     *
     * <p>注意：{@code NBT.compound()}（CompoundBuilder host object）虽然被契约与声明承诺，
     * 但当前经契约 invoker 返回 host object 会触发 NATIVE_TYPE_LEAK（既有不一致，见本票
     * REPORT「问题与处理」），示例因此使用 {@code NBT.of(...)} 值路径。
     */
    static final String MINIMAL_EXAMPLE_SCRIPT = """
            // NekoJS managed API 最小示例：只用契约反射承诺的符号
            const id = ID.of('nekojs', 'chain');
            const label = Text.of('chain-').append('example');
            if (label.isEmpty()) {
              throw new Error('label must not be empty');
            }
            const tag = NBT.of({
              id: ID.asString(id),
              label: 'chain-example',
              count: 3
            });
            NBT.toSnbt(tag)
            """;

    @TempDir
    Path gameDir;

    @Test
    @Tag("nbt-smoke")
    void scriptCallTraversesContractManifestDeclarationChain() {
        // ---- 段 1：NormativeApiContract 反射（唯一规范源） ----
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(CODE_SOURCE);
        Set<String> contractIds = contract.contract().symbols().stream()
                .map(s -> s.id().value()).collect(Collectors.toSet());
        assertTrue(contractIds.contains("global:ID"), "contract must promise global:ID");
        assertTrue(contractIds.contains("member:ID.of"), "contract must promise member:ID.of");
        assertTrue(contractIds.contains("member:ID.asString"), "contract must promise member:ID.asString");
        assertTrue(contractIds.contains("global:Text"), "contract must promise global:Text");
        assertTrue(contractIds.contains("member:Text.of"), "contract must promise member:Text.of");
        assertTrue(contractIds.contains("member:TextValue.append"), "contract must promise TextValue.append");
        assertTrue(contractIds.contains("member:TextValue.isEmpty"), "contract must promise TextValue.isEmpty");
        assertTrue(contractIds.contains("global:NBT"), "contract must promise global:NBT");
        assertTrue(contractIds.contains("member:NBT.of"), "contract must promise NBT value construction");
        assertTrue(contractIds.contains("member:NBT.toSnbt"), "contract must promise NBT.toSnbt");

        // ---- 段 2：manifest 派生（冻结 surface） ----
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new FixturePlatform(gameDir), CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                ApiSurfaceTestSupport.serverEnvironment(), core.contracts(), List.of(core.contributions()), List.of());
        ApiManifest manifest = ApiManifestGenerator.generate(
                ApiRuntimeVersionReader.read(), "test", "1.21.1",
                registry.environmentSnapshot().surfaceSnapshot());
        Set<String> manifestIds = manifest.symbols().stream()
                .map(ApiManifest.ManifestSymbol::id).collect(Collectors.toSet());
        assertTrue(manifestIds.containsAll(List.of("global:ID", "member:ID.of", "member:ID.asString",
                        "global:Text", "member:Text.of", "member:TextValue.append", "member:TextValue.isEmpty",
                        "global:NBT", "member:NBT.of", "member:NBT.toSnbt")),
                "manifest must derive from the same contract: missing " + manifestIds);

        // ---- 段 3：TS declaration 派生（同一 surface） ----
        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, registry.environmentSnapshot()), ScriptType.SERVER);
        assertTrue(ts.contains("const Text: $Text;"), "TS declaration must expose Text global:\n" + ts);
        assertTrue(ts.contains("const ID: $ID;"), "TS declaration must expose ID global");
        assertTrue(ts.contains("const NBT: $NBT;"), "TS declaration must expose NBT global");
        assertTrue(ts.contains("of("), "TS declaration must declare Text.of/ID.of/NBT.of members");
        assertTrue(ts.contains("toSnbt("), "TS declaration must declare NBT.toSnbt member");

        // ---- 段 4：Python declaration 派生（IR 反射侧证据） ----
        TypeDecl pythonIr = new TypeReflector().reflect(ChainFixture.class);
        assertEquals("com.tkisor.nekojs.core.api.ManagedSurfaceEndToEndChainTest$ChainFixture", pythonIr.fqn,
                "python declaration derives from runtime members via reflection");
        assertTrue(pythonIr.methods.stream().anyMatch(m -> "chainMethod".equals(m.name)),
                "runtime member must be reflectable into the python declaration IR");

        // ---- 段 5：真实脚本调用（Graal，契约 invoker 门面） ----
        String snbt = runMinimalExampleScript(core, registry);
        assertEquals("{id:\"nekojs:chain\",label:\"chain-example\",count:3}", snbt,
                "script result must round-trip through the managed contract invokers");
    }

    /** 运行最小示例脚本（单次 eval，脚本尾表达式返回 SNBT）。 */
    private String runMinimalExampleScript(CoreManagedApiBootstrap.CoreManagedApi core,
                                           FrozenApiRegistry registry) {
        NekoSharedHostAccess hostAccess = new NekoSharedHostAccess(List.of());
        ClassFilter classFilter = new ClassFilter(SandboxConfig.defaultConfig());
        try (Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(hostAccess.get())
                .allowHostClassLookup(classFilter)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.strict", "true")
                .build()) {
            ApiGuestErrorFactory errors = ApiGuestErrorFactory.create(context);
            context.getBindings("js").putMember("ID",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.ID_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.ID_GLOBAL), errors));
            context.getBindings("js").putMember("Text",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.TEXT_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.TEXT_GLOBAL), errors));
            context.getBindings("js").putMember("NBT",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.NBT_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.NBT_GLOBAL), errors));

            Value snbt = context.eval("js", MINIMAL_EXAMPLE_SCRIPT);
            return snbt.asString();
        }
    }

    /** 文档示例对应的反射宿主（python IR 段使用）。 */
    public static final class ChainFixture {
        public String chainMethod() { return "chain"; }
    }


    private static final class FixturePlatform implements IPlatform {
        private final Path gameDir;

        FixturePlatform(Path gameDir) {
            this.gameDir = gameDir;
        }

        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return gameDir; }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }
}
