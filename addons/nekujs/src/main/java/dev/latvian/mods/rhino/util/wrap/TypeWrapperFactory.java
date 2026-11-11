package dev.latvian.mods.rhino.util.wrap;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;

/** Converts an input using the current native context and requested target metadata. */
@FunctionalInterface
public interface TypeWrapperFactory<T> {
    T wrap(Context context, Object value, TypeInfo target);
}
