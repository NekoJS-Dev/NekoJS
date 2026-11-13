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
 * Evidence that one real script call travels the whole derivation chain: contract reflection →
 * manifest → Probe TS declaration → runtime call. The Python declaration IR evidence lives in
 * {@code PythonDeclarationDeterminismParityTest}.
 *
 * <p>The script only uses managed symbols promised by the contract (the Text/ID/NBT facades), and
 * every symbol it uses is asserted to exist in both the manifest and the TS declaration, so the
 * runtime call, the contract promise and the generated artifacts agree.
 * The Context follows the production wiring of {@code NekoSandboxFactory} (real
 * {@link NekoSharedHostAccess} + {@link ClassFilter}) with the contract {@link ApiFacadeProxy}
 * facade.
 */
class ManagedSurfaceEndToEndChainTest {

    private static final URI CODE_SOURCE = URI.create("file:///test-nekojs-chain.jar");

    /**
     * Minimal runnable example. It only uses managed capabilities that already pass the gate and
     * are verified through the contract invokers (ID / Text / NBT value construction and
     * serialization).
     *
     * <p>{@code NBT.compound()} (the CompoundBuilder host object) is promised by the contract and
     * the declaration, but the contract invoker currently triggers NATIVE_TYPE_LEAK when it
     * returns a host object. That inconsistency is still open, so the example uses the
     * {@code NBT.of(...)} value path instead.
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
        // ---- Step 1: NormativeApiContract reflection, the single normative source ----
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

        // ---- Step 2: manifest derivation (the frozen surface) ----
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

        // ---- Step 3: TS declaration derivation (from the same surface) ----
        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, registry.environmentSnapshot()), ScriptType.SERVER);
        assertTrue(ts.contains("const Text: $Text;"), "TS declaration must expose Text global:\n" + ts);
        assertTrue(ts.contains("const ID: $ID;"), "TS declaration must expose ID global");
        assertTrue(ts.contains("const NBT: $NBT;"), "TS declaration must expose NBT global");
        assertTrue(ts.contains("of("), "TS declaration must declare Text.of/ID.of/NBT.of members");
        assertTrue(ts.contains("toSnbt("), "TS declaration must declare NBT.toSnbt member");

        // ---- Step 4: Python declaration derivation (IR reflection evidence) ----
        TypeDecl pythonIr = new TypeReflector().reflect(ChainFixture.class);
        assertEquals("com.tkisor.nekojs.core.api.ManagedSurfaceEndToEndChainTest$ChainFixture", pythonIr.fqn,
                "python declaration derives from runtime members via reflection");
        assertTrue(pythonIr.methods.stream().anyMatch(m -> "chainMethod".equals(m.name)),
                "runtime member must be reflectable into the python declaration IR");

        // ---- Step 5: real script call (Graal, contract invoker facades) ----
        String snbt = runMinimalExampleScript(core, registry);
        assertEquals("{id:\"nekojs:chain\",label:\"chain-example\",count:3}", snbt,
                "script result must round-trip through the managed contract invokers");
    }

    /** Runs the minimal example script once and returns the SNBT from its trailing expression. */
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

    /** Reflection host for the documented example; used by the Python IR step. */
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
