package com.tkisor.nekojs.core.posteffect;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure (Minecraft-free) generators for post-effect chain JSON accepted by the
 * {@code PostEffects.register} binding. Two flavors exist because the post-chain
 * JSON format changed between versions:
 *
 * <ul>
 *   <li><b>Modern (26.x, {@code PostChainConfig.CODEC})</b>: {@code targets} is an object map,
 *       passes reference {@code vertex_shader}/{@code fragment_shader} ids such as
 *       {@code minecraft:core/screenquad} and {@code minecraft:post/blit}.</li>
 *   <li><b>Legacy (1.21.1, {@code PostChain} JSON)</b>: {@code targets} is a string array,
 *       passes reference a {@code name} program id resolved from
 *       {@code assets/<ns>/shaders/program/<name>.json}.</li>
 * </ul>
 *
 * <p>The generated chains render {@code minecraft:main -> swap -> minecraft:main} (the same
 * shape as vanilla's {@code invert} chain): pass 1 applies the effect shader into an
 * internal {@code swap} target, pass 2 blits {@code swap} back to the main target. All shader
 * ids are plain strings, so this class stays in common and is unit-testable without
 * Minecraft classes.
 */
public final class PostEffectChainJson {

    public static final String MAIN_TARGET = "minecraft:main";
    public static final String SWAP_TARGET = "swap";
    public static final String SCREENQUAD_VERTEX_SHADER = "minecraft:core/screenquad";
    public static final String BLIT_FRAGMENT_SHADER = "minecraft:post/blit";
    public static final String BOX_BLUR_FRAGMENT_SHADER = "minecraft:post/box_blur";

    private static final Gson GSON = new Gson();

    private PostEffectChainJson() {
    }

    /**
     * Modern (26.x) two-pass blit chain with a custom fragment shader:
     * {@code main -> (fragmentShaderId) -> swap -> (minecraft:post/blit) -> main}.
     *
     * @param fragmentShaderId shader id referenced by pass 1, e.g. {@code nekojs:post/invert}
     * @param uniformsJsonLiteral raw JSON object literal for pass 1 uniforms block
     *                            (invalid JSON is ignored and replaced with {@code {}})
     */
    public static String simpleBlitChainModern(String fragmentShaderId, String uniformsJsonLiteral) {
        JsonObject uniforms = parseObjectOrEmpty(uniformsJsonLiteral);

        JsonObject pass1 = new JsonObject();
        pass1.addProperty("vertex_shader", SCREENQUAD_VERTEX_SHADER);
        pass1.addProperty("fragment_shader", fragmentShaderId);
        pass1.add("inputs", GSON.toJsonTree(List.of(targetInput(MAIN_TARGET))));
        pass1.addProperty("output", SWAP_TARGET);
        pass1.add("uniforms", uniforms);

        JsonObject pass2 = new JsonObject();
        pass2.addProperty("vertex_shader", SCREENQUAD_VERTEX_SHADER);
        pass2.addProperty("fragment_shader", BLIT_FRAGMENT_SHADER);
        pass2.add("inputs", GSON.toJsonTree(List.of(targetInput(SWAP_TARGET))));
        pass2.addProperty("output", MAIN_TARGET);
        pass2.add("uniforms", blitUniforms());

        JsonObject root = new JsonObject();
        root.add("targets", GSON.toJsonTree(Map.of(SWAP_TARGET, new JsonObject())));
        root.add("passes", GSON.toJsonTree(List.of(pass1, pass2)));
        return GSON.toJson(root);
    }

    /**
     * Modern (26.x) box-blur chain using only vanilla shaders
     * ({@code minecraft:post/box_blur}): for each round, one horizontal and one vertical
     * blur pass between {@code main} and {@code swap}. Same shape as vanilla's blur chain.
     *
     * @param radius blur radius in pixels, clamped to [0, 64]
     * @param rounds horizontal+vertical rounds, clamped to [1, 8]
     */
    public static String boxBlurChainModern(double radius, int rounds) {
        double safeRadius = clamp(radius, 0.0, 64.0);
        int safeRounds = (int) clamp(rounds, 1, 8);

        List<JsonObject> passes = new ArrayList<>();
        for (int i = 0; i < safeRounds; i++) {
            passes.add(boxBlurPass(MAIN_TARGET, SWAP_TARGET, 1.0, 0.0, safeRadius));
            passes.add(boxBlurPass(SWAP_TARGET, MAIN_TARGET, 0.0, 1.0, safeRadius));
        }

        JsonObject root = new JsonObject();
        root.add("targets", GSON.toJsonTree(Map.of(SWAP_TARGET, new JsonObject())));
        root.add("passes", GSON.toJsonTree(passes));
        return GSON.toJson(root);
    }

    /**
     * Legacy (1.21.1) two-pass chain referencing an existing post program
     * ({@code assets/<ns>/shaders/program/<name>.json}): {@code main -> program -> swap -> blit -> main}.
     *
     * @param program program id for pass 1, e.g. {@code minecraft:invert}
     */
    public static String simpleBlitChainLegacy(String program) {
        JsonObject pass1 = new JsonObject();
        pass1.addProperty("name", program);
        pass1.addProperty("intarget", MAIN_TARGET);
        pass1.addProperty("outtarget", SWAP_TARGET);

        JsonObject pass2 = new JsonObject();
        pass2.addProperty("name", "blit");
        pass2.addProperty("intarget", SWAP_TARGET);
        pass2.addProperty("outtarget", MAIN_TARGET);

        JsonObject root = new JsonObject();
        root.add("targets", GSON.toJsonTree(List.of(SWAP_TARGET)));
        root.add("passes", GSON.toJsonTree(List.of(pass1, pass2)));
        return GSON.toJson(root);
    }

    /**
     * Uniform block 形如 vanilla blur.json：{@code uniforms} 是 block 名到 uniform 条目列表
     * 的映射（26.x {@code Map<String, List<UniformValue>>}），条目为 {name, type, value}。
     */
    private static JsonObject boxBlurPass(String input, String output, double dirX, double dirY, double radius) {
        JsonObject blurDir = new JsonObject();
        blurDir.addProperty("name", "BlurDir");
        blurDir.addProperty("type", "vec2");
        blurDir.add("value", GSON.toJsonTree(List.of(dirX, dirY)));

        JsonObject radiusUniform = new JsonObject();
        radiusUniform.addProperty("name", "Radius");
        radiusUniform.addProperty("type", "float");
        radiusUniform.addProperty("value", radius);

        JsonObject uniforms = new JsonObject();
        uniforms.add("BlurConfig", GSON.toJsonTree(List.of(blurDir, radiusUniform)));

        JsonObject pass = new JsonObject();
        pass.addProperty("vertex_shader", SCREENQUAD_VERTEX_SHADER);
        pass.addProperty("fragment_shader", BOX_BLUR_FRAGMENT_SHADER);
        pass.add("inputs", GSON.toJsonTree(List.of(targetInput(input, true))));
        pass.addProperty("output", output);
        pass.add("uniforms", uniforms);
        return pass;
    }

    private static JsonObject targetInput(String target) {
        return targetInput(target, false);
    }

    private static JsonObject targetInput(String target, boolean bilinear) {
        JsonObject input = new JsonObject();
        input.addProperty("sampler_name", "In");
        input.addProperty("target", target);
        if (bilinear) {
            input.addProperty("bilinear", true);
        }
        return input;
    }

    /** {@code BlitConfig} block（vanilla invert.json 的第二通道）：恒等色调制。 */
    private static JsonObject blitUniforms() {
        JsonObject colorModulate = new JsonObject();
        colorModulate.addProperty("name", "ColorModulate");
        colorModulate.addProperty("type", "vec4");
        colorModulate.add("value", GSON.toJsonTree(List.of(1.0, 1.0, 1.0, 1.0)));

        JsonObject uniforms = new JsonObject();
        uniforms.add("BlitConfig", GSON.toJsonTree(List.of(colorModulate)));
        return uniforms;
    }

    private static JsonObject parseObjectOrEmpty(String jsonLiteral) {
        if (jsonLiteral == null || jsonLiteral.isBlank()) {
            return new JsonObject();
        }
        try {
            var parsed = JsonParser.parseString(jsonLiteral);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException malformed) {
            return new JsonObject();
        }
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
