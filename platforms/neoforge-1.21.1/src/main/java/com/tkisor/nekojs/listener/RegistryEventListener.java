package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.RegistryEvents;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.CreativeTabRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.FluidRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.MobEffectRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ParticleTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.PotionRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.SoundEventRegistryEventJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class RegistryEventListener {
    private RegistryEventListener() {}

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            BlockRegistryEventJS eventJS = new BlockRegistryEventJS(event);
            RegistryEvents.BLOCK.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            EntityTypeRegistryEventJS eventJS = new EntityTypeRegistryEventJS(event);
            RegistryEvents.ENTITY_TYPE.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            ItemRegistryEventJS eventJS = new ItemRegistryEventJS(event);
            RegistryEvents.ITEM.post(eventJS);
            eventJS.registerAll();

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.forEach((location, block) -> {
                event.register(Registries.ITEM, location, () -> {
                    Item.Properties props = new Item.Properties();
                    return new BlockItem(block, props);
                });
            });

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.clear();
        } else if (event.getRegistryKey().equals(NeoForgeRegistries.FLUID_TYPES.key())) {
            // 流体类型与流体本体分属两个 registry；两个分支都 post（create 按 id 覆盖去重）
            RegistryEvents.FLUID.post(new FluidRegistryEventJS());
            FluidRegistryEventJS.registerTypes(event);
        } else if (event.getRegistryKey().equals(Registries.FLUID)) {
            RegistryEvents.FLUID.post(new FluidRegistryEventJS());
            FluidRegistryEventJS.registerFluids(event);
        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            CreativeTabRegistryEventJS eventJS = new CreativeTabRegistryEventJS(event);
            RegistryEvents.CREATIVE_MODE_TAB.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            SoundEventRegistryEventJS eventJS = new SoundEventRegistryEventJS(event);
            RegistryEvents.SOUND_EVENT.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.MOB_EFFECT)) {
            MobEffectRegistryEventJS eventJS = new MobEffectRegistryEventJS(event);
            RegistryEvents.MOB_EFFECT.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.POTION)) {
            PotionRegistryEventJS eventJS = new PotionRegistryEventJS(event);
            RegistryEvents.POTION.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.PARTICLE_TYPE)) {
            ParticleTypeRegistryEventJS eventJS = new ParticleTypeRegistryEventJS(event);
            RegistryEvents.PARTICLE_TYPE.post(eventJS);
            eventJS.registerAll();
        }
    }

    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        EntityTypeRegistryEventJS.registerAttributes(event);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        GoalRegistry.onEntityJoinLevel(event);
    }
}
