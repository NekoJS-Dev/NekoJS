package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.core.api.ManagedApiValueAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;

/**
 * Type adapter converting JavaScript strings, host {@link NekoId} instances, and managed API
 * {@code ID.of(...)} proxy objects into {@link NekoId} resource identifiers.
 *
 * <p>String inputs accept both bare resource identifiers ({@code "c:armors/diamond"}) and
 * hash-prefixed tag strings ({@code "#c:armors/diamond"}), validating namespace and path syntax
 * via {@link NekoId#resource(String)}.
 */
public final class NekoIdAdapter extends AbstractJSTypeAdapter<NekoId> {

    @Override
    public Class<NekoId> getTargetClass() {
        return NekoId.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                AdapterInputShape.self(),
                AdapterInputShape.string()
        );
    }

    @Override
    protected NekoId fromString(String str) {
        if (str == null) {
            throw new ValueConversionException(NekoId.class, "namespace:path", null,
                    "[NEKO-4056] Resource ID cannot be null");
        }
        String candidate = str.startsWith("#") ? str.substring(1) : str;
        try {
            return NekoId.resource(candidate);
        } catch (IllegalArgumentException e) {
            throw new ValueConversionException(NekoId.class, "namespace:path", str, e.getMessage(), e);
        }
    }

    @Override
    protected NekoId fromHostObject(Object host) {
        if (host instanceof NekoId id) {
            return id;
        }
        return ManagedApiValueAccess.unwrap(host, NekoId.class);
    }

    @Override
    protected boolean acceptOther(Value value) {
        return value.isProxyObject()
                && ManagedApiValueAccess.is(value.asProxyObject(), NekoId.class);
    }

    @Override
    protected NekoId fromOther(Value value) {
        if (value.isProxyObject()) {
            NekoId id = ManagedApiValueAccess.unwrap(value.asProxyObject(), NekoId.class);
            if (id != null) {
                return id;
            }
        }
        throw new ValueConversionException(NekoId.class, "namespace:path", value,
                "unsupported input shape for NekoId");
    }
}
