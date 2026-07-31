package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.CreativeTabRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.FluidRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.MobEffectRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.PaintingVariantRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ParticleTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.PotionRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.SoundEventRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.VillagerTypeRegistryEventJS;

public interface RegistryEvents {
    EventGroup GROUP = EventGroup.of("RegistryEvents");

    EventBusJS<ItemRegistryEventJS, Void> ITEM =
            GROUP.startup("item", ItemRegistryEventJS.class);

    EventBusJS<BlockRegistryEventJS, Void> BLOCK = GROUP.startup("block", BlockRegistryEventJS.class);

    EventBusJS<EntityTypeRegistryEventJS, Void> ENTITY_TYPE = GROUP.startup("entityType", EntityTypeRegistryEventJS.class);

    EventBusJS<FluidRegistryEventJS, Void> FLUID = GROUP.startup("fluid", FluidRegistryEventJS.class);

    EventBusJS<CreativeTabRegistryEventJS, Void> CREATIVE_MODE_TAB =
            GROUP.startup("creativeModeTab", CreativeTabRegistryEventJS.class);

    EventBusJS<SoundEventRegistryEventJS, Void> SOUND_EVENT =
            GROUP.startup("soundEvent", SoundEventRegistryEventJS.class);

    EventBusJS<MobEffectRegistryEventJS, Void> MOB_EFFECT =
            GROUP.startup("mobEffect", MobEffectRegistryEventJS.class);

    EventBusJS<PotionRegistryEventJS, Void> POTION =
            GROUP.startup("potion", PotionRegistryEventJS.class);

    EventBusJS<ParticleTypeRegistryEventJS, Void> PARTICLE_TYPE =
            GROUP.startup("particleType", ParticleTypeRegistryEventJS.class);

    EventBusJS<PaintingVariantRegistryEventJS, Void> PAINTING_VARIANT =
            GROUP.startup("paintingVariant", PaintingVariantRegistryEventJS.class);

    EventBusJS<VillagerTypeRegistryEventJS, Void> VILLAGER_TYPE =
            GROUP.startup("villagerType", VillagerTypeRegistryEventJS.class);
}
