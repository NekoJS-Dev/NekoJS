// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
// 脚本面的 @Doc/@Param 文案两侧本就不同，探针类型 golden 会校验，别照抄主干的措辞。
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
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
public final class PostEffectEventJS {

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
    public boolean register(String id, Map<String, Object> options) {
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
        if (effectId == null) {
            plan.fail("invalid effect id: " + id);
            return false;
        }
        Map<String, Object> opts = options == null ? Map.of() : options;

        String chainJson = opts.get("chainJson") instanceof String s ? s : null;
        if (chainJson == null) {
            String program = opts.get("program") instanceof String p ? p : null;
            if (program == null) {
                plan.fail("post-effect declaration for " + effectId + " needs chainJson or program");
                return false;
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
}
