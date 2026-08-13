package com.tkisor.nekojs.api.surface;

/**
 * API 符号贡献的分层（tier），决定符号的可见范围与稳定级别。
 *
 * <p>主代码当前对两层有显式分支（见 {@code JsApiSurfaceResolver}）：
 * <ul>
 *   <li>{@link #GLOBAL}：贡献的 {@code jsName} 会与 legacy 全局保留名校验，冲突抛
 *       {@code LEGACY_NAME_COLLISION}；</li>
 *   <li>{@link #VERSION}：唯一允许 {@code nativeReturn=true} 的层（其它层抛
 *       {@code NATIVE_TYPE_LEAK}）——版本层贡献按版本号切分返回类型。</li>
 * </ul>
 * 其余常量是贡献分层模型的一部分，目前无独立分支逻辑。
 */
public enum ApiTier {
    /** 全局符号层（如 {@code ID}、{@code Platform} 等全局对象）；受 legacy 保留名校验。 */
    GLOBAL,
    /** 类型成员符号层（如 {@code NekoId.namespace}）。 */
    MEMBER,
    /** 模块内成员符号层。 */
    MODULE_MEMBER,
    /** 平台功能（feature）层符号。 */
    FEATURE,
    /** 平台层符号。 */
    PLATFORM,
    /** 插件扩展（addon）层符号。 */
    ADDON,
    /** 版本层符号：按版本号切分返回类型；唯一允许 {@code nativeReturn=true} 的层。 */
    VERSION,
    /** 不安全的原生类型符号层。 */
    UNSAFE_NATIVE,
    /** 遗留预览符号层。 */
    LEGACY_PREVIEW
}
