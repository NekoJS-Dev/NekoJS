package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class TextJS {

    private TextJS() {}

    public static MutableComponent of(String text) {
        return Component.literal(text == null ? "" : text);
    }

    public static MutableComponent empty() {
        return Component.empty();
    }

    public static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static MutableComponent ofValues(Object... values) {
        MutableComponent result = Component.empty();
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof Component c) {
                result.append(c);
            } else {
                result.append(Component.literal(String.valueOf(v)));
            }
        }
        return result;
    }
}
