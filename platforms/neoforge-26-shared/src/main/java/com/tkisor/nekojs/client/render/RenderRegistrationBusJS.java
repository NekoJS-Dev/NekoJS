package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.eventbus.EventBusFactory;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.Locale;

/**
 * {@code ClientEvents.hudRender}/{@code worldRender} 的注册入口：挂在事件组上的
 * {@link EventBusJS} 子类，但语义是「按 id 注册常驻渲染器」而非注册一次性监听器，
 * 因此覆写 {@link #execute} 走 {@link ClientRenderRegistry}，底层 bus 仅作占位。
 *
 * <p>脚本调用形态：
 * <ul>
 *   <li>{@code bus('my_hud', callback)}</li>
 *   <li>{@code bus('my_hud', { layer: 'background', priority: -100 }, callback)}</li>
 *   <li>options 也接受裸层名（{@code 'background'}）或裸优先级数字</li>
 * </ul>
 * HUD 层名 {@code background|normal|foreground}（默认 normal，数值越小越先绘制，
 * background 在原版 HUD 之下）；世界层名 {@code early|normal|late}（默认 normal）。
 */
public class RenderRegistrationBusJS extends EventBusJS<Object, Void> {

    private enum Mode {
        HUD,
        WORLD
    }

    private final Mode mode;

    private RenderRegistrationBusJS(Mode mode) {
        // 底层 bus 仅为满足 EventBusJS 构造约束：本总线从不 post，只覆写 execute
        super(EventBusFactory.createEventBus(Object.class));
        this.mode = mode;
    }

    /** HUD 渲染器注册总线（{@code ClientEvents.hudRender}）。 */
    public static RenderRegistrationBusJS hud() {
        return new RenderRegistrationBusJS(Mode.HUD);
    }

    /** 世界渲染器注册总线（{@code ClientEvents.worldRender}）。 */
    public static RenderRegistrationBusJS world() {
        return new RenderRegistrationBusJS(Mode.WORLD);
    }

    @Override
    public Object execute(Value... args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    mode == Mode.HUD
                            ? "hudRender requires (id, callback) or (id, options, callback)"
                            : "worldRender requires (id, callback) or (id, options, callback)");
        }
        Value idValue = args[0];
        Value callback = args[args.length - 1];
        if (!idValue.isString()) {
            throw new IllegalArgumentException("renderer id must be a string");
        }
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("renderer requires a callback function as the last argument");
        }
        String id = idValue.asString();
        if (id.isBlank()) {
            throw new IllegalArgumentException("renderer id must not be blank");
        }

        int layer = defaultLayer();
        int priority = 0;
        if (args.length == 3) {
            Value options = args[1];
            if (options.isString()) {
                layer = parseLayer(options.asString());
            } else if (options.isNumber()) {
                priority = (int) options.asDouble();
            } else if (options.hasMembers()) {
                if (options.hasMember("layer")) {
                    Value layerValue = options.getMember("layer");
                    if (layerValue.isString()) {
                        layer = parseLayer(layerValue.asString());
                    }
                }
                if (options.hasMember("priority") && options.getMember("priority").isNumber()) {
                    priority = (int) options.getMember("priority").asDouble();
                }
            } else {
                throw new IllegalArgumentException(
                        "renderer options must be an object { layer, priority }, a layer name or a priority number");
            }
        }

        Context context = callback.getContext();
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        if (mode == Mode.HUD) {
            ClientRenderRegistry.registerHud(
                    id, scriptId, context,
                    ClientRenderRegistry.HudLayer.values()[layer], priority, callback);
        } else {
            ClientRenderRegistry.registerWorld(
                    id, scriptId, context,
                    ClientRenderRegistry.WorldLayer.values()[layer], priority, callback);
        }
        return true;
    }

    private int defaultLayer() {
        return mode == Mode.HUD
                ? ClientRenderRegistry.HudLayer.NORMAL.ordinal()
                : ClientRenderRegistry.WorldLayer.NORMAL.ordinal();
    }

    private int parseLayer(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (mode == Mode.HUD) {
            return switch (normalized) {
                case "background" -> ClientRenderRegistry.HudLayer.BACKGROUND.ordinal();
                case "normal" -> ClientRenderRegistry.HudLayer.NORMAL.ordinal();
                case "foreground" -> ClientRenderRegistry.HudLayer.FOREGROUND.ordinal();
                default -> throw new IllegalArgumentException(
                        "unknown hud layer '" + name + "' (expected background|normal|foreground)");
            };
        }
        return switch (normalized) {
            case "early" -> ClientRenderRegistry.WorldLayer.EARLY.ordinal();
            case "normal" -> ClientRenderRegistry.WorldLayer.NORMAL.ordinal();
            case "late" -> ClientRenderRegistry.WorldLayer.LATE.ordinal();
            default -> throw new IllegalArgumentException(
                    "unknown world layer '" + name + "' (expected early|normal|late)");
        };
    }
}
