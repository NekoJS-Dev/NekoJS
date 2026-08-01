package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.CreativeTabRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EnchantmentRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.FluidRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.MobEffectRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.PotionRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.SoundEventRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.VillagerTypeRegistryEventJS;

/**
 * 1.12.2 RegistryEvents.
 *
 * <p>Each bus carries a script-facing wrapper EventJS (e.g. {@link BlockRegistryEventJS})
 * rather than the raw Forge {@code RegistryEvent.Register}. These custom events are plain
 * Java objects, NOT Forge events, so they must NOT be routed through
 * {@code EventBusForgeBridge}; instead {@code RegistryEventListener} manually constructs
 * the wrapper, posts it to the bus, and then invokes {@code registerAll()} to flush the
 * builders into the Forge registry.
 *
 * <p>1.12.2 支持：item / block / entityType / fluid / creativeModeTab / enchantment /
 * mobEffect / potion / soundEvent / villagerType。particleType 与 paintingVariant
 * 在 1.12.2 无注册表（EnumParticleTypes/EnumArt 均为 final 枚举），不支持。
 */
public interface RegistryEvents {
    EventGroup GROUP = EventGroup.of("RegistryEvents");

    EventBusJS<BlockRegistryEventJS, Void> BLOCK =
            GROUP.startup("block", BlockRegistryEventJS.class);

    EventBusJS<ItemRegistryEventJS, Void> ITEM =
            GROUP.startup("item", ItemRegistryEventJS.class);

    EventBusJS<EntityTypeRegistryEventJS, Void> ENTITY_TYPE =
            GROUP.startup("entityType", EntityTypeRegistryEventJS.class);

    EventBusJS<FluidRegistryEventJS, Void> FLUID =
            GROUP.startup("fluid", FluidRegistryEventJS.class);

    EventBusJS<CreativeTabRegistryEventJS, Void> CREATIVE_MODE_TAB =
            GROUP.startup("creativeModeTab", CreativeTabRegistryEventJS.class);

    EventBusJS<EnchantmentRegistryEventJS, Void> ENCHANTMENT =
            GROUP.startup("enchantment", EnchantmentRegistryEventJS.class);

    EventBusJS<MobEffectRegistryEventJS, Void> MOB_EFFECT =
            GROUP.startup("mobEffect", MobEffectRegistryEventJS.class);

    EventBusJS<PotionRegistryEventJS, Void> POTION =
            GROUP.startup("potion", PotionRegistryEventJS.class);

    EventBusJS<SoundEventRegistryEventJS, Void> SOUND_EVENT =
            GROUP.startup("soundEvent", SoundEventRegistryEventJS.class);

    EventBusJS<VillagerTypeRegistryEventJS, Void> VILLAGER_TYPE =
            GROUP.startup("villagerType", VillagerTypeRegistryEventJS.class);
}
