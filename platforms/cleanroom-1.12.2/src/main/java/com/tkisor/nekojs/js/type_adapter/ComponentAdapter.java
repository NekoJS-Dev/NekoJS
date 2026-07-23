package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 Component 适配器：使用 {@link ITextComponent} 替代 1.21.1 的 {@code Component}。
 * 字符串转换为 {@link TextComponentString}。
 */
public class ComponentAdapter extends AbstractJSTypeAdapter<ITextComponent> {

    @Override
    public Class<ITextComponent> getTargetClass() {
        return ITextComponent.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string());
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
}
