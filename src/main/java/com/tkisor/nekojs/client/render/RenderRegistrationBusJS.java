package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.ScriptOptionSchema;
import com.tkisor.nekojs.api.catalog.EventCallSignature;
import com.tkisor.nekojs.api.catalog.EventCallSignature.Parameter;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Id-based persistent render registrations, not ordinary one-argument event subscriptions. */
public class RenderRegistrationBusJS extends EventBusJS<Object, Void> {
    private enum Mode { HUD, WORLD }
    private final Mode mode;
    private final ScriptOptionSchema options;

    private RenderRegistrationBusJS(Mode mode) {
        super(EventBus.create(Object.class));
        this.mode = mode;
        Enum<?>[] layers = mode == Mode.HUD ? ClientRenderRegistry.HudLayer.values() : ClientRenderRegistry.WorldLayer.values();
        options = new ScriptOptionSchema(
                ScriptOptionSchema.choice("layer", Arrays.stream(layers)
                        .map(layer -> layer.name().toLowerCase(Locale.ROOT)).toArray(String[]::new)),
                ScriptOptionSchema.integer("priority", Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    public static RenderRegistrationBusJS hud() { return new RenderRegistrationBusJS(Mode.HUD); }
    public static RenderRegistrationBusJS world() { return new RenderRegistrationBusJS(Mode.WORLD); }

    /** The same schema supplies runtime validation and the catalog's callable declarations. */
    @Override
    public List<EventCallSignature> callSignatures() {
        List<Parameter> callback = mode == Mode.HUD
                ? List.of(new Parameter("ctx", AdapterInputShape.host(HudRenderContextJS.class)),
                        new Parameter("graphics", AdapterInputShape.host(hudGraphicsType())))
                : List.of(new Parameter("ctx", AdapterInputShape.host(WorldRenderContextJS.class)));
        Parameter id = new Parameter("id", AdapterInputShape.string());
        return List.of(new EventCallSignature(List.of(id), callback, AdapterInputShape.bool()),
                new EventCallSignature(List.of(id, new Parameter("options", AdapterInputShape.union(
                        options.inputShape(), options.fieldShape("layer"), options.fieldShape("priority")))),
                        callback, AdapterInputShape.bool()));
    }

    private static Class<?> hudGraphicsType() {
        try {
            // The version-specific context owns the raw graphics type; shared registration stays portable.
            return HudRenderContextJS.class.getMethod("graphics").getReturnType();
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException("[NEKO-4062] HUD context must expose its graphics type", missing);
        }
    }

    @Override
    public Object execute(Value... args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("[NEKO-4062] Render registration requires (id, callback) or (id, options, callback)");
        }
        Value idValue = args[0];
        Value callback = args[args.length - 1];
        if (!idValue.isString() || idValue.asString().isBlank()) {
            throw new IllegalArgumentException("[NEKO-4062] Renderer id must be a nonblank string");
        }
        if (!callback.canExecute()) throw new IllegalArgumentException("[NEKO-4062] Renderer requires a callback as the last argument");

        Map<String, Object> parsed = Map.of();
        if (args.length == 3) {
            Value input = args[1];
            parsed = input.isString() ? Map.of("layer", options.readField("layer", input))
                    : input.isNumber() ? Map.of("priority", options.readField("priority", input)) : options.read(input);
        }
        String layerName = (String) parsed.getOrDefault("layer", "normal");
        int layer = mode == Mode.HUD ? ClientRenderRegistry.HudLayer.valueOf(layerName.toUpperCase(Locale.ROOT)).ordinal()
                : ClientRenderRegistry.WorldLayer.valueOf(layerName.toUpperCase(Locale.ROOT)).ordinal();
        int priority = (Integer) parsed.getOrDefault("priority", 0);
        Context context = callback.getContext();
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        if (mode == Mode.HUD) {
            ClientRenderRegistry.registerHud(idValue.asString(), scriptId, context,
                    ClientRenderRegistry.HudLayer.values()[layer], priority, callback);
        } else {
            ClientRenderRegistry.registerWorld(idValue.asString(), scriptId, context,
                    ClientRenderRegistry.WorldLayer.values()[layer], priority, callback);
        }
        return true;
    }
}
