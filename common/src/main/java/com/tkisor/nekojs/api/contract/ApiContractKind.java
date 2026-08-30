package com.tkisor.nekojs.api.contract;

/**
 * API 契约的分类，区分契约的稳定性与适用场景。
 *
 * <p>主代码的显式分支（见 {@code JsApiSurfaceResolver} / {@code VerifiedContractSet}）：
 * <ul>
 *   <li>{@link #PORTABLE}：每个 owner 恰一个的跨平台稳定契约（{@code requirePortable}
 *       校验），其哈希作为 {@code ApiContractHashes.portableContractHash}；</li>
 *   <li>{@link #FEATURE} / {@link #ADDON}：功能与插件扩展契约，其兼容性哈希进入
 *       {@code ApiContractHashes.moduleContractHashes} 供模块解析比对。</li>
 * </ul>
 * {@link #PLATFORM} 与 {@link #SPI} 是分层模型的一部分，目前无独立分支逻辑。
 */
public enum ApiContractKind {
    /** 跨平台可移植契约（每 owner 恰一个）。 */
    PORTABLE,
    /** 平台功能契约（参与模块契约哈希）。 */
    FEATURE,
    /** 平台专属契约。 */
    PLATFORM,
    /** 插件扩展（addon）契约（参与模块契约哈希）。 */
    ADDON,
    /** 服务提供者接口（SPI）契约。 */
    SPI
}
