package com.tkisor.nekojs.api.data;

import java.util.Collection;

/**
 * Abstract view of a JS value that hides the underlying GraalVM {@code Value}.
 *
 * <p>It sorts script-side values into null, string, number, boolean, host object, array and
 * proxy object, so a {@link JsTypeAdapter} can inspect and read them without depending on the
 * engine implementation.
 */
public interface JsValueView {
    /** Returns whether the value is {@code null}. */
    boolean isNull();
    /** Returns whether the value is a string. */
    boolean isString();
    /** Returns whether the value is a number. */
    boolean isNumber();
    /** Returns whether the value is a boolean. */
    boolean isBoolean();
    /** Returns whether the value is a Java host object. */
    boolean isHostObject();
    /** Returns whether the value is an array. */
    boolean isArray();

    /** Returns whether the value is a JS proxy object; {@code false} by default. */
    default boolean isProxyObject() {
        return false;
    }

    /** Returns the underlying proxy object; throws {@link UnsupportedOperationException} when the value is not a proxy. */
    default Object asProxyObject() {
        throw new UnsupportedOperationException("value is not a proxy object");
    }

    /** Returns the string value; throws when the value is not a string. */
    String asString();
    /** Returns the value as an int, truncating any fractional part. */
    int asInt();
    /** Returns the value as a double. */
    double asDouble();
    /** Returns the value as a boolean. */
    boolean asBoolean();

    /** Returns the value as a host object, cast to the given type; returns {@code null} when the
     *  value cannot be converted. */
    <T> T asHostObject(Class<T> type);

    /** Returns whether the value has the given member. */
    boolean hasMember(String key);
    /** Returns the member with the given key. */
    JsValueView getMember(String key);
    /** Returns the element at the given index. */
    JsValueView getArrayElement(long index);
    /** Returns the number of elements in the array. */
    long getArraySize();
    /** Returns the names of all members. */
    Collection<String> getMemberKeys();
}
