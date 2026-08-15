package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.NekoId;

/**
 * 资源标识符（ID）门面，暴露为脚本侧全局对象 {@code ID}。
 *
 * <p>负责字符串与 {@link NekoId} 之间的转换。脚本侧通常传入
 * {@code "minecraft:stone"} 或省略命名空间的 {@code "stone"}
 * （此时使用默认命名空间 {@link NekoId#DEFAULT_NAMESPACE}）。
 */
public interface IdFacade {
    /** 解析字符串为 {@link NekoId}；无命名空间前缀时使用默认命名空间 {@link NekoId#DEFAULT_NAMESPACE}。 */
    NekoId of(String value);

    /** 以显式命名空间与路径构造 {@link NekoId}。 */
    NekoId of(String namespace, String path);

    /** 返回 {@link NekoId} 的命名空间。 */
    String namespace(NekoId id);

    /** 返回 {@link NekoId} 的路径。 */
    String path(NekoId id);

    /** 返回 {@link NekoId} 的 {@code "namespace:path"} 字符串表示。 */
    String asString(NekoId id);
}
