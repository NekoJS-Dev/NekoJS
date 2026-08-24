package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API 表面冻结基线（golden）：把当前契约反射产出的 core API 表面固化为
 * {@code src/test/resources/nekojs/golden/api-manifest-core.json}。
 *
 * <p>任何符号/签名/capability/module 变化都会让 golden 对比失败——这是「冻结」的机器可执行
 * 部分：变更必须显式走 `-Dnekojs.golden.regenerate=true` 再生成并经过破坏性变更评审
 * （治理细节见本地 ai_arch/API_VERSIONING.md，未入库），禁止静默改动 API 表面。
 */
class ApiManifestGoldenTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs.jar");
    private static final Path GOLDEN_PATH = Path.of("src/test/resources/nekojs/golden/api-manifest-core.json");

    @Test
    void coreApiSurfaceMatchesFrozenGolden() throws Exception {
        ApiManifest manifest = buildManifest();
        String actual = ApiManifestJson.toJson(manifest) + "\n";

        // Boolean.getBoolean（而非 != null）：:common:test 无条件把这个 system property 透传成
        // 字符串 "false"（common/build.gradle），所以 != null 会让每次普通测试都改写基线再
        // Assumptions.abort，冻结门禁整体空转。
        if (Boolean.getBoolean("nekojs.golden.regenerate")) {
            Files.createDirectories(GOLDEN_PATH.getParent());
            Files.writeString(GOLDEN_PATH, actual);
            Assumptions.abort("golden regenerated, review the diff before committing");
        }

        String expected = Files.readString(GOLDEN_PATH);
        assertEquals(expected, actual, "API surface drifted from frozen baseline; "
                + "run `./gradlew :common:test -Dnekojs.golden.regenerate=true --tests ...ApiManifestGoldenTest` "
                + "to regenerate, then review the diff (breaking-change review required)");
    }

    @Test
    void manifestCarriesVersionAndPlatformMetadata() {
        ApiManifest manifest = buildManifest();

        // api/spi/runtime 版本来自 api-runtime.properties（0.0.0 = 未门控）
        assertEquals(ApiRuntimeVersionReader.read().apiVersion().toString(), manifest.apiVersion());
        assertEquals("0.0.0", manifest.spiVersion());
        assertEquals(1, manifest.catalogSchemaVersion());
        assertEquals("test", manifest.platform().loader());
        // 确定性排序
        List<String> ids = manifest.symbols().stream().map(ApiManifest.ManifestSymbol::id).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "symbols must be sorted deterministically");
    }

    @Test
    void frozenBaselineCoversCoreFacades() throws Exception {
        ApiManifest manifest = buildManifest();
        List<String> ids = manifest.symbols().stream().map(ApiManifest.ManifestSymbol::id).toList();

        assertTrue(ids.contains("global:Platform"), "Platform global must be frozen: " + ids);
        assertTrue(ids.contains("member:JsonIO.parse"), "JsonIO.parse must be frozen");
        assertTrue(ids.contains("member:Text.of"), "Text.of must be frozen");
        assertTrue(ids.contains("member:NBT.parse"), "NBT.parse must be frozen");
        // 契约反射的符号总数基线（原 JSON 110 个，反射允许更完整）
        assertTrue(ids.size() >= 110, "frozen symbol count below baseline: " + ids.size());
    }

    /**
     * 负向断言：冻结门禁必须真的能发现表面变化。少一个符号、多一个符号、或某个符号多一个
     * 重载签名，序列化结果都必须与 golden 不同——否则 {@link #coreApiSurfaceMatchesFrozenGolden}
     * 只是在比较两份恒等的字符串。
     */
    @Test
    void goldenRejectsRemovedAddedAndChangedSymbols() throws Exception {
        String golden = Files.readString(GOLDEN_PATH);
        ApiManifest manifest = buildManifest();
        List<ApiManifest.ManifestSymbol> symbols = manifest.symbols();
        assertTrue(symbols.size() >= 2, "need at least two symbols to mutate");

        assertNotEquals(golden, json(withSymbols(manifest, symbols.subList(1, symbols.size()))),
                "removing a symbol must change the manifest JSON");

        List<ApiManifest.ManifestSymbol> added = new ArrayList<>(symbols);
        added.add(new ApiManifest.ManifestSymbol("global:zzzNotFrozen", List.of()));
        assertNotEquals(golden, json(withSymbols(manifest, added)),
                "adding a symbol must change the manifest JSON");

        List<ApiManifest.ManifestSymbol> changed = new ArrayList<>(symbols);
        ApiManifest.ManifestSymbol first = changed.get(0);
        List<String> extraSignature = new ArrayList<>(first.signatures());
        extraSignature.add("(java.lang.String)");
        changed.set(0, new ApiManifest.ManifestSymbol(first.id(), extraSignature));
        assertNotEquals(golden, json(withSymbols(manifest, changed)),
                "adding an overload signature must change the manifest JSON");
    }

    /** 再生成开关必须只在显式为 true 时打开：build.gradle 无条件透传字符串 "false"。 */
    @Test
    void regenerateSwitchIsOffForNonTrueValues() {
        assertFalse(Boolean.getBoolean("nekojs.golden.regenerate"),
                "golden regeneration must be off during a normal test run; the gate is a no-op otherwise");
    }

    private static String json(ApiManifest manifest) {
        return ApiManifestJson.toJson(manifest) + "\n";
    }

    private static ApiManifest withSymbols(ApiManifest base, List<ApiManifest.ManifestSymbol> symbols) {
        return new ApiManifest(base.catalogSchemaVersion(), base.apiVersion(), base.spiVersion(),
                base.runtimeContractVersion(), base.nekojsVersion(), base.platform(),
                base.capabilities(), base.modules(), symbols);
    }

    private static ApiManifest buildManifest() {
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        ApiRuntimeVersions versions = ApiRuntimeVersionReader.read();
        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                contract.contract().symbols(),
                Set.of(),
                List.of(),
                List.of(),
                new EnvironmentKey(null, null, null, null, null, null, Map.of()));
        return ApiManifestGenerator.generate(versions, "test", "0.0.0", surface);
    }
}
