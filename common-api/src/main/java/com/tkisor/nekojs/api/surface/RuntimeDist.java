package com.tkisor.nekojs.api.surface;

/**
 * 运行时分布：物理客户端、专用服务端、集成服务端。
 *
 * <p>主代码（{@code EnvironmentKeyFactory}）当前按 {@code Platform.isClient()} 二值映射：
 * 客户端 → {@link #CLIENT}，其余 → {@link #DEDICATED_SERVER}；{@link #INTEGRATED_SERVER}
 * 为预留值，暂未参与实际映射。
 */
public enum RuntimeDist {
    /** 物理客户端。 */
    CLIENT,
    /** 专用服务端（当前把非客户端运行环境统一映射为此值）。 */
    DEDICATED_SERVER,
    /** 集成服务端（单人世界内嵌）；预留，暂未参与实际映射。 */
    INTEGRATED_SERVER
}
