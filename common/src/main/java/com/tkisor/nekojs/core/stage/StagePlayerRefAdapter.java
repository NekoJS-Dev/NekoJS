package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.api.event.StablePlayerAdvancementEvent;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.api.event.StablePlayerDimensionChangeEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StablePlayerItemEvent;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.core.api.ManagedApiValueAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Type adapter converting player UUID strings, portable {@code StablePlayer*Event} payloads,
 * {@link StagePlayerRef} instances, and platform-native {@code Player} host objects into
 * {@link StagePlayerRef}.
 *
 * <p>Platform modules register their loader-specific {@code Player -> StagePlayerRef} extractor
 * via {@link #registerHostResolver(Function)} during initialization so {@code common} stays free
 * of Minecraft imports.
 */
public final class StagePlayerRefAdapter extends AbstractJSTypeAdapter<StagePlayerRef> {

    private static final List<Function<Object, StagePlayerRef>> HOST_RESOLVERS =
            new CopyOnWriteArrayList<>();

    /**
     * Registers a platform host resolver converting native player instances (for example
     * {@code ServerPlayer}, {@code LocalPlayer}, or {@code FakePlayer}) into {@link StagePlayerRef}.
     * Returning {@code null} indicates that the object is not handled by that resolver.
     */
    public static void registerHostResolver(Function<Object, StagePlayerRef> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        HOST_RESOLVERS.add(resolver);
    }

    @Override
    public Class<StagePlayerRef> getTargetClass() {
        return StagePlayerRef.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                AdapterInputShape.self(),
                AdapterInputShape.string(),
                AdapterInputShape.host(StablePlayerEvent.class),
                AdapterInputShape.host(StablePlayerItemEvent.class),
                AdapterInputShape.host(StablePlayerChatEvent.class),
                AdapterInputShape.host(StablePlayerAdvancementEvent.class),
                AdapterInputShape.host(StablePlayerDimensionChangeEvent.class)
        );
    }

    @Override
    protected StagePlayerRef fromString(String s) {
        if (s == null || s.isBlank()) {
            throw new ValueConversionException(
                    StagePlayerRef.class, "player | uuid", s, "[NEKO-4070] Player UUID must not be blank");
        }
        try {
            return StagePlayerRef.ofUuid(s.trim());
        } catch (IllegalArgumentException e) {
            throw new ValueConversionException(StagePlayerRef.class, "player | uuid", s, e.getMessage(), e);
        }
    }

    @Override
    protected StagePlayerRef fromHostObject(Object host) {
        if (host instanceof StagePlayerRef ref) {
            return ref;
        }
        StagePlayerRef unwrapped = ManagedApiValueAccess.unwrap(host, StagePlayerRef.class);
        if (unwrapped != null) {
            return unwrapped;
        }
        if (host instanceof StablePlayerEvent event) {
            return fromEventFields(host, event.getPlayerUuid(), event.getPlayerName(), event.clientSide());
        }
        if (host instanceof StablePlayerItemEvent event) {
            return fromEventFields(host, event.getPlayerUuid(), event.getPlayerName());
        }
        if (host instanceof StablePlayerChatEvent event) {
            return fromEventFields(host, event.getPlayerUuid(), event.getPlayerName());
        }
        if (host instanceof StablePlayerAdvancementEvent event) {
            return fromEventFields(host, event.getPlayerUuid(), event.getPlayerName());
        }
        if (host instanceof StablePlayerDimensionChangeEvent event) {
            return fromEventFields(host, event.getPlayerUuid(), event.getPlayerName());
        }
        for (Function<Object, StagePlayerRef> resolver : HOST_RESOLVERS) {
            try {
                StagePlayerRef resolved = resolver.apply(host);
                if (resolved != null) {
                    return resolved;
                }
            } catch (IllegalArgumentException e) {
                throw new ValueConversionException(
                        StagePlayerRef.class, "player | uuid", host, e.getMessage(), e);
            }
        }
        return null;
    }

    @Override
    protected boolean acceptOther(Value value) {
        return value.isProxyObject()
                && ManagedApiValueAccess.is(value.asProxyObject(), StagePlayerRef.class);
    }

    @Override
    protected StagePlayerRef fromOther(Value value) {
        if (value.isProxyObject()) {
            StagePlayerRef ref = ManagedApiValueAccess.unwrap(value.asProxyObject(), StagePlayerRef.class);
            if (ref != null) {
                return ref;
            }
        }
        throw new ValueConversionException(
                StagePlayerRef.class, "player | uuid", value, "unsupported input shape for StagePlayerRef");
    }

    private static StagePlayerRef fromEventFields(Object source, String uuid, String name) {
        return fromEventFields(source, uuid, name, false);
    }

    private static StagePlayerRef fromEventFields(Object source, String uuid, String name, boolean clientSide) {
        if (uuid == null || uuid.isBlank()) {
            throw new ValueConversionException(
                    StagePlayerRef.class,
                    "player event with non-blank playerUuid",
                    source,
                    "[NEKO-4070] Player event does not carry a valid playerUuid");
        }
        try {
            return clientSide ? StagePlayerRef.client(uuid, name) : StagePlayerRef.of(uuid, name);
        } catch (IllegalArgumentException e) {
            throw new ValueConversionException(StagePlayerRef.class, "player | uuid", source, e.getMessage(), e);
        }
    }
}
