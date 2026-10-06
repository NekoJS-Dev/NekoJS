package com.tkisor.nekojs.client.renderer;

import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/** Resolves script entity render configuration on the client, never on a dedicated server. */
public final class NekoEntityRenderers {
    private NekoEntityRenderers() {}

    public static EntityRendererProvider<LivingEntity> provider(EntityTypeBuilder.RenderConfiguration configuration) {
        if (configuration.renderer().equals("humanoid")) {
            return context -> new NekoHumanoidEntityRenderer(context, configuration.texture(), configuration.shadowRadius());
        }
        Constructor<?> constructor;
        try {
            Class<?> rendererClass = Class.forName(configuration.renderer(), false, NekoEntityRenderers.class.getClassLoader());
            if (!EntityRenderer.class.isAssignableFrom(rendererClass) || Modifier.isAbstract(rendererClass.getModifiers())) {
                throw new IllegalArgumentException("[NEKO-4025] Entity renderer must be a concrete EntityRenderer: " + configuration.renderer());
            }
            EntityRendererTypes.requireCompatible(rendererClass, configuration.entityClass());
            constructor = rendererClass.getConstructor(EntityRendererProvider.Context.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("[NEKO-4025] Entity renderer must have a public constructor(Context): "
                    + configuration.renderer(), exception);
        }
        return context -> create(constructor, context);
    }

    @SuppressWarnings("unchecked")
    private static EntityRenderer<LivingEntity, ?> create(Constructor<?> constructor, EntityRendererProvider.Context context) {
        try {
            return (EntityRenderer<LivingEntity, ?>) constructor.newInstance(context);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("[NEKO-4025] Entity renderer construction failed: "
                    + constructor.getDeclaringClass().getName(), exception);
        }
    }
}
