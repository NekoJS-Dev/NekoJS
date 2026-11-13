package com.tkisor.nekojs.bindings.static_access;

/**
 * Common unicode icons and symbol constants, the counterpart of the KubeJS {@code TextIcons} global.
 * NekoJS ships no font resources and uses the system glyphs, so when a symbol renders differently
 * across fonts, set the style yourself with {@code Text}.
 *
 * <p>Bound as a class; scripts use {@code TextIcons.CHECK} and {@code TextIcons.STAR}.
 */
public interface TextIcons {
    String CHECK = "✔";
    String CROSS = "✘";
    String PLUS = "＋";
    String MINUS = "−";
    String ARROW_RIGHT = "→";
    String ARROW_LEFT = "←";
    String ARROW_UP = "↑";
    String ARROW_DOWN = "↓";
    String STAR = "★";
    String HEART = "❤";
    String WARNING = "⚠";
    String GEAR = "⚙";
    String DOT = "•";
    String BULLET_SQUARE = "▪";
    String SKULL = "☠";
    String ANVIL = "⚒";
    String PICKAXE = "⛏";
    String SWORD = "⚔";
    String SHIELD = "⛨";
    String POTION = "⚗";
    String ENCHANT_BOOK = "✎";
    String SUN = "☀";
    String MOON = "☾";
    String SNOWFLAKE = "❄";
    String FIRE = "🔥";
}
