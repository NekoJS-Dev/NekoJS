package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.inject.EventSpec;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Forge 事件统一扩展方法（注入到 {@link Event} 基类）。
 *
 * <p>脚本侧 {@code event.cancel()} 等价于 {@code return true} 取消事件，但更直观可发现。
 * 只有实现了 {@link ICancellableEvent} 的事件真正生效；不可取消的事件调用时 no-op。
 */
@RemapByPrefix("neko$")
public interface EventExtension extends EventSpec {

    @Override
    default boolean neko$cancel() {
        if (this instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
            return true;
        }
        return false;
    }

    @Override
    default boolean neko$isCancelled() {
        return this instanceof ICancellableEvent cancellable && cancellable.isCanceled();
    }
}
