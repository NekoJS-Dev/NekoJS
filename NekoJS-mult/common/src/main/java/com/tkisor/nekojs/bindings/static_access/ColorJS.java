package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

/**
 * 颜色工具：ARGB int 的打包/拆解与十六进制字符串解析。
 */
@Doc("Color helpers for packing, extracting, and parsing ARGB color ints.")
public final class ColorJS {
    /** 打包 RGB 通道为不透明颜色（各通道自动钳制到 0-255）。 */
    @Doc("Packs RGB channels into an opaque color int; channels are clamped to 0-255.")
    @Param(name = "red", value = "red channel, 0-255")
    @Param(name = "green", value = "green channel, 0-255")
    @Param(name = "blue", value = "blue channel, 0-255")
    @Return("opaque ARGB color int (alpha = 255)")
    public int rgb(int red, int green, int blue) {
        return argb(255, red, green, blue);
    }

    /** 打包 ARGB 通道为颜色 int（各通道自动钳制到 0-255）。 */
    @Doc("Packs ARGB channels into a color int; channels are clamped to 0-255.")
    @Param(name = "alpha", value = "alpha channel, 0-255")
    @Param(name = "red", value = "red channel, 0-255")
    @Param(name = "green", value = "green channel, 0-255")
    @Param(name = "blue", value = "blue channel, 0-255")
    @Return("ARGB color int")
    public int argb(int alpha, int red, int green, int blue) {
        return (channel(alpha) << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    /** 提取 alpha 通道。 */
    @Doc("Extracts the alpha channel of a color.")
    @Param(name = "color", value = "ARGB color int")
    @Return("alpha channel value, 0-255")
    public int alpha(int color) {
        return color >>> 24 & 255;
    }

    /** 提取 red 通道。 */
    @Doc("Extracts the red channel of a color.")
    @Param(name = "color", value = "ARGB color int")
    @Return("red channel value, 0-255")
    public int red(int color) {
        return color >>> 16 & 255;
    }

    /** 提取 green 通道。 */
    @Doc("Extracts the green channel of a color.")
    @Param(name = "color", value = "ARGB color int")
    @Return("green channel value, 0-255")
    public int green(int color) {
        return color >>> 8 & 255;
    }

    /** 提取 blue 通道。 */
    @Doc("Extracts the blue channel of a color.")
    @Param(name = "color", value = "ARGB color int")
    @Return("blue channel value, 0-255")
    public int blue(int color) {
        return color & 255;
    }

    /** 格式化为 {@code #RRGGBB}（忽略 alpha）。 */
    @Doc("Formats the color as '#RRGGBB', ignoring the alpha channel.")
    @Param(name = "color", value = "ARGB color int")
    @Return("hex string like '#RRGGBB'")
    public String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /** 格式化为 {@code #AARRGGBB}。 */
    @Doc("Formats the color as '#AARRGGBB', including the alpha channel.")
    @Param(name = "color", value = "ARGB color int")
    @Return("hex string like '#AARRGGBB'")
    public String hexArgb(int color) {
        return String.format("#%08X", color);
    }

    /** 解析 {@code #RRGGBB} 或 {@code #AARRGGBB} 十六进制字符串（前缀 {@code #} 可省略）。 */
    @Doc("Parses '#RRGGBB' or '#AARRGGBB' into a color int; the leading '#' is optional.")
    @Param(name = "value", value = "hex color string, with or without leading '#'")
    @Return("ARGB color int; 6-digit form gets opaque alpha")
    public int parse(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() == 6) {
            return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
        }
        if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Color must be #RRGGBB or #AARRGGBB: " + value);
    }

    private int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
