package dev.latvian.mods.kubejs.event;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;

/** Native event payload contract; no Rhino context or listener owner is created. */
public interface KubeEvent {
    default Object defaultExitValue(Context context) {
        return null;
    }

    default Object mapExitValue(Context context, Object value) {
        TypeInfo type = getExitValueType();
        return type == null ? value : context.jsToJava(value, type);
    }

    default TypeInfo getExitValueType() {
        return null;
    }

    default void afterPosted(EventResult result) {
    }

    default Object cancel(Context context) {
        throw unsupportedExit();
    }

    default Object cancel(Context context, Object value) {
        throw unsupportedExit();
    }

    default Object success(Context context) {
        throw unsupportedExit();
    }

    default Object success(Context context, Object value) {
        throw unsupportedExit();
    }

    default Object exit(Context context) {
        throw unsupportedExit();
    }

    default Object exit(Context context, Object value) {
        throw unsupportedExit();
    }

    default Object cancel() {
        throw unsupportedExit();
    }

    default Object cancel(Object value) {
        throw unsupportedExit();
    }

    default Object success() {
        throw unsupportedExit();
    }

    default Object success(Object value) {
        throw unsupportedExit();
    }

    default Object exit() {
        throw unsupportedExit();
    }

    default Object exit(Object value) {
        throw unsupportedExit();
    }

    private static UnsupportedOperationException unsupportedExit() {
        return EventResult.unsupportedExit();
    }
}
