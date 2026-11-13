// 26.x implementation; this file must not contain version guards again. The 1.21.1
// implementation is the same-named file under versions/1.21.1/src, so keep it in sync when
// this file's behavior changes.
package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.BaseJsTypeAdapter;
import com.tkisor.nekojs.api.data.JsValueView;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.data.TextArgument;
import com.tkisor.nekojs.api.data.TextStyle;
import com.tkisor.nekojs.api.data.TextClickEvent;
import com.tkisor.nekojs.api.data.TextHoverEvent;
import com.tkisor.nekojs.core.api.ManagedApiValueAccess;
import java.util.List;
import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public class ComponentAdapter extends BaseJsTypeAdapter<Component> {
    public ComponentAdapter() {
        super(Component.class);
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                raw("$TextValue"));
    }

    @Override
    protected Component fromString(String s) {
        return Component.literal(s);
    }

    @Override
    protected Component fromHostObject(Object host) {
        if (host instanceof Component component) return component;
        return null; // Not recognized
    }

    @Override
    protected boolean acceptOther(JsValueView value) {
        return value.isProxyObject() && ManagedApiValueAccess.is(value.asProxyObject(), TextValue.class);
    }

    @Override
    protected Component fromOther(JsValueView value) {
        TextValue text = ManagedApiValueAccess.unwrap(value.asProxyObject(), TextValue.class);
        if (text == null) return null;
        return convert(text);
    }

    private static Component convert(TextValue value) {
        if (value instanceof TextValue.Styled styled) {
            Component inner = convert(styled.value());
            if (inner instanceof MutableComponent mutable) {
                applyStyle(mutable, styled.style());
                return mutable;
            }
            MutableComponent wrapped = Component.empty().append(inner);
            applyStyle(wrapped, styled.style());
            return wrapped;
        }
        if (value instanceof TextValue.Literal literal) {
            return Component.literal(literal.text());
        }
        if (value instanceof TextValue.Translatable translatable) {
            Object[] arguments = translatable.arguments().stream().map(ComponentAdapter::convertArgument).toArray();
            if (translatable.fallback() != null) {
                return Component.translatable(translatable.key(), translatable.fallback(), arguments);
            }
            return Component.translatable(translatable.key(), arguments);
        }
        if (value instanceof TextValue.Keybind keybind) {
            return Component.keybind(keybind.keybind());
        }
        if (value instanceof TextValue.Score score) {
            return Component.score(score.name(), score.objective());
        }
        if (value instanceof TextValue.Selector selector) {
            // NF26 limitation: Component.selector requires a CommandBuildContext that is unavailable
            // here, so the value degrades to a literal.
            // 26.x (MC 1.21.6+) changes the Component.selector signature to (ParsedSelector),
            // and building a ParsedSelector from a pattern needs a CommandBuildContext that the
            // adapter layer does not have, so it can only degrade to a literal.
            // Selectors still render normally on 1.21.1 / 1.12.2.
            return Component.literal(selector.pattern());
        }
        MutableComponent result = Component.empty();
        for (TextValue child : ((TextValue.Sequence) value).values()) {
            result = result.copy().append(convert(child));
        }
        return result;
    }

    private static Object convertArgument(TextArgument argument) {
        if (argument instanceof TextArgument.NestedText nested) return convert(nested.value());
        if (argument instanceof TextArgument.StringValue string) return string.value();
        if (argument instanceof TextArgument.NumberValue number) return number.nativeNumber();
        return ((TextArgument.BooleanValue) argument).value();
    }

    /** Applies a portable {@link TextStyle} to the component; only non-null fields override. */
    private static void applyStyle(MutableComponent component, TextStyle style) {
        if (style.isEmpty()) {
            return;
        }
        Style base = component.getStyle();
        Style patch = base;
        if (style.bold() != null) patch = patch.withBold(style.bold());
        if (style.italic() != null) patch = patch.withItalic(style.italic());
        if (style.underlined() != null) patch = patch.withUnderlined(style.underlined());
        if (style.strikethrough() != null) patch = patch.withStrikethrough(style.strikethrough());
        if (style.obfuscated() != null) patch = patch.withObfuscated(style.obfuscated());
        if (style.color() != null) {
            TextColor color = TextColor.parseColor(style.color()).result().orElse(null);
            if (color != null) patch = patch.withColor(color);
        }
        if (style.insertion() != null) patch = patch.withInsertion(style.insertion());
        if (style.font() != null) {
            Identifier fontId = Identifier.tryParse(style.font());
            if (fontId != null) patch = patch.withFont(new FontDescription.Resource(fontId));
        }
        if (style.clickEvent() != null) {
            ClickEvent click = convertClick(style.clickEvent());
            if (click != null) patch = patch.withClickEvent(click);
        }
        if (style.hoverEvent() != null) {
            HoverEvent hover = convertHover(style.hoverEvent());
            if (hover != null) patch = patch.withHoverEvent(hover);
        }
        component.setStyle(patch);
    }

    private static ClickEvent convertClick(TextClickEvent event) {
        return switch (event) {
            case TextClickEvent.RunCommand e -> new ClickEvent.RunCommand(e.command());
            case TextClickEvent.SuggestCommand e -> new ClickEvent.SuggestCommand(e.command());
            case TextClickEvent.OpenUrl e -> {
                try {
                    yield new ClickEvent.OpenUrl(new java.net.URI(e.url()));
                } catch (java.net.URISyntaxException ex) {
                    yield null;
                }
            }
            case TextClickEvent.OpenFile e -> new ClickEvent.OpenFile(e.path());
            case TextClickEvent.CopyToClipboard e -> new ClickEvent.CopyToClipboard(e.text());
            case TextClickEvent.ChangePage e -> new ClickEvent.ChangePage(e.page());
        };
    }

    private static HoverEvent convertHover(TextHoverEvent event) {
        if (event instanceof TextHoverEvent.ShowText showText) {
            return new HoverEvent.ShowText(convert(showText.text()));
        }
        return null;
    }
}
