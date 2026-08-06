package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * 事件统一扩展规范（cancel / isCancelled）。
 *
 * <p>各平台的 {@code EventExtension} 必须 {@code extends EventSpec}。
 *
 * <p>原生事件类（NF {@code net.neoforged.bus.api.Event} / CR
 * {@code net.minecraftforge.fml.common.eventhandler.Event}）不提供统一的取消接口，
 * 故此 spec 提供 {@code neko$cancel()} / {@code neko$isCancelled()} 作为跨平台统一方法。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
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
