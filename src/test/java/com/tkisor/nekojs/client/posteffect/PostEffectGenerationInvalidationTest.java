//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 28 generation 换代的缓存失效 fixture（五节点共享同一份代码；只驱动 Adapter 的失效决策
 * 与安装面，不加载 GPU 资源，因此无需注册表）。
 *
 * <p><b>P1 回归（先红后绿）</b>：{@code PostChain} 缓存键是 {@code (id, allowedTargets)}，
 * 不含定义内容。同一 id 在新 generation 改 chain JSON 重声明时，若只对「未再声明 ∪ 显式
 * 退役」的 id 失效缓存，旧链仍会被 {@code getOrCreatePostChain} 命中，而 shader 源已读新
 * 定义——正是 AC3 禁止的半更新。本 fixture 钉住「重声明 ⇒ 该 id 必须进失效集」。
 *
 * <p>id 类型写成 {@code Identifier}：本文件住共享测试树，1.21.1 节点由 stonecutter 的
 * {@code !mc_ids} 规则求值成 {@code ResourceLocation}，与孪生实现一致。
 */
class PostEffectGenerationInvalidationTest {

    private static final String SHADER_A = "minecraft:post/invert";
    private static final String SHADER_B = "minecraft:post/blur";

    @BeforeAll
    static void initPlatform() {
        PostEffectDeclarationHarness.ensurePlatformInitialized();
    }

    private static Identifier id(String value) {
        return Identifier.tryParse(value);
    }

    private static PostEffectManager.Definition definition(String value, String chainJson) {
//? if >=26 {
        return PostEffectManager.parseDefinition(id(value), chainJson, Map.of(), Map.of());
//?} else {
/*        return PostEffectManager.parseDefinition(id(value), chainJson);
*///?}
    }

    private static String chainJson(String shaderOrProgram) {
//? if >=26 {
        return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainModern(shaderOrProgram, null);
//?} else {
/*        return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainLegacy(shaderOrProgram);
*///?}
    }

    private static Map<Identifier, PostEffectManager.Definition> set(
            String idA, String chainA, String idB, String chainB) {
        Map<Identifier, PostEffectManager.Definition> map = new LinkedHashMap<>();
        map.put(id(idA), definition(idA, chainA));
        map.put(id(idB), definition(idB, chainB));
        return map;
    }

    private static Set<String> strings(Map<?, ?> byId) {
        return strings(byId.keySet());
    }

    private static Set<String> strings(Set<?> ids) {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (Object value : ids) {
            out.add(String.valueOf(value));
        }
        return out;
    }

    @Test
    void redeclaringAnIdWithDifferentChainJsonMustInvalidateItsCachedChain() {
        PostEffectDeclarationHarness.resetDeclarations();
        Map<Identifier, PostEffectManager.Definition> first = new LinkedHashMap<>();
        first.put(id("nekojs:gray"), definition("nekojs:gray", chainJson(SHADER_A)));
        PostEffectManager.installGeneration(1L, first, Set.of());

        // 同一 id 重声明为不同的链：定义必须真的变了（否则测不到缓存问题）。
        Map<Identifier, PostEffectManager.Definition> second = new LinkedHashMap<>();
        second.put(id("nekojs:gray"), definition("nekojs:gray", chainJson(SHADER_B)));
        assertFalse(first.get(id("nekojs:gray")).equals(second.get(id("nekojs:gray"))),
                "the fixture must redeclare the id with a genuinely different definition");

        // P1 的核心断言：修复前这里是空集（旧缓存仍被命中 → 旧链 + 新 shader 源的半更新）。
        assertEquals(Set.of("nekojs:gray"), strings(PostEffectManager.redefinedIds(first, second)),
                "a same-id re-declaration with different content must invalidate its cached chain");

        PostEffectManager.installGeneration(2L, second, Set.of());
        assertEquals(Set.of("nekojs:gray"), strings(PostEffectManager.installedDefinitions()),
                "the re-declared id stays installed with the new definition");
        assertTrue(PostEffectManager.removedIds(first, second, Set.of()).isEmpty(),
                "a re-declared id is invalidated, not removed (no double release)");
        PostEffectDeclarationHarness.resetDeclarations();
    }

    @Test
    void identicalRedeclarationAndUnrelatedIdDoNotInvalidate() {
        PostEffectDeclarationHarness.resetDeclarations();
        Map<Identifier, PostEffectManager.Definition> first =
                set("nekojs:gray", chainJson(SHADER_A), "nekojs:kept", chainJson(SHADER_B));
        Map<Identifier, PostEffectManager.Definition> identical =
                set("nekojs:gray", chainJson(SHADER_A), "nekojs:kept", chainJson(SHADER_B));
        Map<Identifier, PostEffectManager.Definition> otherChanged =
                set("nekojs:gray", chainJson(SHADER_A), "nekojs:kept", chainJson(SHADER_A));

        assertTrue(strings(PostEffectManager.redefinedIds(first, identical)).isEmpty(),
                "an identical replay is not an invalidation (no needless cache churn)");
        assertEquals(Set.of("nekojs:kept"), strings(PostEffectManager.redefinedIds(first, otherChanged)),
                "only the id whose definition changed is invalidated");
    }

    @Test
    void retiredAndUndeclaredIdsAreRemovedWhileRedeclaredIdsAreOnlyInvalidated() {
        PostEffectDeclarationHarness.resetDeclarations();
        Map<Identifier, PostEffectManager.Definition> first =
                set("nekojs:a", chainJson(SHADER_A), "nekojs:b", chainJson(SHADER_B));
        PostEffectManager.installGeneration(1L, first, Set.of());

        // b 不再声明、a 重声明为新内容：b 进 removed，a 进 redefined，两者不相交。
        Map<Identifier, PostEffectManager.Definition> second = new LinkedHashMap<>();
        second.put(id("nekojs:a"), definition("nekojs:a", chainJson(SHADER_B)));
        Set<String> removed = strings(PostEffectManager.removedIds(first, second, Set.of()));
        Set<String> redefined = strings(PostEffectManager.redefinedIds(first, second));
        assertEquals(Set.of("nekojs:b"), removed, "an undeclared id is removed");
        assertEquals(Set.of("nekojs:a"), redefined, "a re-declared id is invalidated, not removed");
        Set<String> overlap = new java.util.LinkedHashSet<>(removed);
        overlap.retainAll(redefined);
        assertTrue(overlap.isEmpty(), "the removal and invalidation sets must not overlap");
        PostEffectDeclarationHarness.resetDeclarations();
    }

    @Test
    void explicitRetireOfAnInstalledIdRemovesIt() {
        PostEffectDeclarationHarness.resetDeclarations();
        Map<Identifier, PostEffectManager.Definition> first =
                set("nekojs:a", chainJson(SHADER_A), "nekojs:b", chainJson(SHADER_B));
        PostEffectManager.installGeneration(1L, first, Set.of());

        Map<Identifier, PostEffectManager.Definition> unchanged =
                set("nekojs:a", chainJson(SHADER_A), "nekojs:b", chainJson(SHADER_B));
        assertEquals(Set.of("nekojs:b"),
                strings(PostEffectManager.removedIds(first, unchanged, Set.of(id("nekojs:b")))),
                "an explicit retire of an installed id removes it even when it is still declared");
        assertTrue(PostEffectManager.removedIds(first, unchanged, Set.of(id("nekojs:never"))).isEmpty(),
                "retiring an id that was never installed removes nothing (no cache to release)");
        PostEffectDeclarationHarness.resetDeclarations();
    }
}
//?}
