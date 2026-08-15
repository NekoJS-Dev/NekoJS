package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * 事件统一扩展规范（cancel / isCancelled）。
 *
 * <p>NeoForge 系平台的 {@code EventExtension} 必须 {@code extends EventSpec}，通过 mixin
 * 注入各自的原生事件基类（NF {@code net.neoforged.bus.api.Event}）。
 *
 * <p>scope 为 {@link PlatformAvailability.Scope#NF_ONLY}：Cleanroom 1.12.2 无法实现——
 * Forge/FML 会在任何 mod 的 mixin 配置注册之前加载
 * {@code net.minecraftforge.fml.common.eventhandler.Event} 基类（coremod 变换器注册阶段），
 * 基类 mixin 必然抛 {@code MixinTargetAlreadyLoadedException}。1.12.2 脚本侧取消事件
 * 请使用监听器 {@code return true} 或原生 {@code setCanceled(true)} / {@code isCanceled()}。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface EventSpec {

    /** 取消该事件。仅在可取消事件上有效。返回是否成功取消。 */
    default boolean neko$cancel() {
        throw new UnsupportedOperationException("EventSpec.neko$cancel not implemented");
    }

    /** 该事件是否已被取消。 */
    default boolean neko$isCancelled() {
        throw new UnsupportedOperationException("EventSpec.neko$isCancelled not implemented");
    }
}
