package com.tkisor.nekujs.bridge;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import dev.latvian.mods.kubejs.util.TickDuration;
import graal.graalvm.polyglot.Value;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Routes native host arguments through the shared legacy tick conversion. */
public final class LegacyTickDurationAdapter implements JSTypeAdapter<TickDuration> {
    @Override public Class<TickDuration> getTargetClass() { return TickDuration.class; }

    @Override public ConversionPrecedence getPrecedence() { return ConversionPrecedence.HIGH; }

    @Override
    public boolean test(Value value) {
        return value == null || value.isNull() || value.isString() || value.isNumber()
                || value.isHostObject() && (value.asHostObject() instanceof TickDuration
                || value.asHostObject() instanceof Duration);
    }

    @Override
    public TickDuration apply(Value value) {
        if (value == null || value.isNull()) return TickDuration.wrap(null, null);
        if (value.isHostObject()) return TickDuration.wrap(null, value.asHostObject());
        if (value.isString()) return TickDuration.wrap(null, value.asString());
        if (value.isNumber()) {
            if (value.fitsInLong()) return TickDuration.wrap(null, value.asLong());
            return TickDuration.wrap(null, value.asDouble());
        }
        return TickDuration.wrap(null, value);
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(AdapterInputShape.self(), AdapterInputShape.number(), AdapterInputShape.string(),
                AdapterInputShape.host(Duration.class), AdapterInputShape.raw("null"));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("Numbers are ticks (20 per second); strings combine decimal amounts with t, ns, ms, s, m, h, d, w, M, or y units. Whitespace is optional. Null is zero.");
    }
}
