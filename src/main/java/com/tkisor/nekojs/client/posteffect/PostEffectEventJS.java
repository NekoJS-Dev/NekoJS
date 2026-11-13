// Fabric has no equivalent implementation yet; remove the whole-file guard once the port is
// done.
//? if neoforge {
// 26.x implementation, so this file must not contain version guards. The 1.21.1 implementation
// is the same-named file under versions/1.21.1/src; changing this file's behavior means
// updating that one too.
// The @Doc/@Param text on the script side already differs between the two trees and the
// probe-type golden checks it, so do not copy it into the twin file.
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import com.tkisor.nekojs.api.annotation.ScriptInput;
import com.tkisor.nekojs.api.annotation.ScriptInternalConstructor;
import java.util.function.Consumer;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.posteffect.PostEffectCandidatePlan;
import com.tkisor.nekojs.core.posteffect.PostEffectChainJson;
import com.tkisor.nekojs.core.posteffect.PostEffectDeclaration;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payload of the {@code ClientEvents.postEffects} declaration event: the declaration half of
 * the {@code PostEffects} feature, separate from the runtime binding
 * ({@code PostEffects.set/clear/toggle/current}).
 *
 * <p>The event only <b>collects</b> declarations into an inert candidate plan. It never mounts
 * a production listener, activates a post chain or touches the current client frame. The
 * platform adapter ({@link PostEffectDomainOwner}) commits the collected generation at the
 * reload commit point; a failed or interrupted candidate keeps the previous active
 * generation.
 *
 * <pre>
 * // client_scripts
 * ClientEvents.postEffects(event => {
 *   event.register('nekojs:gray', {
 *     fragmentShader: '... GLSL ...',
 *     uniformsJson: '{"Intensity": 0.5}'
 *   })
 *   event.unregister('nekojs:old_effect')
 * })
 * </pre>
 */
@Doc("Post-effect declaration event: register/unregister runtime post-effect chains for the next client generation.")
public final class PostEffectEventJS implements ProxyObject {

    private final PostEffectCandidatePlan plan;

