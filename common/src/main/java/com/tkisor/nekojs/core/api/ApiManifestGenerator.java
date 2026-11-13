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
 * Builds an ApiManifest from a runtime version snapshot and a frozen surface snapshot.
 *
 * <p>Every list is sorted for determinism, so separate JVM runs produce the same output:
 * capabilities and modules by name, symbols by stable id, and the signatures of each symbol by
 * call key. Golden tests ({@code ApiManifestGoldenTest}) guard the frozen API surface: a changed
 * symbol set or signature must update the golden file explicitly and go through breaking-change
 * review.
 */
public final class ApiManifestGenerator {

    private ApiManifestGenerator() {}

    /**
     * @param versions  runtime version snapshot
     * @param loader    platform loader id, such as {@code neoforge} or {@code fabric}
     * @param minecraft Minecraft version id, such as {@code 26.1}, {@code 1.21.1} or {@code 1.12.2}
     * @param surface   frozen API surface snapshot
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
