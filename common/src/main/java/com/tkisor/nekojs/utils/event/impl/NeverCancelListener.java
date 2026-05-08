package com.tkisor.nekojs.utils.event.impl;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public record NeverCancelListener<E>(Consumer<E> consumer) implements Predicate<E> {
    public NeverCancelListener(Consumer<E> consumer) {
        this.consumer = Objects.requireNonNull(consumer);
    }

    @Override
    public boolean test(E e) {
        consumer.accept(e);
        return false;
    }
}
