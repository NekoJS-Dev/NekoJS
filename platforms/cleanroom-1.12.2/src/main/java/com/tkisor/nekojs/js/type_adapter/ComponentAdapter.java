package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJsTypeAdapter;
import com.tkisor.nekojs.api.data.JsValueView;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.data.TextArgument;
import com.tkisor.nekojs.core.api.ManagedApiValueAccess;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 Component 适配器：使用 {@link ITextComponent} 替代 1.21.1 的 {@code Component}。
 * 字符串转换为 {@link TextComponentString}。
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
}
