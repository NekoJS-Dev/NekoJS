// 1.21.1 节点专有变体（DEVEX-ROADMAP 档 1 整文件拆分案例）：主干 PostEffectsJS 已 26.x
// 基准化；本文件为 1.21.1 的完整实现——脚本面 @Doc/@Param 文案存在时代差（探针类型
// golden 会校验），行为 = 主干在 1.21.1 节点的历史求值产物（构造性变换）。
// 主干脚本面变更时须同步本文件。
// TODO(loader-port): deferred to the LoaderBridge fabric port
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.core.posteffect.PostEffectChainJson;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-side post-effect binding ({@code PostEffects}, feature 8b). Client scripts only.
 *
 * <p><b>v1 (this batch, no mixins):</b> {@link #set}/{@link #clear}/{@link #toggle} work with
 * any effect that exists as a resource — the vanilla presets {@code minecraft:invert},
 * {@code minecraft:spider}, {@code minecraft:creeper}, {@code minecraft:blur},
 * {@code minecraft:entity_outline}, {@code minecraft:transparency} (see {@link #PRESETS}) and
 * any {@code shaders/post} chain JSON supplied by resource packs or other mods. {@link #register}
 * validates and stores runtime chain JSON; activating runtime-only ids additionally requires
 * a pending 1.21.1 post-chain mixin and logs a warning until then.
 *
 * <pre>
 * // client_scripts
 * if (PostEffects.isAvailable('minecraft:invert')) {
 *   PostEffects.set('minecraft:invert');
 *   // ... later ...
 *   PostEffects.clear();
 * }
 * </pre>
 */
@Doc("Client-side full-screen post effects: set/clear/toggle resource-backed effects and register runtime chains.")
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

    /** Script reload teardown: forget runtime-registered definitions (matching binding close semantics). */
    @Override
    public void close(ScriptType scriptType) {
        if (scriptType == ScriptType.CLIENT) {
            PostEffectManager.clearRegistered();
        }
    }

    /**
     * Registers a runtime post-effect chain. Without a pending 1.21.1 post-chain mixin
     * the definition is validated and stored but cannot be activated via {@link #set};
     * effects that exist as resources stay activatable.
     *
     * <p>Options (all optional): {@code chainJson} — full shaders/post chain JSON;
     * {@code program} — vanilla shader program id, wrapped into a simple blit chain.
     */
    @Doc("Registers a runtime post-effect chain from options { chainJson?: string, program?: string }; returns true when the chain JSON is well-formed.")
    @Param(name = "id", value = "effect id, e.g. 'nekojs:my_invert'")
    @Param(name = "options", value = "{ chainJson?: string, program?: string }")
    @Return("true when registered (runtime-only ids need the pending 1.21.1 post-chain mixin to activate)")
    public boolean register(String id, Map<String, Object> options) {
        ResourceLocation effectId = ResourceLocation.tryParse(id);
        if (effectId == null) return false;
        Map<String, Object> opts = options == null ? Map.of() : options;

        String chainJson = opts.get("chainJson") instanceof String s ? s : null;

        if (chainJson == null) {
            String program = opts.get("program") instanceof String p ? p : null;
            if (program == null) return false;
            chainJson = PostEffectChainJson.simpleBlitChainLegacy(program);
        }

        return PostEffectManager.register(effectId, chainJson);
    }

    /** Removes a runtime-registered definition; clears it first when active. */
    @Doc("Unregisters a runtime post-effect definition, clearing it first when it is active.")
    @Param(name = "id", value = "effect id")
    @Return("true when a definition was removed")
    public boolean unregister(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id);
        return effectId != null && PostEffectManager.unregister(effectId);
    }

    /** Activates the effect on the client thread. Runtime-only ids warn and return false (v1). */
    @Doc("Activates a post effect (e.g. 'minecraft:invert'); executed on the client thread.")
    @Param(name = "id", value = "effect id; must exist as a shaders/post chain resource in v1")
    @Return("true when accepted")
    public boolean set(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id);
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
        ResourceLocation effectId = ResourceLocation.tryParse(id);
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

    /** Whether a runtime definition exists for the id. */
    @Doc("Returns whether a runtime-registered definition exists for the id.")
    @Param(name = "id", value = "effect id")
    @Return("true when registered through PostEffects.register")
    public boolean has(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id);
        return effectId != null && PostEffectManager.hasDefinition(effectId);
    }

    /** Whether the effect exists as a post_effect resource (activatable in v1). */
    @Doc("Returns whether the effect exists as a shaders/post chain resource in the active resource packs (activatable without mixins).")
    @Param(name = "id", value = "effect id")
    @Return("true when resource-backed")
    public boolean isAvailable(String id) {
        ResourceLocation effectId = ResourceLocation.tryParse(id);
        return effectId != null && PostEffectManager.isResourceAvailable(effectId);
    }

    /** Vanilla preset ids usable with set/toggle. */
    @Doc("Lists vanilla preset ids usable with set/toggle.")
    @Return("preset id list")
    public List<String> presets() {
        return new ArrayList<>(PRESETS);
    }
}
