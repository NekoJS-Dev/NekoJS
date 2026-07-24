package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.network.chat.Component;

public class ComponentAdapter extends AbstractJSTypeAdapter<Component> {
    @Override
    public Class<Component> getTargetClass() {
        return Component.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                string());
    }

    @Override
    protected Component fromString(String s) {
        return Component.literal(s);
    }

    @Override
    protected Component fromHostObject(Object host) {
        if (host instanceof Component component) return component;
        return null; // 不识别
    }
}
