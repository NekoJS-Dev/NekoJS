package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.RegistryEvents;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.event.registry.BlockRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.CreativeTabRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EnchantmentRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.EntityTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.FluidRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ItemRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.MobEffectRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.ParticleTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.PaintingVariantRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.PotionRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.SoundEventRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.registry.VillagerTypeRegistryEventJS;
import com.tkisor.nekojs.wrapper.registry.BlockBuilderJS;
import com.tkisor.nekojs.wrapper.registry.EntityTypeBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
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
            // 流体方块（BLOCK 在 FLUID 之后、ITEM 之前）
            FluidRegistryEventJS.registerBlocks(event);
        } else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            EntityTypeRegistryEventJS eventJS = new EntityTypeRegistryEventJS(event);
            RegistryEvents.ENTITY_TYPE.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            ItemRegistryEventJS eventJS = new ItemRegistryEventJS(event);
            RegistryEvents.ITEM.post(eventJS);
            eventJS.registerAll();

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.forEach((location, builder) -> {
                event.register(Registries.ITEM, location, () -> {
                    Block block = builder.getCreatedBlock();
                    Item.Properties props = builder.getItemBuilder() != null
                            ? builder.getItemBuilder().buildProperties()
                            : new Item.Properties();
                    return new BlockItem(block, props);
                });
            });

            BlockRegistryEventJS.PENDING_BLOCK_ITEMS.clear();

            // 流体桶（ITEM 在 BLOCK 之后；registerItems 末尾清理所有跨 pass 状态）
            FluidRegistryEventJS.registerItems(event);

            // 生物蛋（ENTITY_TYPE 先于 ITEM 触发；1.21.1 四参构造器带颜色，模型 tint 染色）
            EntityTypeRegistryEventJS.PENDING_SPAWN_EGGS.forEach((location, builder) -> {
                EntityType<? extends LivingEntity> type = EntityTypeRegistryEventJS.getEntityType(location);
                if (type == null) {
                    return;
                }
                ResourceLocation eggLocation = ResourceLocation.fromNamespaceAndPath(
                        location.getNamespace(), location.getPath() + "_spawn_egg");
                event.register(Registries.ITEM, eggLocation, () -> createSpawnEgg(type, builder));
            });
            EntityTypeRegistryEventJS.PENDING_SPAWN_EGGS.clear();
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
        } else if (event.getRegistryKey().equals(Registries.PAINTING_VARIANT)) {
            PaintingVariantRegistryEventJS eventJS = new PaintingVariantRegistryEventJS(event);
            RegistryEvents.PAINTING_VARIANT.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.VILLAGER_TYPE)) {
            VillagerTypeRegistryEventJS eventJS = new VillagerTypeRegistryEventJS(event);
            RegistryEvents.VILLAGER_TYPE.post(eventJS);
            eventJS.registerAll();
        } else if (event.getRegistryKey().equals(Registries.ENCHANTMENT)) {
            EnchantmentRegistryEventJS eventJS = new EnchantmentRegistryEventJS(event);
            RegistryEvents.ENCHANTMENT.post(eventJS);
            eventJS.registerAll();
        }
    }

    /** 1.21.1：四参构造器（type + 双色）。NekoScriptMob 是 Mob 子类，unchecked 转换安全。 */
    @SuppressWarnings("unchecked")
    private static Item createSpawnEgg(EntityType<? extends LivingEntity> type, EntityTypeBuilderJS builder) {
        return new SpawnEggItem((EntityType<? extends Mob>) (EntityType<?>) type,
                builder.getSpawnEggBackgroundColor(), builder.getSpawnEggHighlightColor(), new Item.Properties());
    }

    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        EntityTypeRegistryEventJS.registerAttributes(event);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        GoalRegistry.onEntityJoinLevel(event);
    }

    /**
     * 创造标签页内容构建：把 {@link ItemRegistryEventJS#GROUP_ASSIGNMENTS} 里分配到当前
     * 标签页的物品追加进去（脚本通过 ItemBuilderJS.group(tabId) 分配）。
     * 在所有注册完成之后触发，故可安全从 {@link net.minecraft.core.registries.BuiltInRegistries#ITEM} 解析。
     */
    public static void onBuildCreativeTabContents(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        var tabId = event.getTabKey().location();
        ItemRegistryEventJS.GROUP_ASSIGNMENTS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(tabId))
                .forEach(entry -> {
                    Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.getKey());
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        event.accept(new net.minecraft.world.item.ItemStack(item),
                                net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    }
                });
    }
}
