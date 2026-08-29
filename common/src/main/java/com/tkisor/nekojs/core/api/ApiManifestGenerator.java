package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.ApiManifest;
import com.tkisor.nekojs.api.surface.ApiManifest.PlatformInfo;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * ApiManifest 生成器：从运行时版本快照 + 冻结的 surface snapshot 导出可序列化 manifest。
 *
 * <p>所有列表输出为字典序（确定性，跨 JVM 运行可复现）：capabilities / modules 按名称、
 * symbols 按 stable id、每个 symbol 的签名按调用键。API 表面冻结以 golden 对比测试守护
 * （{@code ApiManifestGoldenTest}）：符号集合或签名变化必须显式更新 golden 并走破坏性
 * 变更评审（治理细节见本地 ai_arch/API_VERSIONING.md，未入库）。
 */
public final class ApiManifestGenerator {

    private ApiManifestGenerator() {}

    /**
     * @param versions 运行时版本快照（api-runtime.properties）
     * @param loader   平台 loader 标识（如 {@code neoforge} / {@code cleanroom}）
     * @param minecraft Minecraft 版本标识（如 {@code 26.1} / {@code 1.21.1} / {@code 1.12.2}）
     * @param surface  冻结的 API 表面快照
     */
    public static ApiManifest generate(
            ApiRuntimeVersions versions,
            String loader,
            String minecraft,
            ApiSurfaceSnapshot surface) {
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(surface, "surface");

        List<ApiManifest.ManifestSymbol> symbols = surface.symbols().stream()
                .map(symbol -> new ApiManifest.ManifestSymbol(
                        symbol.id().value(),
                        symbol.signatures().stream()
                                .map(ApiSignature::callKey)
                                .sorted()
                                .toList()))
                .sorted(Comparator.comparing(ApiManifest.ManifestSymbol::id))
                .toList();

        List<String> capabilities = surface.activeCapabilityNames().stream().sorted().toList();
        List<String> modules = surface.activeModules().stream()
                .map(module -> module.descriptor().moduleId())
                .sorted()
                .toList();

        return new ApiManifest(
                versions.catalogSchemaVersion(),
                versions.apiVersion().toString(),
                versions.spiVersion().toString(),
                versions.runtimeContractVersion().toString(),
                versions.nekojsVersion(),
                new PlatformInfo(loader, minecraft),
                capabilities,
                modules,
                symbols);
    }
}
