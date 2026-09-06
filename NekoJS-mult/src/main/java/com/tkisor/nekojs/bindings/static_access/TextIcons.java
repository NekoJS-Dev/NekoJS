package com.tkisor.nekojs.bindings.static_access;

/**
 * 常用 unicode 图标/符号常量（KubeJS {@code TextIcons} 全局的对标；NekoJS 版本不带
 * 自带字体资源，直接用系统字形，跨字体渲染有差异时请自行用 {@code Text} 设样式）。
 *
 * <p>绑定为类，脚本 {@code TextIcons.CHECK}、{@code TextIcons.STAR}。
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