    /** @param plan collection target created by {@link PostEffectDomainOwner}. */
    @ScriptInternalConstructor
    public PostEffectEventJS(PostEffectCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Declares a post-effect chain from options (same option shape as the previous
     * {@code PostEffects.register} binding):
     * <ul>
     *   <li>{@code chainJson} — full 26.x post-chain JSON
     *       ({@code assets/<ns>/post_effect/<path>.json} format);</li>
     *   <li>{@code fragmentShader} — GLSL source; a two-pass blit chain
     *       ({@code main -> swap -> main}) is generated around it;</li>
     *   <li>{@code fragmentShaderId} — shader id for the generated chain (default {@code <ns>:post/<path>});</li>
     *   <li>{@code fragmentShaders} / {@code vertexShaders} — extra GLSL source tables;</li>
     *   <li>{@code uniformsJson} — JSON object literal merged into pass-1 uniforms;</li>
     *   <li>{@code blurRadius} / {@code blurRounds} — generate a vanilla box-blur chain instead.</li>
     * </ul>
     * An invalid payload records a collection error: the whole batch fails at preflight and
     * the previous active generation keeps serving (no partial declaration).
     */
    @Doc("Declares a post-effect chain from options { chainJson?, fragmentShader?, fragmentShaderId?, fragmentShaders?, vertexShaders?, uniformsJson?, blurRadius?, blurRounds? }.")
    @Param(name = "id", value = "effect id, e.g. 'nekojs:my_invert'")
    @Param(name = "options", value = "{ chainJson?: string, fragmentShader?: string, fragmentShaderId?: string, fragmentShaders?: object, vertexShaders?: object, uniformsJson?: string, blurRadius?: number, blurRounds?: number }")
    @Return("true when the declaration was collected; false when the payload is invalid (fails the whole batch)")
    public boolean register(String id, @ScriptInput(PostEffectOptions.class) Map<String, Object> options) {
        try {
            return collect(id, PostEffectOptions.SCHEMA.validate(options));
        } catch (RuntimeException invalid) {
            plan.fail("[NEKO-4060] Invalid post-effect declaration for " + id + ": " + invalid.getMessage());
            return false;
        }
    }

    /** Collects a callback-last declaration; captured builders are sealed when the callback exits. */
    @Doc("Declares an inert post-effect chain using callback-scoped builder properties.")
    @Param(name = "id", value = "effect id")
    @Param(name = "builder", value = "declaration configuration; invoked exactly once before collection")
    @Return("true when collected; false when invalid (fails the whole candidate)")
    public boolean register(String id, Consumer<PostEffectBuilder> builder) {
        PostEffectBuilder options = new PostEffectBuilder();
        try {
            if (builder == null) throw new IllegalArgumentException("[NEKO-4061] Post-effect builder callback is required");
            builder.accept(options);
            return collect(id, options.finish());
        } catch (RuntimeException invalid) {
            plan.fail("[NEKO-4061] Invalid post-effect builder for " + id + ": " + invalid.getMessage());
            return false;
        } finally {
            options.seal();
        }
    }

    /** Routes guest functions before object conversion; Java overload selection otherwise treats them as maps. */
    private Object invokeGuest(String method, Value[] arguments) {
        try {
            int expected = switch (method) {
                case "register" -> 2;
                case "unregister" -> 1;
                default -> 0;
            };
            if (arguments.length != expected) {
                throw new IllegalArgumentException("[NEKO-4061] Invalid argument count for post-effect " + method);
            }
            if (method.equals("getDeclaredCount")) return getDeclaredCount();
            if (!arguments[0].isString()) {
                throw new IllegalArgumentException("[NEKO-4061] Post-effect id must be a string");
            }
            String id = arguments[0].asString();
            if (method.equals("unregister")) return unregister(id);
            Value input = arguments[1];
            if (input.canExecute()) return register(id, builder -> input.executeVoid(builder));
            return register(id, PostEffectOptions.SCHEMA.read(input));
        } catch (RuntimeException invalid) {
            plan.fail("[NEKO-4061] Invalid post-effect " + method + " invocation: " + invalid.getMessage());
            return false;
        }
    }

    @HideFromJS
    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "register", "unregister", "getDeclaredCount" ->
                    (ProxyExecutable) arguments -> invokeGuest(name, arguments);
            case "declaredCount" -> getDeclaredCount();
            default -> null;
        };
    }

    @HideFromJS
    @Override
    public Object getMemberKeys() { return new String[] {"register", "unregister", "getDeclaredCount", "declaredCount"}; }

    @HideFromJS
    @Override
    public boolean hasMember(String name) {
        return switch (name) {
            case "register", "unregister", "getDeclaredCount", "declaredCount" -> true;
            default -> false;
        };
    }

    @HideFromJS
    @Override
    public void putMember(String name, Value value) {
        String message = "[NEKO-4061] Post-effect event members are read-only: " + name;
        plan.fail(message);
        throw new IllegalArgumentException(message);
    }

    @HideFromJS
    @Override
    public boolean removeMember(String name) {
        String message = "[NEKO-4061] Post-effect event members cannot be deleted: " + name;
        plan.fail(message);
        throw new IllegalArgumentException(message);
    }

    private boolean collect(String id, Map<String, Object> opts) {
        Identifier effectId = Identifier.tryParse(id == null ? "" : id);
        if (effectId == null) {
            plan.fail("[NEKO-4060] Invalid effect id: " + id);
            return false;
        }

        String chainJson = asString(opts.get("chainJson"));
        Map<Identifier, String> fragmentShaders = parseShaderMap(asStringMap(opts.get("fragmentShaders")));
        Map<Identifier, String> vertexShaders = parseShaderMap(asStringMap(opts.get("vertexShaders")));

        String explicitShaderId = asString(opts.get("fragmentShaderId"));
        if (explicitShaderId != null && Identifier.tryParse(explicitShaderId) == null) {
            throw new IllegalArgumentException("[NEKO-4060] Invalid fragment shader id: " + explicitShaderId);
        }
        if (chainJson == null) {
            String fragmentShader = asString(opts.get("fragmentShader"));
            Number blurRadius = asNumber(opts.get("blurRadius"));
            if (fragmentShader != null) {
                String shaderId = asString(opts.get("fragmentShaderId"));
                if (shaderId == null) {
                    shaderId = effectId.getNamespace() + ":post/" + effectId.getPath();
                }
                Identifier parsedShaderId = Identifier.tryParse(shaderId);
                if (parsedShaderId == null) {
                    plan.fail("[NEKO-4060] Invalid fragment shader id for " + effectId + ": " + shaderId);
                    return false;
                }
                fragmentShaders = new LinkedHashMap<>(fragmentShaders);
                fragmentShaders.put(parsedShaderId, fragmentShader);
                chainJson = PostEffectChainJson.simpleBlitChainModern(shaderId, asString(opts.get("uniformsJson")));
            } else if (blurRadius != null) {
                Number rounds = asNumber(opts.get("blurRounds"));
                chainJson = PostEffectChainJson.boxBlurChainModern(
                        blurRadius.doubleValue(), rounds == null ? 1 : rounds.intValue());
            } else {
                plan.fail("[NEKO-4060] Post-effect declaration for " + effectId
                        + " needs chainJson, fragmentShader or blurRadius");
                return false;
            }
        }

        plan.install(PostEffectDeclaration.install(effectId.toString(), chainJson,
                toIdMap(fragmentShaders), toIdMap(vertexShaders)));
        return true;
    }

    /**
     * Removes a definition declared earlier in the same batch (or by the previous
     * generation): the id is retired when the batch commits. Use it when a script stops
     * declaring an effect conditionally.
     */
    @Doc("Retires a post-effect declaration: the id is released when this batch commits.")
    @Param(name = "id", value = "effect id")
    @Return("true when the declaration was collected; false when the id is invalid")
    public boolean unregister(String id) {
        Identifier effectId = Identifier.tryParse(id == null ? "" : id);
        if (effectId == null) {
            plan.fail("[NEKO-4060] Invalid effect id: " + id);
            return false;
        }
        plan.retireDeclared(effectId.toString());
        return true;
    }

    /** Number of install declarations collected by this event so far. */
    @Doc("Number of post-effect declarations collected by this event so far.")
    @Return("declaration count")
    public int getDeclaredCount() {
        return plan.installCount();
    }

    @Nullable
    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    @Nullable
    private static Number asNumber(Object value) {
        return value instanceof Number n ? n : null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, String> asStringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                    result.put(k, v);
                }
            }
            return result;
        }
        return null;
    }

    private static Map<Identifier, String> parseShaderMap(@Nullable Map<String, String> sources) {
        Map<Identifier, String> result = new LinkedHashMap<>();
        if (sources != null) sources.forEach((id, source) -> {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) throw new IllegalArgumentException("[NEKO-4060] Invalid shader id: " + id);
            result.put(parsed, source);
        });
        return result;
    }

    private static Map<String, String> toIdMap(Map<Identifier, String> sources) {
        Map<String, String> result = new LinkedHashMap<>();
        sources.forEach((id, source) -> result.put(id.toString(), source));
        return result;
    }
}
//?}
