package com.tkisor.nekojs.api.surface;

import java.util.List;

/**
 * Serializable observation of the API surface (ApiManifest).
 *
 * <p>A manifest records what an implementation exposes; it is not normative input. It is exported
 * by {@code ApiManifestGenerator} from a frozen surface snapshot and used for API diffs,
 * cross-platform comparison of the stable subset and release tooling. The normative contract is
 * still {@code NormativeApiContract}, which is reviewed by hand.
 *
 * <p>This DTO holds only serializable fields, strings and lists, and no {@code Class}/
 * {@code Method}/{@code Path}/Graal {@code Value}. JSON encoding and decoding lives in
 * {@code ApiManifestJson} in common.
 *
 * @param catalogSchemaVersion   catalog schema version
 * @param apiVersion             API version (SemVer string)
 * @param spiVersion             SPI version (SemVer string; 0.0.0 = not gated)
 * @param runtimeContractVersion runtime contract version (ESM/CJS, compiler, Node shim; independent of the game API)
 * @param nekojsVersion          NekoJS version string
 * @param platform               platform info (loader + minecraft)
 * @param capabilities           enabled capability names (alphabetical)
 * @param modules                enabled modules (alphabetical)
 * @param symbols                symbol list (sorted by stable symbol id)
 */
public record ApiManifest(
        int catalogSchemaVersion,
        String apiVersion,
        String spiVersion,
        String runtimeContractVersion,
        String nekojsVersion,
        PlatformInfo platform,
        List<String> capabilities,
        List<String> modules,
        List<ManifestSymbol> symbols
) {
    public ApiManifest {
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        modules = List.copyOf(modules == null ? List.of() : modules);
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
    }

    /** Platform info. */
    public record PlatformInfo(String loader, String minecraft) {}

    /**
     * Manifest record for one symbol: a stable symbol id (such as {@code global:Item} or
     * {@code member:Platform.getInfo}) plus the call key of each overload (parameter shape only,
     * without the return type).
     */
    public record ManifestSymbol(String id, List<String> signatures) {
        public ManifestSymbol {
            signatures = List.copyOf(signatures == null ? List.of() : signatures);
        }
    }
}
