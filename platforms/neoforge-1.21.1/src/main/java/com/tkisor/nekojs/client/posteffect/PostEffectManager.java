package com.tkisor.nekojs.client.posteffect;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime client post-effect registry (feature 8b, client-side only), 1.21.1 mirror of the
 * 26-shared {@code PostEffectManager}.
 *
 * <p><b>1.21.1 specifics (verified against NeoForge 21.1 sources):</b>
 * {@code GameRenderer} has no {@code postEffectId} field — it keeps the loaded
 * {@code PostChain} instance directly. Activation goes through the public
 * {@code loadEffect(ResourceLocation)} with a full {@code shaders/post/<name>.json}
 * location, clearing through {@code shutdownEffect()}. The active effect id is therefore
 * tracked locally ({@code GameRenderer#currentEffect()} exposes the chain, not the id);
 * entity-driven effects (creeper/spider vision) will not update the tracked id.
 * {@code effectActive} remains a private field read reflectively.
 *
 * <p><b>v1 split (no mixins in this batch):</b> {@link #set}/{@link #clear}/{@link #toggle}
 * work with any effect that exists as a resource ({@code assets/<ns>/shaders/post/<name>.json})
 * — the six vanilla presets plus resource-pack/mod-provided chains. {@link #register} parses
 * (well-formedness only — 1.21.1 chain configs are private to {@code PostChain}) and stores
 * runtime definitions; activating runtime-only ids additionally requires a future 1.21.1
 * post-chain mixin and is rejected with a warning until then.
 */
public final class PostEffectManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS PostEffects");

    /** 1.21.1 post chains live at {@code assets/<ns>/shaders/post/<name>.json}. */
    private static final FileToIdConverter POST_EFFECT_FILES = FileToIdConverter.json("shaders/post");

    private static final Map<ResourceLocation, Definition> DEFINITIONS = new ConcurrentHashMap<>();

    /** Tracked id of the effect activated through this manager (vanilla keeps no id field). */
    private static volatile ResourceLocation lastSetId;

    private PostEffectManager() {
    }

    /** A runtime-registered effect: raw chain JSON plus optional shader notes. */
    record Definition(String chainJson) {
    }

    // ---- registration (stored for a future 1.21.1 post-chain mixin; not activatable in v1) ----

    public static boolean register(ResourceLocation id, String chainJson) {
        if (chainJson == null || chainJson.isBlank()) return false;
        try {
            JsonParser.parseString(chainJson);
        } catch (JsonParseException e) {
            NekoJS.LOGGER.warn("Failed to parse runtime client post effect {}", id, e);
            return false;
        }
        DEFINITIONS.put(id, new Definition(chainJson));
        LOGGER.info("Registered runtime client post effect {} (activation requires a pending 1.21.1 post-chain mixin)", id);
        return true;
    }

    public static boolean unregister(ResourceLocation id) {
        boolean removed = DEFINITIONS.remove(id) != null;
        if (removed && id.equals(current())) {
            clear();
        }
        return removed;
    }

    /** Clears every runtime-registered definition (script reload teardown). */
    public static void clearRegistered() {
        ResourceLocation current = current();
        DEFINITIONS.clear();
        if (current != null) {
            clear();
        }
    }

    public static boolean hasDefinition(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    // ---- activation (vanilla-resource-backed effects; client-thread execution) ----

    /** Maps {@code ns:name} to the 1.21.1 chain location {@code ns:shaders/post/name.json}. */
    static ResourceLocation chainLocation(ResourceLocation id) {
        return id.withPath("shaders/post/" + id.getPath() + ".json");
    }

    /**
     * Activates the post effect {@code id} through {@code GameRenderer#loadEffect}. Returns
     * {@code false} when the id is only known as a runtime definition (needs a future mixin)
     * or the chain resource is missing (loadEffect would log an error every frame).
     */
    public static boolean set(ResourceLocation id) {
        if (isRuntimeOnly(id)) {
            LOGGER.warn("Post effect {} was registered at runtime and cannot be activated until the 1.21.1 post-chain mixin lands; use a resource-pack effect id instead", id);
            return false;
        }
        if (!isResourceAvailable(id)) {
            LOGGER.warn("Post effect {} has no shaders/post chain resource; refusing to activate", id);
            return false;
        }
        ResourceLocation location = chainLocation(id);
        lastSetId = id;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.loadEffect(location));
        return true;
    }

    /** Deactivates any active post effect ({@code GameRenderer#shutdownEffect()}). */
    public static boolean clear() {
        lastSetId = null;
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.shutdownEffect());
        return true;
    }

    /** Toggles: activates {@code id} when idle/different, clears when {@code id} is active. */
    public static boolean toggle(ResourceLocation id) {
        if (id.equals(current())) {
            return clear();
        }
        return set(id);
    }

    /**
     * Tracked active effect id, or null. 1.21.1 {@code GameRenderer} exposes the loaded
     * {@code PostChain} (not its id), so ids activated outside this manager (e.g. entity
     * vision effects) are not reflected.
     */
    @Nullable
    public static ResourceLocation current() {
        if (lastSetId == null) return null;
        return Minecraft.getInstance().gameRenderer.currentEffect() != null ? lastSetId : null;
    }

    /**
     * Whether an effect is currently applied. {@code effectActive} is a private
     * {@code GameRenderer} field (no public getter), read via reflection.
     */
    public static boolean isActive() {
        Object gameRenderer = Minecraft.getInstance().gameRenderer;
        try {
            Field field = gameRenderer.getClass().getDeclaredField("effectActive");
            field.setAccessible(true);
            return Boolean.TRUE.equals(field.get(gameRenderer));
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Could not read GameRenderer.effectActive", e);
            return Minecraft.getInstance().gameRenderer.currentEffect() != null;
        }
    }

    /** True when the effect exists as a {@code shaders/post} chain resource in the active packs. */
    public static boolean isResourceAvailable(ResourceLocation id) {
        return Minecraft.getInstance().getResourceManager().getResource(chainLocation(id)).isPresent();
    }

    static boolean isRuntimeOnly(ResourceLocation id) {
        return !isResourceAvailable(id) && DEFINITIONS.containsKey(id);
    }
}
