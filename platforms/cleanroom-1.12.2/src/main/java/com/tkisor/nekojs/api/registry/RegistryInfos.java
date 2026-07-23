package com.tkisor.nekojs.api.registry;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 1.12.2 RegistryInfos - pre-built RegistryInfo instances for commonly used registries.
 */
@SuppressWarnings("rawtypes")
public final class RegistryInfos {
    private RegistryInfos() {}

    public static final RegistryInfo<Item> ITEM = new RegistryInfo<>(Item.class, ForgeRegistries.ITEMS, new ResourceLocation("item"));
    public static final RegistryInfo<Block> BLOCK = new RegistryInfo<>(Block.class, ForgeRegistries.BLOCKS, new ResourceLocation("block"));
    public static final RegistryInfo<EntityEntry> ENTITY = new RegistryInfo<>(EntityEntry.class, ForgeRegistries.ENTITIES, new ResourceLocation("entity"));
    public static final RegistryInfo<SoundEvent> SOUND_EVENT = new RegistryInfo<>(SoundEvent.class, ForgeRegistries.SOUND_EVENTS, new ResourceLocation("sound_event"));
    public static final RegistryInfo<Potion> POTION = new RegistryInfo<>(Potion.class, ForgeRegistries.POTIONS, new ResourceLocation("potion"));
    public static final RegistryInfo<Enchantment> ENCHANTMENT = new RegistryInfo<>(Enchantment.class, ForgeRegistries.ENCHANTMENTS, new ResourceLocation("enchantment"));
    public static final RegistryInfo<Biome> BIOME = new RegistryInfo<>(Biome.class, ForgeRegistries.BIOMES, new ResourceLocation("biome"));
}
