package com.tkisor.nekojs.core.posteffect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PostEffectChainJson} 结构校验：生成的 JSON 可解析且字段与目标版本的
 * post-chain 格式一致（26.x {@code PostChainConfig} / 1.21.1 {@code PostChain}），
 * main -> swap -> main 两通道形状与 vanilla invert 链对齐。
 */
class PostEffectChainJsonTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void simpleBlitChainModernMatchesPostChainConfigShape() {
        String json = PostEffectChainJson.simpleBlitChainModern("nekojs:post/invert", null);
        JsonObject root = parse(json);

        // targets 是 object map（26.x Codec.unboundedMap），不是数组
        assertTrue(root.getAsJsonObject("targets").has("swap"));
        JsonArray passes = root.getAsJsonArray("passes");
        assertEquals(2, passes.size());

        JsonObject pass1 = passes.get(0).getAsJsonObject();
        assertEquals(PostEffectChainJson.SCREENQUAD_VERTEX_SHADER, pass1.get("vertex_shader").getAsString());
        assertEquals("nekojs:post/invert", pass1.get("fragment_shader").getAsString());
        assertEquals(PostEffectChainJson.SWAP_TARGET, pass1.get("output").getAsString());
        JsonObject input1 = pass1.getAsJsonArray("inputs").get(0).getAsJsonObject();
        assertEquals("In", input1.get("sampler_name").getAsString());
        assertEquals(PostEffectChainJson.MAIN_TARGET, input1.get("target").getAsString());

        JsonObject pass2 = passes.get(1).getAsJsonObject();
        assertEquals(PostEffectChainJson.BLIT_FRAGMENT_SHADER, pass2.get("fragment_shader").getAsString());
        assertEquals(PostEffectChainJson.MAIN_TARGET, pass2.get("output").getAsString());
        JsonObject input2 = pass2.getAsJsonArray("inputs").get(0).getAsJsonObject();
        assertEquals(PostEffectChainJson.SWAP_TARGET, input2.get("target").getAsString());
        // blit 通道的 BlitConfig block 直接映射到条目列表（vanilla invert.json 第二通道）
        JsonObject colorModulate = pass2.getAsJsonObject("uniforms").getAsJsonArray("BlitConfig")
                .get(0).getAsJsonObject();
        assertEquals("ColorModulate", colorModulate.get("name").getAsString());
        assertEquals("vec4", colorModulate.get("type").getAsString());
    }

    @Test
    void simpleBlitChainModernMergesUniformsLiteral() {
        String uniforms = "{\"InvertConfig\":[{\"name\":\"InverseAmount\",\"type\":\"float\",\"value\":0.5}]}";
        String json = PostEffectChainJson.simpleBlitChainModern("nekojs:post/invert", uniforms);
        JsonObject pass1 = parse(json).getAsJsonArray("passes").get(0).getAsJsonObject();
        assertEquals(0.5, pass1.getAsJsonObject("uniforms")
                .getAsJsonArray("InvertConfig").get(0).getAsJsonObject()
                .get("value").getAsDouble(), 1e-9);
    }

    @Test
    void invalidUniformsLiteralFallsBackToEmptyObject() {
        String json = PostEffectChainJson.simpleBlitChainModern("nekojs:post/x", "{not json");
        JsonObject pass1 = parse(json).getAsJsonArray("passes").get(0).getAsJsonObject();
        assertTrue(pass1.getAsJsonObject("uniforms").entrySet().isEmpty());
    }

    @Test
    void boxBlurChainModernEmitsPairsOfDirectionalPasses() {
        String json = PostEffectChainJson.boxBlurChainModern(5.0, 2);
        JsonObject root = parse(json);
        JsonArray passes = root.getAsJsonArray("passes");
        assertEquals(4, passes.size(), "2 轮 × (水平 + 垂直) = 4 通道");

        JsonObject first = passes.get(0).getAsJsonObject();
        assertEquals(PostEffectChainJson.BOX_BLUR_FRAGMENT_SHADER, first.get("fragment_shader").getAsString());
        assertEquals(PostEffectChainJson.MAIN_TARGET,
                first.getAsJsonArray("inputs").get(0).getAsJsonObject().get("target").getAsString());
        assertEquals(PostEffectChainJson.SWAP_TARGET, first.get("output").getAsString());
        assertTrue(first.getAsJsonArray("inputs").get(0).getAsJsonObject().get("bilinear").getAsBoolean());
        // uniforms: {"BlurConfig": [{"name":"BlurDir","type":"vec2","value":[1.0,0.0]}, ...]}
        JsonObject blurDir = first.getAsJsonObject("uniforms").getAsJsonArray("BlurConfig")
                .get(0).getAsJsonObject();
        assertEquals("BlurDir", blurDir.get("name").getAsString());
        assertEquals("vec2", blurDir.get("type").getAsString());
        assertEquals(1.0, blurDir.getAsJsonArray("value").get(0).getAsDouble(), 1e-9);
        assertEquals(0.0, blurDir.getAsJsonArray("value").get(1).getAsDouble(), 1e-9);
    }

    @Test
    void boxBlurChainModernClampsRadiusAndRounds() {
        JsonObject root = parse(PostEffectChainJson.boxBlurChainModern(Double.NaN, 99));
        assertEquals(16, root.getAsJsonArray("passes").size(), "99 轮收敛到 8 轮（8 × 2 通道）");
        JsonObject radius = root.getAsJsonArray("passes").get(0).getAsJsonObject()
                .getAsJsonObject("uniforms").getAsJsonArray("BlurConfig")
                .get(1).getAsJsonObject();
        assertEquals("Radius", radius.get("name").getAsString());
        assertEquals(0.0, radius.get("value").getAsDouble(), 1e-9, "NaN radius 收敛到 0");
    }

    @Test
    void simpleBlitChainLegacyMatchesPostChainShape() {
        String json = PostEffectChainJson.simpleBlitChainLegacy("minecraft:invert");
        JsonObject root = parse(json);

        // 1.21.1 targets 是字符串数组
        assertEquals("swap", root.getAsJsonArray("targets").get(0).getAsString());
        JsonArray passes = root.getAsJsonArray("passes");
        assertEquals(2, passes.size());

        JsonObject pass1 = passes.get(0).getAsJsonObject();
        assertEquals("minecraft:invert", pass1.get("name").getAsString());
        assertEquals(PostEffectChainJson.MAIN_TARGET, pass1.get("intarget").getAsString());
        assertEquals(PostEffectChainJson.SWAP_TARGET, pass1.get("outtarget").getAsString());

        JsonObject pass2 = passes.get(1).getAsJsonObject();
        assertEquals("blit", pass2.get("name").getAsString());
        assertEquals(PostEffectChainJson.MAIN_TARGET, pass2.get("outtarget").getAsString());
        assertFalse(pass1.has("vertex_shader"), "1.21.1 通道不使用 26.x 的 shader id 字段");
    }
}
