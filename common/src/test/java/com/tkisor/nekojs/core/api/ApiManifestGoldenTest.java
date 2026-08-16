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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        if (System.getProperty("nekojs.golden.regenerate") != null) {
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
