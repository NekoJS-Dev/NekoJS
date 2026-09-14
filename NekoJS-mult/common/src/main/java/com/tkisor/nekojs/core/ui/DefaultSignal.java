package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiSignal;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** {@link UiSignal} 默认实现：写入经 owner 合并失效并做 owner-thread 校验。 */
final class DefaultSignal<T> implements UiSignal<T> {

    private final DefaultUiStateOwner owner;
    private final String name;
    private T value;

    DefaultSignal(DefaultUiStateOwner owner, String name, T initial) {
        this.owner = owner;
        this.name = name;
        this.value = initial;
    }

    @Override
    public T get() {
        owner.link(this);
        return value;
    }

    @Override
    public void set(T next) {
        owner.requireOwnerThread("signal.set(" + name + ")");
        if (Objects.equals(value, next)) {
            return;
        }
        value = next;
        owner.markDirty(this);
    }

    @Override
    public void update(UnaryOperator<T> updater) {
        Objects.requireNonNull(updater, "updater");
        owner.requireOwnerThread("signal.update(" + name + ")");
        T next = updater.apply(value);
        set(next);
    }

    /** 当前值（不建立依赖；owner 内部与测试观察用）。 */
    T peek() {
        return value;
    }

    @Override
    public String toString() {
        return "UiSignal(" + name + ")";
    }
}
