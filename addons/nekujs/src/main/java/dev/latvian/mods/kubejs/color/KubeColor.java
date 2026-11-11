package dev.latvian.mods.kubejs.color;

import net.minecraft.network.chat.TextColor;

public interface KubeColor {
    int kjs$getARGB();
    default int kjs$getRGB() { return kjs$getARGB() & 0xFFFFFF; }
    default int kjs$getFireworkRGB() { return kjs$getRGB(); }
    default String kjs$toHexString() { return String.format(java.util.Locale.ROOT, "#%08X", kjs$getARGB()); }
    default String kjs$serialize() { return kjs$toHexString(); }
    default TextColor kjs$createTextColor() { return TextColor.fromRgb(kjs$getRGB()); }
}
