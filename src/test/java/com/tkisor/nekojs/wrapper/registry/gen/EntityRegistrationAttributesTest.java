package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EntityRegistrationAttributesTest {
    @Test
    void missingRequiredAttributesFailBeforeAnEntityTypeIsPublished() {
        assumeTrue(VanillaRegistryProbe.registryMetadataAvailable(), "native attribute registry requires Minecraft bootstrap");
        EntityTypeBuilder builder = new EntityTypeBuilder(Identifier.parse("nekojs:missing_entity_attributes"));
        builder.attributeSupplier(AttributeSupplier.builder().build());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, builder::get);
        assertTrue(error.getMessage().contains("missing"));
        assertNull(EntityTypeBuilder.getEntityType(builder.id));
    }

    @Test
    void nonFiniteConfiguredAttributesFailBeforeAnEntityTypeIsPublished() {
        assumeTrue(VanillaRegistryProbe.registryMetadataAvailable(), "native attribute registry requires Minecraft bootstrap");
        EntityTypeBuilder builder = new EntityTypeBuilder(Identifier.parse("nekojs:nan_entity_attributes"));
        builder.attributes(attributes -> attributes.maxHealth(Double.NaN));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, builder::get);
        assertTrue(error.getMessage().contains("finite"));
        assertNull(EntityTypeBuilder.getEntityType(builder.id));
    }

    @Test
    void incompleteMobBaselinesAreRejectedForDefaultAndOpaqueFactories() {
        assumeTrue(VanillaRegistryProbe.registryMetadataAvailable(), "native attribute registry requires Minecraft bootstrap");
        EntityTypeBuilder defaultMob = new EntityTypeBuilder(Identifier.parse("nekojs:incomplete_default_mob"));
        defaultMob.attributeSupplier(net.minecraft.world.entity.LivingEntity.createLivingAttributes().build());
        assertThrows(IllegalArgumentException.class, defaultMob::get);
        EntityTypeBuilder opaqueMob = new EntityTypeBuilder(Identifier.parse("nekojs:incomplete_opaque_mob"));
        opaqueMob.factory((type, level) -> null);
        opaqueMob.attributeSupplier(net.minecraft.world.entity.LivingEntity.createLivingAttributes().build());
        assertThrows(IllegalArgumentException.class, opaqueMob::get);
    }

    @Test
    void nativeSpecialAndCommonBaseAttributesSurviveExplicitOverrides() throws Exception {
        assumeTrue(VanillaRegistryProbe.registryMetadataAvailable(), "native attribute registry requires Minecraft bootstrap");
        EntityTypeBuilder builder = new EntityTypeBuilder(Identifier.parse("nekojs:extra_entity_attributes"));
        builder.attributeSupplier(Mob.createMobAttributes().add(Attributes.FLYING_SPEED, 0.73)
                .add(Attributes.MOVEMENT_SPEED, 0.42).add(Attributes.ARMOR, 5).build());
        builder.attributes(attributes -> attributes.maxHealth(40));
        var resolve = EntityTypeBuilder.class.getDeclaredMethod("resolveAttributes");
        resolve.setAccessible(true);
        var supplier = (AttributeSupplier) resolve.invoke(builder);
        assertEquals(0.73, supplier.getBaseValue(Attributes.FLYING_SPEED));
        assertEquals(0.42, supplier.getBaseValue(Attributes.MOVEMENT_SPEED));
        assertEquals(5, supplier.getBaseValue(Attributes.ARMOR));
        assertEquals(40, supplier.getBaseValue(Attributes.MAX_HEALTH));
    }
}
