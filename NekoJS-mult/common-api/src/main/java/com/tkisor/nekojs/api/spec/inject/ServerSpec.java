package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * MinecraftServer 跨平台统一扩展规范（NF_ONLY——CR 1.12.2 暂未实现对应扩展）。
 *
 * <p>各 NF 平台的 {@code ServerExtension} 必须 {@code extends ServerSpec}。
 *
 * <p>{@link #neko$data()} 返回 {@code Object}（NF 返回 {@code AttachedData<MinecraftServer>}），
 * 各平台用协变返回类型覆盖，spec 不声明具体类型以遵守 common-api 边界约束。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface ServerSpec {

    /** 挂载到该 server 的内存数据容器。 */
    default Object neko$data() {
        throw new UnsupportedOperationException("ServerSpec.neko$data not implemented");
    }
}
