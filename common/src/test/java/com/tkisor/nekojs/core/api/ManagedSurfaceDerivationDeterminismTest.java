package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.probe.backend.typescript.ManagedApiDeclarationGenerator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC2/AC5（ticket 09）：同一契约输入重复生成 manifest 与 Probe TypeScript declaration
 * 的结果<b>逐字节稳定</b>，且产物成员与 runtime member（契约反射符号）一一对应
 * （成员名、签名、owner/module 归属 parity）。
 *
 * <p>本测试<b>只读</b>既有 golden（写 golden 由显式 regenerate 负责），所有重复生成都在
 * 内存中完成，不触碰任何基线文件。
 */
class ManagedSurfaceDerivationDeterminismTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-nekojs-determinism.jar");

    @Test
    void contractSynthesisIsByteStableAcrossIndependentRuns() {
        VerifiedApiContract first = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        VerifiedApiContract second = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);

        assertEquals(first.contract().toString(), second.contract().toString(),
                "NormativeApiContract canonical form must be byte-stable across independent synthesis");
        assertEquals(first.integritySha256(), second.integritySha256(),
                "contract integrity hash must be reproducible from the same reflection inputs");
    }

    @Test
    void manifestDerivationIsByteStableAcrossRepeatedGeneration() {
        String first = manifestJson();
        String second = manifestJson();

        assertEquals(first, second, "manifest JSON must be byte-stable for the same contract input");
    }

    @Test
    void tsDeclarationDerivationIsByteStableAcrossRepeatedGeneration() {
        assertEquals(tsDeclaration(ScriptType.SERVER), tsDeclaration(ScriptType.SERVER),
                "SERVER TS declaration must be byte-stable");
        assertEquals(tsDeclaration(ScriptType.CLIENT), tsDeclaration(ScriptType.CLIENT),
                "CLIENT TS declaration must be byte-stable");
    }

    /**
     * AC5 parity：TS declaration 的成员集合必须恰好等于契约（runtime member）集合——
     * 每个 member:Owner.member 渲染进 interface $Owner，每个 global:X 渲染为 const X，
     * 不得多出（未承诺成员）也不得遗漏（漏声明的 runtime 成员）。
     */
    @Test
    void tsDeclarationMembersHaveRuntimeParity() {
        List<ApiSymbol> symbols = resolvedSurfaceSymbols();
        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, resolvedSnapshot()), ScriptType.SERVER);

        // owner → member names（来自契约/runtime member）
        Map<String, List<String>> owners = new LinkedHashMap<>();
        List<String> globalNames = new ArrayList<>();
        for (ApiSymbol symbol : symbols) {
            ApiSymbolId id = symbol.id();
            if ("member".equals(id.kind())) {
                String qualified = id.qualifiedName();
                int separator = qualified.lastIndexOf('.');
                assertTrue(separator > 0, "member symbol must be qualified: " + id.value());
                owners.computeIfAbsent(qualified.substring(0, separator), k -> new ArrayList<>())
                        .add(qualified.substring(separator + 1));
            } else if ("global".equals(id.kind())) {
                globalNames.add(id.qualifiedName());
            }
        }

        // 产物侧：interface $Owner 块内的成员名 + const 全局名
        Map<String, List<String>> renderedOwners = extractInterfaceMembers(ts);
        assertEquals(owners.keySet(), renderedOwners.keySet(),
                "rendered owner interfaces must match contract member owners exactly (module/owner attribution)");
        for (Map.Entry<String, List<String>> entry : owners.entrySet()) {
            List<String> expected = entry.getValue().stream().distinct().sorted().toList();
            List<String> actual = renderedOwners.getOrDefault(entry.getKey(), List.of())
                    .stream().distinct().sorted().toList();
            assertEquals(expected, actual, "member parity mismatch in interface $" + entry.getKey());
        }

        List<String> renderedGlobals = extractConstNames(ts);
        assertEquals(globalNames.stream().distinct().sorted().toList(), renderedGlobals,
                "rendered globals must match contract global symbols exactly");
    }

    /** TS declaration 中每个契约签名必须出现（签名 anchor parity）。 */
    @Test
    void tsDeclarationRendersEveryContractSignature() {
        List<ApiSymbol> symbols = resolvedSurfaceSymbols();
        String ts = new ManagedApiDeclarationGenerator()
                .generate(Map.of(ScriptType.SERVER, resolvedSnapshot()), ScriptType.SERVER);

        int signatureCount = 0;
        for (ApiSymbol symbol : symbols) {
            String anchor;
            if ("global".equals(symbol.id().kind())) {
                anchor = "const " + symbol.id().qualifiedName() + ":";
            } else {
                String qualified = symbol.id().qualifiedName();
                anchor = qualified.substring(qualified.lastIndexOf('.') + 1) + "(";
            }
            assertTrue(ts.contains(anchor),
                    "rendered declaration missing signature anchor '" + anchor + "' for " + symbol.id().value());
            signatureCount += symbol.signatures().size();
        }
        assertTrue(signatureCount >= 100, "fixture should cover the whole core surface, got " + signatureCount);
    }

    // ---------- helpers ----------

    private static List<ApiSymbol> resolvedSurfaceSymbols() {
        return resolvedSnapshot().surfaceSnapshot().symbols();
    }

    private static ApiEnvironmentSnapshot resolvedSnapshot() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                environment(), core.contracts(), List.of(core.contributions()), List.of());
        return registry.environmentSnapshot();
    }

    private static String manifestJson() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), TEST_CODE_SOURCE);
        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                environment(), core.contracts(), List.of(core.contributions()), List.of());
        ApiRuntimeVersions versions = ApiRuntimeVersionReader.read();
        ApiManifest manifest = ApiManifestGenerator.generate(
                versions, "test", "0.0.0", registry.environmentSnapshot().surfaceSnapshot());
        return ApiManifestJson.toJson(manifest) + "\n";
    }

    private static String tsDeclaration(ScriptType scriptType) {
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(scriptType, resolvedSnapshot());
        return new ManagedApiDeclarationGenerator().generate(managedApis, scriptType);
    }

    /** 从 interface $Owner { ... } 块提取成员名（方法名）。 */
    private static Map<String, List<String>> extractInterfaceMembers(String ts) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("    interface \\$(\\w+) \\{").matcher(ts);
        List<String> owners = new ArrayList<>();
        List<Integer> contentStarts = new ArrayList<>();
        while (matcher.find()) {
            owners.add(matcher.group(1));
            contentStarts.add(matcher.end());
        }
        for (int i = 0; i < owners.size(); i++) {
            int contentEnd = ts.indexOf("\n    }", contentStarts.get(i));
            if (contentEnd < 0) contentEnd = ts.length();
            Pattern member = Pattern.compile("^        (\\w+)\\(", Pattern.MULTILINE);
            Matcher memberMatcher = member.matcher(ts.substring(contentStarts.get(i), contentEnd));
            List<String> names = new ArrayList<>();
            while (memberMatcher.find()) {
                names.add(memberMatcher.group(1));
            }
            result.put(owners.get(i), names);
        }
        return result;
    }

    private static List<String> extractConstNames(String ts) {
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("^    const (\\w+):", Pattern.MULTILINE).matcher(ts);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static EnvironmentKey environment() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "test",
                "0.0.0",
                LoaderVersion.parse("0.0.0"),
                "1.21.1",
                Map.of());
    }

    private static final class EmptyPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return Path.of("."); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }
}
