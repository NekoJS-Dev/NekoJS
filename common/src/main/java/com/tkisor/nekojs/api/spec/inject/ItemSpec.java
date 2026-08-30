package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Item 跨平台统一扩展规范。
 *
 * <p>各平台的 {@code ItemExtension} 必须 {@code extends ItemSpec}。
 *
 * <p>{@code getId()} 在 NF 是实例零参 vs 原生 static 一参（arity 不同，安全），
 * 在 CR 原生无实例 getId，故可进 spec。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface ItemSpec {

    /** 物品注册 id（如 "minecraft:stone"）。 */
    default String neko$getId() {
        throw new UnsupportedOperationException("ItemSpec.neko$getId not implemented");
    }
}
