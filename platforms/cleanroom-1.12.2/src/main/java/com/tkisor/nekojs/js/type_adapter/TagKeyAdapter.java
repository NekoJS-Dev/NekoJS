package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;

import java.util.Collections;
import java.util.List;

/**
 * 1.12.2 TagKey 适配器（stub）。
 * <b>1.12.2 无 {@code TagKey} 类</b>，本适配器始终抛 {@link UnsupportedOperationException}，
 * 仅供类型系统占位。
 *
 * <p>1.12.2 中 tag 功能通过 {@code OreDictionary}（物品）或直接遍历注册表实现，
 * 无泛型 TagKey 抽象。</p>
 */
public class TagKeyAdapter extends AbstractJSTypeAdapter<Object> {

    @Override
    public Class<Object> getTargetClass() {
        return Object.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return Collections.emptyList();
    }

    @Override
    protected Object fromString(String s) {
        throw new UnsupportedOperationException("TagKey is not available in Minecraft 1.12.2");
    }

    @Override
    protected Object fromHostObject(Object host) {
        throw new UnsupportedOperationException("TagKey is not available in Minecraft 1.12.2");
    }

    @Override
    protected boolean acceptNull() {
        return false;
    }
}
