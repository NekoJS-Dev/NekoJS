package com.tkisor.nekojs.api.data;

import java.util.Collection;

public interface JsValueView {
    boolean isNull();
    boolean isString();
    boolean isNumber();
    boolean isBoolean();
    boolean isHostObject();
    boolean isArray();

    String asString();
    int asInt();
    double asDouble();
    boolean asBoolean();

    <T> T asHostObject(Class<T> type);

    boolean hasMember(String key);
    JsValueView getMember(String key);
    JsValueView getArrayElement(long index);
    long getArraySize();
    Collection<String> getMemberKeys();
}
