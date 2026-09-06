//? if >=26 {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绑定 helper 的可裸跑部分：KMath 纯数学；ParticleOptions 无参路径经
 * {@link VanillaRegistryProbe} assume-skip（注册表真值）。
 */
class BindingHelpersTest {

    @Test
    void kMathDegRadRoundTrip() {
        assertEquals(Math.PI / 2, KMathJS.deg(90), 1e-12);
        assertEquals(90.0, KMathJS.rad(Math.PI / 2), 1e-12);
    }

    @Test
    void kMathVectorFactories() {
        assertEquals(new Vec3(1, 2, 3), KMathJS.vec3(1, 2, 3));
        assertEquals(net.minecraft.core.BlockPos.containing(1.5, 64.0, -2.5),
                KMathJS.blockPos(1.5, 64.0, -2.5));
    }

    @Test
    void textIconsAreNonBlankConstants() {
        for (String icon : new String[]{TextIcons.CHECK, TextIcons.CROSS, TextIcons.STAR, TextIcons.HEART}) {
            assertNotNull(icon);
            assertTrue(icon.isBlank() == false);
        }
    }

    @Test
    void particleOptionsWithoutArgumentsReturnsTheParticleTypeItself() {
        Assumptions.assumeTrue(VanillaRegistryProbe.available());
        try (Context context = Context.newBuilder().build()) {
            ParticleOptions options = new ParticleOptionsJS().of(
                    net.minecraft.resources.Identifier.parse("minecraft:cloud"));
            assertInstanceOf(ParticleType.class, options);
        }
    }

    @Test
    void unknownParticleIdIsRejected() {
        Assumptions.assumeTrue(VanillaRegistryProbe.available());
        try (Context context = Context.newBuilder().build()) {
            var helper = new ParticleOptionsJS();
            assertThrows(com.tkisor.nekojs.api.data.ValueConversionException.class,
                    () -> helper.of(net.minecraft.resources.Identifier.parse("nekojs:no_such_particle")));
        }
    }
}
//?}
