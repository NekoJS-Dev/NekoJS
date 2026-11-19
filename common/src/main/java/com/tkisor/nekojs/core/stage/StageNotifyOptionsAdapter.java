package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.AdapterInputShape.Slot;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import com.tkisor.nekojs.core.api.ManagedApiValueAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Type adapter converting JavaScript notification option objects
 * ({@code { silent?, title?, chat?, actionBar? }}) and host {@link StageNotifyOptions} instances
 * into {@link StageNotifyOptions}.
 */
public final class StageNotifyOptionsAdapter extends AbstractJSTypeAdapter<StageNotifyOptions> {

    private static final String EXPECTED_SHAPE =
            "{ silent?: boolean, title?: boolean, chat?: boolean, actionBar?: boolean }";
    private static final Set<String> KNOWN_KEYS =
            Set.of("silent", "title", "chat", "actionBar");

    @Override
    public Class<StageNotifyOptions> getTargetClass() {
        return StageNotifyOptions.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                AdapterInputShape.self(),
                AdapterInputShape.object(
                        Slot.opt("silent", AdapterInputShape.bool()),
                        Slot.opt("title", AdapterInputShape.bool()),
                        Slot.opt("chat", AdapterInputShape.bool()),
                        Slot.opt("actionBar", AdapterInputShape.bool())
                )
        );
    }

    @Override
    protected StageNotifyOptions fromHostObject(Object host) {
        if (host instanceof StageNotifyOptions options) {
            return options;
        }
        StageNotifyOptions unwrapped = ManagedApiValueAccess.unwrap(host, StageNotifyOptions.class);
        if (unwrapped != null) {
            return unwrapped;
        }
        if (host instanceof Map<?, ?> map) {
            for (Object rawKey : map.keySet()) {
                if (!(rawKey instanceof String key) || !KNOWN_KEYS.contains(key)) {
                    throw new ValueConversionException(
                            StageNotifyOptions.class,
                            EXPECTED_SHAPE,
                            host,
                            "unknown option key '" + rawKey + "'");
                }
            }
            return new StageNotifyOptions(
                    readMapBoolean(map, "silent", host),
                    readMapBoolean(map, "title", host),
                    readMapBoolean(map, "chat", host),
                    readMapBoolean(map, "actionBar", host)
            );
        }
        return null;
    }

    @Override
    protected boolean acceptOther(Value value) {
        if (value.isProxyObject()
                && ManagedApiValueAccess.is(value.asProxyObject(), StageNotifyOptions.class)) {
            return true;
        }
        if (!value.hasMembers()
                || value.hasArrayElements()
                || value.canExecute()
                || value.isBoolean()
                || value.isNumber()
                || value.isString()) {
            return false;
        }
        for (String key : value.getMemberKeys()) {
            if (!KNOWN_KEYS.contains(key)) {
                return false;
            }
            Value member = value.getMember(key);
            if (member != null && !member.isNull() && !member.isBoolean()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected StageNotifyOptions fromOther(Value value) {
        if (value.isProxyObject()) {
            StageNotifyOptions unwrapped =
                    ManagedApiValueAccess.unwrap(value.asProxyObject(), StageNotifyOptions.class);
            if (unwrapped != null) {
                return unwrapped;
            }
        }
        if (!value.hasMembers() || value.hasArrayElements() || value.canExecute()) {
            throw new ValueConversionException(
                    StageNotifyOptions.class, EXPECTED_SHAPE, value, "expected an options object literal");
        }
        for (String key : value.getMemberKeys()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw new ValueConversionException(
                        StageNotifyOptions.class, EXPECTED_SHAPE, value, "unknown option key '" + key + "'");
            }
        }
        return new StageNotifyOptions(
                readMemberBoolean(value, "silent"),
                readMemberBoolean(value, "title"),
                readMemberBoolean(value, "chat"),
                readMemberBoolean(value, "actionBar")
        );
    }

    private static Boolean readMemberBoolean(Value obj, String key) {
        if (!obj.hasMember(key)) {
            return null;
        }
        Value member = obj.getMember(key);
        if (member == null || member.isNull()) {
            return null;
        }
        if (!member.isBoolean()) {
            throw new ValueConversionException(
                    StageNotifyOptions.class,
                    EXPECTED_SHAPE,
                    obj,
                    "field '" + key + "' must be a boolean");
        }
        return member.asBoolean();
    }

    private static Boolean readMapBoolean(Map<?, ?> map, String key, Object source) {
        if (!map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Value v) {
            if (v.isNull()) {
                return null;
            }
            if (v.isBoolean()) {
                return v.asBoolean();
            }
        }
        throw new ValueConversionException(
                StageNotifyOptions.class,
                EXPECTED_SHAPE,
                source,
                "field '" + key + "' must be a boolean");
    }
}
