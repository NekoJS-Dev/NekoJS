package com.tkisor.nekojs.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for the {@code BlockEvents.randomTick} mixin: it must inject into the method vanilla
 * actually dispatches, not into the {@code BlockBehaviour} interface default. That default is
 * empty and every vanilla randomly-ticking block overrides it, so an injection there leaves the
 * event unreachable for vanilla blocks.
 *
 * <p>Two parts, both headless-JVM friendly:
 * <ol>
 *   <li><b>Runtime shape</b> (reflection on the node classpath, mojmap names on both
 *       loaders): {@code BlockBehaviour.BlockStateBase.randomTick(ServerLevel, BlockPos,
 *       RandomSource)} exists as the concrete dispatch method, {@code BlockState} does not
 *       override it (so {@code ServerLevel}'s per-position virtual dispatch lands on it for
 *       every block), and at least one vanilla randomly-ticking block (CropBlock) overrides the
 *       4-arg {@code BlockBehaviour.randomTick}, which proves vanilla overrides never reach the
 *       interface default.</li>
 *   <li><b>Source trace</b>: the mixins of both loader trees target {@code BlockStateBase},
 *       keep the {@code isRandomlyTicking()} gate (vanilla calls this method for every position
 *       in a ticking section; the gate keeps the "only randomly-ticking blocks" behavior) and
 *       the no-listener fast path.</li>
 * </ol>
 * The real dispatch is covered by the runServer smoke run (forceloaded chunk plus script
 * listener markers).
 */
class BlockRandomTickInjectionSiteTest {

    @Test
    void blockStateBaseFunnelExistsAndIsTheDispatchTargetForEveryBlock() throws NoSuchMethodException {
        Method funnel = BlockBehaviour.BlockStateBase.class
                .getMethod("randomTick", ServerLevel.class, BlockPos.class, RandomSource.class);
        assertFalse(Modifier.isAbstract(funnel.getModifiers()),
                "the injected funnel must be concrete on this node");
        // ServerLevel dispatches blockstate.randomTick(...); BlockState itself must not
        // re-declare it, or the funnel would not be the single funnel anymore
        for (Method method : BlockState.class.getDeclaredMethods()) {
            assertFalse(method.getName().equals("randomTick"),
                    "BlockState re-declaring randomTick would bypass the injected funnel");
        }
        // BlockStateBase exposes the gate the mixin applies (documented semantics keeper)
        assertNotNull(BlockBehaviour.BlockStateBase.class.getMethod("isRandomlyTicking"),
                "the isRandomlyTicking gate must be reachable on the funnel class");
    }

    @Test
    void vanillaRandomlyTickingBlocksOverrideTheInterfaceDefaultAway() throws NoSuchMethodException {
        Method vanillaOverride = CropBlock.class.getDeclaredMethod(
                "randomTick", BlockState.class, ServerLevel.class, BlockPos.class, RandomSource.class);
        assertFalse(Modifier.isAbstract(vanillaOverride.getModifiers()),
                "CropBlock is a vanilla randomly-ticking block that overrides the 4-arg method");
        // and the interface default itself exists (the old, bypassed injection site; it is
        // compiled protected on the interface, hence getDeclaredMethod)
        assertNotNull(BlockBehaviour.class.getDeclaredMethod(
                "randomTick", BlockState.class, ServerLevel.class, BlockPos.class, RandomSource.class),
                "the old interface-default site exists on this node");
    }

    @Test
    void bothLoaderTwinsInjectAtTheFunnelWithTheSemanticsGuards() {
        String neoforge = read("src/main/java/com/tkisor/nekojs/mixin/BlockBehaviourMixin.java");
        assertTrue(neoforge.contains("@Mixin(BlockBehaviour.BlockStateBase.class)"),
                "the NeoForge mixin must target the BlockStateBase funnel (D5)");
        assertFalse(neoforge.contains("@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.class)"),
                "the old interface-default target made the event unreachable for vanilla blocks");
        assertTrue(neoforge.contains("hasListeners()"),
                "randomTick is hot: the no-listener fast path must stay");
        assertTrue(neoforge.contains("isRandomlyTicking()"),
                "the funnel fires for every position in a ticking section; the gate keeps the"
                        + " documented only-randomly-ticking-blocks semantics");

        String fabric = read("src/fabric/java/com/tkisor/nekojs/fabric/mixin/MixinBlockBehaviourRandomTick.java");
        assertTrue(fabric.contains("@Mixin(BlockBehaviour.BlockStateBase.class)"),
                "the fabric twin must target the same BlockStateBase funnel (D5)");
        assertTrue(fabric.contains("isRandomlyTicking()"),
                "the fabric twin keeps the only-randomly-ticking-blocks gate");
        assertTrue(fabric.contains("hasListeners()"),
                "the fabric twin keeps the no-listener fast path");
    }

    private static String read(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                break;
            }
            dir = dir.getParent();
        }
        assertNotNull(dir, "cannot locate repo root from " + Path.of("").toAbsolutePath());
        try {
            return Files.readString(dir.resolve(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + relative, e);
        }
    }
}
