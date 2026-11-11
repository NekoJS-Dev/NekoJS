package dev.latvian.mods.kubejs.recipe.component;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;
import java.util.regex.Pattern;

public record UniqueIdBuilder(StringBuilder builder) {
    public static final Pattern NON_W_PATTERN = Pattern.compile("\\W");
    public static final Pattern MULTIPLE_UNDERSCORES_PATTERN = Pattern.compile("_{2,}");
    public void appendSeparator() { builder.append('_'); }
    public void append(String string) { if (string != null && !string.isEmpty()) builder.append('_').append(string); }
    public void append(ResourceLocation id) {
        if (id == null) return;
        builder.append('_');
        if (!id.getNamespace().equals("minecraft") && !id.getNamespace().equals("kubejs")) builder.append(id.getNamespace()).append('_');
        builder.append(id.getPath());
    }
    public void append(ResourceKey<?> key) { if (key != null) append(key.location()); }
    public String build() {
        String result = builder.toString();
        builder.setLength(0);
        result = MULTIPLE_UNDERSCORES_PATTERN.matcher(NON_W_PATTERN.matcher(result.toLowerCase(Locale.ROOT)).replaceAll("_")).replaceAll("_");
        if (result.startsWith("_")) result = result.substring(1);
        if (result.endsWith("_")) result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? null : result;
    }
}
