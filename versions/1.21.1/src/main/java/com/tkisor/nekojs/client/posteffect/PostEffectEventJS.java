// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
// 脚本面的 @Doc/@Param 文案两侧本就不同，探针类型 golden 会校验，别照抄主干的措辞。
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import com.tkisor.nekojs.api.annotation.ScriptInput;
import java.util.function.Consumer;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.posteffect.PostEffectCandidatePlan;
import com.tkisor.nekojs.core.posteffect.PostEffectChainJson;
import com.tkisor.nekojs.core.posteffect.PostEffectDeclaration;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Payload of the {@code ClientEvents.postEffects} declaration event (ticket 28): the
 * declaration half of the {@code PostEffects} feature, separated from the runtime binding
 * ({@code PostEffects.set/clear/toggle/current}).
 *
 * <p>The event only <b>collects</b> declarations into an inert candidate plan — it never
 * mounts a production listener or touches the current client frame. The platform Adapter
 * ({@link PostEffectDomainOwner}) commits the collected generation at the reload commit
 * point; a failed or interrupted candidate keeps the previous active generation.
 *
 * <pre>
 * // client_scripts
 * ClientEvents.postEffects(event => {
 *   event.register('nekojs:gray', { chainJson: '...' })
 *   event.unregister('nekojs:old_effect')
 * })
 * </pre>
 */
@Doc("Post-effect declaration event: register/unregister runtime post-effect chains for the next client generation.")
public final class PostEffectEventJS implements ProxyObject {

    private final PostEffectCandidatePlan plan;

    /** @param plan collection target created by {@link PostEffectDomainOwner}. */
    public PostEffectEventJS(PostEffectCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Declares a post-effect chain from options (same option shape as the previous
     * {@code PostEffects.register} binding): {@code chainJson} — full 1.21.1
     * {@code shaders/post} chain JSON; {@code program} — vanilla shader program id, wrapped
     * into a simple blit chain.
     *
     * <p>An invalid payload records a collection error: the whole batch fails at preflight
     * and the previous active generation keeps serving.
     */
    @Doc("Declares a post-effect chain from options { chainJson?: string, program?: string }.")
    @Param(name = "id", value = "effect id, e.g. 'nekojs:my_invert'")
    @Param(name = "options", value = "{ chainJson?: string, program?: string }")
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
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
        if (effectId == null) {
            plan.fail("[NEKO-4060] Invalid effect id: " + id);
            return false;
        }

        String chainJson = opts.get("chainJson") instanceof String s ? s : null;
        if (chainJson == null) {
            String program = opts.get("program") instanceof String p ? p : null;
            if (program == null) {
                plan.fail("[NEKO-4060] Post-effect declaration for " + effectId + " needs chainJson or program");
                return false;
            }
            if (ResourceLocation.tryParse(program) == null) {
                throw new IllegalArgumentException("[NEKO-4060] Invalid shader program id: " + program);
            }
            chainJson = PostEffectChainJson.simpleBlitChainLegacy(program);
        }

        plan.install(PostEffectDeclaration.install(effectId.toString(), chainJson, Map.of(), Map.of()));
        return true;
    }

    /**
     * Removes a definition declared earlier in the same batch (or by the previous
     * generation): the id is retired when the batch commits.
     */
    @Doc("Retires a post-effect declaration: the id is released when this batch commits.")
    @Param(name = "id", value = "effect id")
    @Return("true when the declaration was collected; false when the id is invalid")
    public boolean unregister(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
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
}
