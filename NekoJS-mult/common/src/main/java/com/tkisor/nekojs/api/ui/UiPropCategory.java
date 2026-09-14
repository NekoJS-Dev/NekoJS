package com.tkisor.nekojs.api.ui;

/**
 * props 分类（jsx-ui-spec「props 规则」）：内容、布局、视觉、可访问性、状态和事件。
 *
 * <p>分类只承载契约文档与校验分组语义；不实现 CSS selector、继承、浏览器 box model、
 * 任意 CSS 字符串或 DOM attribute 扩展。
 */
public enum UiPropCategory {
    /** 内容类：text / value / placeholder / title / src / fit / wrap。 */
    CONTENT,
    /** 布局类：尺寸、间距、对齐等受控 Minecraft UI 约束。 */
    LAYOUT,
    /** 视觉类：背景、边框、颜色、圆角、透明度、字号、字体。 */
    VISUAL,
    /** 可访问性类：narration、tooltip。 */
    ACCESSIBILITY,
    /** 状态类：disabled / focused / hovered / pressed / error / loading / maxLength。 */
    STATE,
    /** 事件类：lowerCamelCase 的 on* 属性，值必须是脚本函数或明确的空值。 */
    EVENT
}
