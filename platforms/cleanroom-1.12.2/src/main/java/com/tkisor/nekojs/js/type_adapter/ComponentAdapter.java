package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJsTypeAdapter;
import com.tkisor.nekojs.api.data.JsValueView;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.data.TextArgument;
import com.tkisor.nekojs.api.data.TextStyle;
import com.tkisor.nekojs.api.data.TextClickEvent;
import com.tkisor.nekojs.api.data.TextHoverEvent;
import com.tkisor.nekojs.core.api.ManagedApiValueAccess;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.List;
import java.util.Locale;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 Component 适配器：使用 {@link ITextComponent} 替代 1.21.1 的 {@code Component}。
 * 字符串转换为 {@link TextComponentString}。富文本样式经 {@link Style} 应用。
 */
public class ComponentAdapter extends AbstractJsTypeAdapter<ITextComponent> {

    public ComponentAdapter() {
        super(ITextComponent.class);
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string(),
                raw("$TextValue"));
    }

    @Override
    protected ITextComponent fromString(String s) {
        return new TextComponentString(s);
    }

    @Override
    protected ITextComponent fromHostObject(Object host) {
        if (host instanceof ITextComponent component) return component;
        return null;
    }

    @Override
    protected boolean acceptOther(JsValueView value) {
        return value.isProxyObject() && ManagedApiValueAccess.is(value.asProxyObject(), TextValue.class);
    }

    @Override
    protected ITextComponent fromOther(JsValueView value) {
        TextValue text = ManagedApiValueAccess.unwrap(value.asProxyObject(), TextValue.class);
        if (text == null) return null;
        return convert(text);
    }

    private static ITextComponent convert(TextValue value) {
        if (value instanceof TextValue.Styled styled) {
            ITextComponent inner = convert(styled.value());
            applyStyle(inner, styled.style());
            return inner;
        }
        if (value instanceof TextValue.Literal literal) {
            return new TextComponentString(literal.text());
        }
        if (value instanceof TextValue.Translatable translatable) {
            Object[] arguments = translatable.arguments().stream().map(ComponentAdapter::convertArgument).toArray();
            return new TextComponentTranslation(translatable.key(), arguments);
        }
        ITextComponent result = new TextComponentString("");
        for (TextValue child : ((TextValue.Sequence) value).values()) {
            result.appendSibling(convert(child));
        }
        return result;
    }

    private static Object convertArgument(TextArgument argument) {
        if (argument instanceof TextArgument.NestedText nested) return convert(nested.value());
        if (argument instanceof TextArgument.StringValue string) return string.value();
        if (argument instanceof TextArgument.NumberValue number) return number.nativeNumber();
        return ((TextArgument.BooleanValue) argument).value();
    }

    /**
     * 把可移植 {@link TextStyle} 应用到组件上。1.12.2 限制：
     * <ul>
     *   <li>颜色仅支持 {@link TextFormatting} 的 16 个命名色；任意 hex（{@code #RRGGBB}）忽略。</li>
     *   <li>无 font 概念（font 1.13+ 才有）；font 字段忽略。</li>
     *   <li>点击事件无 COPY_TO_CLIPBOARD；该动作忽略。</li>
     * </ul>
     */
    private static void applyStyle(ITextComponent component, TextStyle style) {
        if (style.isEmpty()) {
            return;
        }
        Style mc = component.getStyle();
        if (style.bold() != null) mc.setBold(style.bold());
        if (style.italic() != null) mc.setItalic(style.italic());
        if (style.underlined() != null) mc.setUnderlined(style.underlined());
        if (style.strikethrough() != null) mc.setStrikethrough(style.strikethrough());
        if (style.obfuscated() != null) mc.setObfuscated(style.obfuscated());
        if (style.color() != null) {
            TextFormatting formatting = resolveColor(style.color());
            if (formatting != null) mc.setColor(formatting);
        }
        if (style.insertion() != null) mc.setInsertion(style.insertion());
        // font 在 1.12.2 不存在，忽略
        if (style.clickEvent() != null) {
            ClickEvent click = convertClick(style.clickEvent());
            if (click != null) mc.setClickEvent(click);
        }
        if (style.hoverEvent() != null) {
            HoverEvent hover = convertHover(style.hoverEvent());
            if (hover != null) mc.setHoverEvent(hover);
        }
    }

    private static TextFormatting resolveColor(String color) {
        if (color == null || color.isBlank()) return null;
        // 仅识别 TextFormatting 的 16 个命名色；忽略 hex（1.12.2 无任意颜色支持）
        try {
            return TextFormatting.getValueByName(color.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ClickEvent convertClick(TextClickEvent event) {
        // 1.12.2: ClickEvent(Action, String)；Action 仅 5 个（无 COPY_TO_CLIPBOARD）
        return switch (event) {
            case TextClickEvent.RunCommand e -> new ClickEvent(ClickEvent.Action.RUN_COMMAND, e.command());
            case TextClickEvent.SuggestCommand e -> new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, e.command());
            case TextClickEvent.OpenUrl e -> new ClickEvent(ClickEvent.Action.OPEN_URL, e.url());
            case TextClickEvent.OpenFile e -> new ClickEvent(ClickEvent.Action.OPEN_FILE, e.path());
            case TextClickEvent.ChangePage e -> new ClickEvent(ClickEvent.Action.CHANGE_PAGE, e.value());
            // COPY_TO_CLIPBOARD 在 1.12.2 不存在，忽略
            default -> null;
        };
    }

    private static HoverEvent convertHover(TextHoverEvent event) {
        if (event instanceof TextHoverEvent.ShowText showText) {
            return new HoverEvent(HoverEvent.Action.SHOW_TEXT, convert(showText.text()));
        }
        return null;
    }
}
