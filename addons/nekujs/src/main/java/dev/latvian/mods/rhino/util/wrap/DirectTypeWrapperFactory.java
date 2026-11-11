package dev.latvian.mods.rhino.util.wrap;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;

@FunctionalInterface
public interface DirectTypeWrapperFactory<T> extends TypeWrapperFactory<T> {
    T wrap(Object value);

    @Override
    default T wrap(Context context, Object value, TypeInfo target) {
        return wrap(value);
    }
}
