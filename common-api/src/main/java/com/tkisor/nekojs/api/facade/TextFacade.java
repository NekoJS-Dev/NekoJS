package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.TextValue;

import java.util.List;

public interface TextFacade {
    TextValue of(String text);

    TextValue empty();

    TextValue translatable(String key, List<Object> arguments);

    TextValue ofValues(List<Object> values);

    TextValue append(TextValue receiver, List<Object> values);
}
