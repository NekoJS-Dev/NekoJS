package com.tkisor.nekojs.client.renderer;

import com.tkisor.nekojs.wrapper.entity.NekoScriptMob;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityRendererConfigurationTest {
    @Test
    void defaultModelHasVisibleBodyHeadAndLimbs() {
        var model = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 64).bakeRoot();
        for (String part : new String[] {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            assertFalse(model.getChild(part).isEmpty(), part + " must contain drawable vanilla geometry");
        }
    }

    @Test
    void defaultProviderIsAvailableWithoutStartingAClient() {
        var configuration = new EntityTypeBuilder.RenderConfiguration("humanoid",
                Identifier.parse("minecraft:textures/entity/zombie/zombie.png"), 0.5F, NekoScriptMob.class);
        assertNotNull(NekoEntityRenderers.provider(configuration));
        assertDoesNotThrow(() -> EntityRendererTypes.requireCompatible(NekoHumanoidEntityRenderer.class, NekoScriptMob.class));
    }

    @Test
    void invalidNativeRendererClassFailsBeforeFactoryExecution() {
        var configuration = new EntityTypeBuilder.RenderConfiguration("java.lang.String",
                Identifier.parse("minecraft:textures/entity/zombie/zombie.png"), 0.5F, NekoScriptMob.class);
        assertThrows(IllegalArgumentException.class, () -> NekoEntityRenderers.provider(configuration));
    }

    @Test
    void nativeRendererMustAcceptTheDeclaredEntityClass() throws ClassNotFoundException {
        Class<?> zombieRenderer = Class.forName("net.minecraft.client.renderer.entity.ZombieRenderer", false,
                getClass().getClassLoader());
        assertThrows(IllegalArgumentException.class,
                () -> EntityRendererTypes.requireCompatible(zombieRenderer, NekoScriptMob.class));
        assertThrows(IllegalArgumentException.class,
                () -> EntityRendererTypes.requireCompatible(zombieRenderer, null));
        Class<?> zombie = Class.forName("net.minecraft.world.entity.monster.zombie.Zombie", false,
                getClass().getClassLoader());
        assertDoesNotThrow(() -> EntityRendererTypes.requireCompatible(zombieRenderer, zombie));
    }
}
