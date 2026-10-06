package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.entity.NekoScriptMob;
import graal.graalvm.polyglot.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityRegistrationSafetyTest {
    private static EntityTypeBuilder builder() {
        return new EntityTypeBuilder(Identifier.parse("nekojs:entity_safety"));
    }

    @Test
    void visibleDefaultConfigurationAndBeanPropertiesAreScriptAccessible() {
        EntityTypeBuilder builder = builder();
        assertEquals("humanoid", builder.getRenderer());
        assertEquals("minecraft:textures/entity/zombie/zombie.png", builder.getTexture());
        assertTrue(builder.getShadowRadius() > 0F);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("build", BuilderSurface.of(builder));
            context.eval("js", "build.renderer = 'humanoid'; build.texture = 'demo:textures/entity/mob.png'; build.shadowRadius = 0.25;");
        }
        assertEquals("demo:textures/entity/mob.png", builder.getTexture());
        assertEquals(0.25F, builder.getShadowRadius());
    }

    @Test
    void invalidRenderConfigurationAndInfiniteGeometryAreRejected() {
        EntityTypeBuilder builder = builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setTexture("https://example.com/image.png"));
        assertThrows(IllegalArgumentException.class, () -> builder.setTexture("demo:textures/../image.png"));
        assertThrows(IllegalArgumentException.class, () -> builder.setRenderer("not a class"));
        assertThrows(IllegalArgumentException.class, () -> builder.setShadowRadius(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> builder.setWidth(Float.POSITIVE_INFINITY));
    }

    @Test
    void nativeClassConstructorIsValidatedBeforeRegistryOrEntityCreation() {
        EntityTypeBuilder builder = builder();
        assertThrows(IllegalArgumentException.class, () -> builder.entityClass(LivingEntity.class));
        assertThrows(IllegalArgumentException.class, () -> builder.entityClass(NoStandardConstructor.class));
        assertSame(builder, builder.entityClass(NekoScriptMob.class));
    }

    @Test
    void guestEntityFactoryCannotEscapeItsContextOwner() {
        EntityTypeBuilder builder = builder();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("build", BuilderSurface.of(builder));
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> context.eval("js", "build.factory((type, level) => null)"));
            assertTrue(error.getMessage().contains("native Java implementation"), error.getMessage());
        }
        assertSame(builder, builder.factory(new NativeFactory()));
    }

    public static final class NoStandardConstructor extends NekoScriptMob {
        public NoStandardConstructor() {
            super(null, null);
        }
    }

    private static final class NativeFactory implements java.util.function.BiFunction<EntityType<?>, Level, LivingEntity> {
        @Override
        public LivingEntity apply(EntityType<?> type, Level level) {
            return null;
        }
    }
}
