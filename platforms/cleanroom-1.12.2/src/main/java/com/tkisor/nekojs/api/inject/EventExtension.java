package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.EventSpec;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * Forge 事件统一扩展方法（注入到 1.12.2 {@link Event} 基类）。
 *
 * <p>脚本侧 {@code event.cancel()} 等价于 {@code return true} 取消事件。
 * 1.12.2 的 {@link Event#setCanceled(boolean)} 在非 {@code @Cancelable} 事件上会抛异常，
 * 所以这里 try-catch 安全降级。
 */
@RemapByPrefix("neko$")
public interface EventExtension extends EventSpec {

    /**
     * 取消当前事件。仅对 {@code @Cancelable} 事件生效。
     *
     * @return {@code true} 如果事件被成功取消
     */
    @Override
    default boolean neko$cancel() {
        try {
            ((Event) this).setCanceled(true);
            return true;
        } catch (IllegalArgumentException e) {
            // 非 @Cancelable 事件，setCanceled 抛异常——安全降级
            return false;
        }
    }

    /**
     * 当前事件是否已被取消。
     *
     * @return {@code true} 如果事件已被取消
     */
    @Override
    default boolean neko$isCancelled() {
        return ((Event) this).isCanceled();
    }
}
