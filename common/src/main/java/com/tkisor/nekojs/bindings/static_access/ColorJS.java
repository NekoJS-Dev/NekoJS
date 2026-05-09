package com.tkisor.nekojs.bindings.static_access;

public final class ColorJS {
    public int rgb(int red, int green, int blue) {
        return argb(255, red, green, blue);
    }

    public int argb(int alpha, int red, int green, int blue) {
        return (channel(alpha) << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    public int alpha(int color) {
        return color >>> 24 & 255;
    }

    public int red(int color) {
        return color >>> 16 & 255;
    }

    public int green(int color) {
        return color >>> 8 & 255;
    }

    public int blue(int color) {
        return color & 255;
    }

    public String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    public String hexArgb(int color) {
        return String.format("#%08X", color);
    }

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
