package com.tkisor.nekojs.api.surface;

import java.util.List;

/**
 * 可序列化的 API 表面观测结果（ApiManifest，设计见 unified-js-api-design §9.5）。
 *
 * <p>manifest 是「实现观测结果」，不是规范性输入：它由 {@code ApiManifestGenerator} 从
 * 冻结的 surface snapshot 导出，用于 API diff、跨平台 stable 子集比较与发布工具。
 * 规范性契约仍是 {@code NormativeApiContract}（人工审阅）。
 *
 * <p>本 DTO 只含字符串/列表等可序列化字段（无 {@code Class}/{@code Method}/{@code Path}/
 * Graal {@code Value}）；JSON 编解码见 common 的 {@code ApiManifestJson}。
 *
 * @param catalogSchemaVersion   catalog schema 版本
 * @param apiVersion             API 版本（SemVer 字符串）
 * @param spiVersion             SPI 版本（SemVer 字符串；0.0.0 = 未门控）
 * @param runtimeContractVersion 运行时契约版本（ESM/CJS、编译器、Node shim，独立于游戏 API）
 * @param nekojsVersion          NekoJS 版本字符串
 * @param platform               平台信息（loader + minecraft）
 * @param capabilities           激活能力名（字典序）
 * @param modules                激活模块（字典序）
 * @param symbols                符号清单（按 stable symbol id 字典序）
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

    /** 平台信息。 */
    public record PlatformInfo(String loader, String minecraft) {}

    /**
     * 单个符号的 manifest 记录：stable symbol id（如 {@code global:Item}、
     * {@code member:Platform.getInfo}）+ 每个重载的调用键（仅参数形状，不含返回类型）。
     */
    public record ManifestSymbol(String id, List<String> signatures) {
        public ManifestSymbol {
            signatures = List.copyOf(signatures == null ? List.of() : signatures);
        }
    }
}
