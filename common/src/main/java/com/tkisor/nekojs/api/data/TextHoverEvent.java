package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * A hover event for rich text, independent of platform and MC version.
 *
 * <p>Only "show text" is supported, because it works the same from 1.12.2 through 1.21.x.
 * Item and entity hover are left out for now: the data each version carries differs too much
 * to port.
 */
public sealed interface TextHoverEvent permits TextHoverEvent.ShowText {

    /** The action name, exposed to scripts as {@code event.action}. */
    String action();

    /** Shows a piece of text. {@code text} is another {@link TextValue} and can carry its own
     * style. */
    record ShowText(TextValue text) implements TextHoverEvent {
        public ShowText { Objects.requireNonNull(text, "text"); }
        @Override public String action() { return "showText"; }
    }
}
