package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * 富文本悬停事件（跨平台、MC 版本无关）。
 *
 * <p>当前仅支持「显示文本」一种动作（跨 1.12.2 / 1.21.x 都稳定可用）。
 * 物品/实体悬停因各版本数据载体差异较大，暂不纳入可移植契约。
 */
public sealed interface TextHoverEvent permits TextHoverEvent.ShowText {

    /** 动作名（脚本侧 {@code event.action}）。 */
    String action();

    /** 显示一段文本。{@code text} 为另一段 {@link TextValue}（可继续带样式）。 */
    record ShowText(TextValue text) implements TextHoverEvent {
        public ShowText { Objects.requireNonNull(text, "text"); }
        @Override public String action() { return "showText"; }
    }
}
