package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

public final class TextJS {

    private TextJS() {}

    public static TextComponentString of(String text) {
        return new TextComponentString(text == null ? "" : text);
    }

    public static TextComponentString empty() {
        return new TextComponentString("");
    }

    public static TextComponentTranslation translatable(String key, Object... args) {
        return new TextComponentTranslation(key, args);
    }

    public static TextComponentString ofValues(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (Object v : values) {
            if (v != null) sb.append(v);
        }
        return new TextComponentString(sb.toString());
    }
}
