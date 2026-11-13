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
 * Cache invalidation fixture for post-effect generation swaps (shared by all five nodes). It drives
 * only the invalidation decision and the install surface of the adapter and loads no GPU resources, so
 * no registry is needed.
 *
 * <p><b>Regression</b>: the {@code PostChain} cache key is {@code (id, allowedTargets)} and does not
 * include the definition content. When the same id is redeclared in a new generation with a different
 * chain JSON, invalidating only the ids that were removed or no longer declared leaves the old chain
 * reachable by {@code getOrCreatePostChain} while the shader source already reflects the new
 * definition, the half-update the design forbids. This fixture pins that a redeclaration puts the id
 * into the invalidation set.
 *
 * <p>The id type is spelled {@code Identifier}: this file lives in the shared test tree, and for the
 * 1.21.1 node stonecutter's {@code !mc_ids} rule evaluates it to {@code Identifier}, matching the twin
 * implementation.
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
        /*return PostEffectManager.parseDefinition(id(value), chainJson);
*///?}
    }

    private static String chainJson(String shaderOrProgram) {
//? if >=26 {
        return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainModern(shaderOrProgram, null);
//?} else {
        /*return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
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

        // The same id is redeclared with a different chain, so the definition must really differ.
        // Otherwise the cache problem cannot show up.
        Map<Identifier, PostEffectManager.Definition> second = new LinkedHashMap<>();
        second.put(id("nekojs:gray"), definition("nekojs:gray", chainJson(SHADER_B)));
        assertFalse(first.get(id("nekojs:gray")).equals(second.get(id("nekojs:gray"))),
                "the fixture must redeclare the id with a genuinely different definition");

        // The core assertion: before the fix this set is empty, so the old cache is still hit and the
        // result is an old chain with the new shader source.
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

        // b is no longer declared and a is redeclared with new content: b lands in removed, a in
        // redefined, and the two sets do not intersect.
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
