// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
// 脚本面的 @Doc/@Param 文案两侧本就不同，探针类型 golden 会校验，别照抄到孪生文件。
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
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
 * Payload of the {@code ClientEvents.postEffects} declaration event (ticket 28): the
 * declaration half of the {@code PostEffects} feature, separated from the runtime binding
 * ({@code PostEffects.set/clear/toggle/current}).
 *
 * <p>The event only <b>collects</b> declarations into an inert candidate plan — it never
 * mounts a production listener, activates a post chain or touches the current client frame.
 * The platform Adapter ({@link PostEffectDomainOwner}) commits the collected generation at
 * the reload commit point; a failed or interrupted candidate keeps the previous active
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
public final class PostEffectEventJS {

    private final PostEffectCandidatePlan plan;

    /** @param plan collection target created by {@link PostEffectDomainOwner}. */
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
    public boolean register(String id, Map<String, Object> options) {
        Identifier effectId = Identifier.tryParse(id == null ? "" : id);
        if (effectId == null) {
            plan.fail("invalid effect id: " + id);
            return false;
        }
        Map<String, Object> opts = options == null ? Map.of() : options;

        String chainJson = asString(opts.get("chainJson"));
        Map<Identifier, String> fragmentShaders = PostEffectManager.parseShaderMap(asStringMap(opts.get("fragmentShaders")));
        Map<Identifier, String> vertexShaders = PostEffectManager.parseShaderMap(asStringMap(opts.get("vertexShaders")));

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
                    plan.fail("invalid fragment shader id for " + effectId + ": " + shaderId);
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
                plan.fail("post-effect declaration for " + effectId
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
            plan.fail("invalid effect id: " + id);
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

    private static Map<String, String> toIdMap(Map<Identifier, String> sources) {
        Map<String, String> result = new LinkedHashMap<>();
        sources.forEach((id, source) -> result.put(id.toString(), source));
        return result;
    }
}
//?}
