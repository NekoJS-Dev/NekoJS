package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.ScriptTypeView;
import java.lang.reflect.Modifier;

/** Resolves output-only authoring views without replacing native instances or input contracts. */
public final class ScriptTypeViews {
    private ScriptTypeViews() {}

    public static Class<?> outputClass(Class<?> source) {
        if (source == null) return null;
        ScriptTypeView annotation = source.getAnnotation(ScriptTypeView.class);
        if (annotation == null) return source;
        Class<?> view = annotation.value();
        if (!view.isInterface() || !Modifier.isPublic(view.getModifiers()) || !view.isAssignableFrom(source)
                || view.getTypeParameters().length != 0) {
            throw new IllegalArgumentException("[NEKO-4065] Script output view must be an implemented public non-generic interface: "
                    + source.getName() + " -> " + view.getName());
        }
        return view;
    }
}
