// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
// 脚本面的 @Doc/@Param 文案两侧本就不同，探针类型 golden 会校验，别照抄主干的措辞。
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.Binding;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime post-effect binding ({@code PostEffects}, feature 8b). Client scripts only.
 *
 * <p><b>Runtime binding face (ticket 28).</b> This binding keeps exactly the runtime
 * operations: {@link #set}/{@link #clear}/{@link #toggle}/{@link #current} (plus
 * {@link #isActive}) work with any effect that exists as a resource — the vanilla presets
 * {@code minecraft:invert}, {@code minecraft:spider}, {@code minecraft:creeper},
 * {@code minecraft:blur}, {@code minecraft:entity_outline}, {@code minecraft:transparency}
 * (see {@link #PRESETS}) and any {@code shaders/post} chain JSON supplied by resource packs
 * or other mods. These members are <b>not</b> reload transaction operations.
 *
 * <p><b>Declaration face.</b> Runtime chains are declared through
 * {@code ClientEvents.postEffects} ({@code event.register(id, options)} /
 * {@code event.unregister(id)}) — the declaration lifecycle is generation-scoped, lives in
 * an inert candidate plan and applies only at the commit point. Use
 * {@link #activeGeneration()} / {@link #hasDefinition} / {@link #installed()} for the
 * read-only declaration query; the previous direct {@code PostEffects.register} entry point
 * is gone (see the ticket 28 migration table).
 *
 * <pre>
 * // client_scripts
 * ClientEvents.postEffects(event => {
 *   event.register('nekojs:gray', { program: 'minecraft:invert' })
 * })
 * </pre>
 */
@Doc("Client-side post effects: runtime set/clear/toggle/current plus read-only queries for effects declared through ClientEvents.postEffects.")
public final class PostEffectsJS implements Binding {

    /** Vanilla preset ids usable with {@link #set} on every supported version. */
    @Doc("Vanilla preset effect ids usable with set/toggle: minecraft:invert, minecraft:spider, minecraft:creeper, minecraft:blur, minecraft:entity_outline, minecraft:transparency.")
    public static final List<String> PRESETS = List.of(
            "minecraft:invert",
            "minecraft:spider",
            "minecraft:creeper",
            "minecraft:blur",
            "minecraft:entity_outline",
            "minecraft:transparency"
    );

    @Override
    public String name() {
        return "PostEffects";
    }

    @Override
    public Object value() {
        return this;
    }

    /**
     * Script reload teardown. Declared generations are owned by the reload lifecycle
     * (candidate plans and the commit point), so the runtime binding only drops the active
     * post effect here rather than clearing a declaration registry.
     */
    @Override
    public void close(ScriptType scriptType) {
        if (scriptType == ScriptType.CLIENT) {
            PostEffectManager.clear();
        }
    }

    /** Activates the effect on the client thread. Declaration-only ids warn and return false. */
    @Doc("Activates a post effect (e.g. 'minecraft:invert'); executed on the client thread.")
    @Param(name = "id", value = "effect id; must exist as a shaders/post chain resource")
    @Return("true when accepted")
    public boolean set(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
        return effectId != null && PostEffectManager.set(effectId);
    }

    /** Deactivates the active post effect. */
    @Doc("Clears the active post effect.")
    @Return("true")
    public boolean clear() {
        return PostEffectManager.clear();
    }

    /** Toggles: activates {@code id} when idle or different, clears when {@code id} is active. */
    @Doc("Toggles a post effect: set when idle/different, clear when it is already active.")
    @Param(name = "id", value = "effect id")
    @Return("true when accepted")
    public boolean toggle(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
        return effectId != null && PostEffectManager.toggle(effectId);
    }

    /** Currently active effect id, or null. */
    @Doc("Returns the id last activated through this binding, or null when none is active.")
    @Return("effect id string or null")
    @Nullable
    public String current() {
        ResourceLocation current = PostEffectManager.current();
        return current == null ? null : current.toString();
    }

    /** Whether an effect is currently applied (reads GameRenderer#effectActive reflectively). */
    @Doc("Returns whether a post effect is currently applied.")
    @Return("true when GameRenderer.effectActive is set")
    public boolean isActive() {
        return PostEffectManager.isActive();
    }

    /**
     * Whether the <b>active declaration generation</b> installed a definition for the id.
     * False for a retired or never-declared id, so callers can tell a live declaration from
     * a stale one instead of reading an old runtime registration.
     */
    @Doc("Returns whether the active declaration generation installed a definition for the id.")
    @Param(name = "id", value = "effect id")
    @Return("true when declared through ClientEvents.postEffects by the active generation")
    public boolean hasDefinition(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
        return effectId != null && PostEffectManager.hasDefinition(effectId);
    }

    /** Effect ids installed by the active declaration generation (read-only snapshot). */
    @Doc("Lists the effect ids installed by the active declaration generation.")
    @Return("effect id list")
    public List<String> installed() {
        List<String> ids = new ArrayList<>();
        PostEffectManager.installedDefinitions().keySet().forEach(id -> ids.add(id.toString()));
        ids.sort(String::compareTo);
        return ids;
    }

    /**
     * Client script generation that installed the active declarations ({@code -1} when no
     * declaration generation has committed yet). Together with {@link #hasDefinition} this is
     * the generation/stale query face: a caller that captured an id from an older generation
     * sees the new number instead of silently reading stale state.
     */
    @Doc("Returns the client generation that installed the active post-effect declarations (-1 when none).")
    @Return("generation number")
    public long activeGeneration() {
        return PostEffectManager.activeGeneration();
    }

    /** Whether the effect exists as a shaders/post chain resource (activatable in v1). */
    @Doc("Returns whether the effect exists as a shaders/post chain resource in the active resource packs (activatable without mixins).")
    @Param(name = "id", value = "effect id")
    @Return("true when resource-backed")
    public boolean isAvailable(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id == null ? "" : id);
        return effectId != null && PostEffectManager.isResourceAvailable(effectId);
    }

    /** Vanilla preset ids usable with set/toggle. */
    @Doc("Lists vanilla preset ids usable with set/toggle.")
    @Return("preset id list")
    public List<String> presets() {
        return new ArrayList<>(PRESETS);
    }
}
