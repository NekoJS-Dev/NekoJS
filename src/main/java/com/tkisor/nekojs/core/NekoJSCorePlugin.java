package com.tkisor.nekojs.core;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tkisor.nekojs.api.*;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.bindings.event.*;
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.bindings.static_access.IngredientJS;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.wrapper.network.NetworkJS;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TriState;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@RegisterNekoJSPlugin
public class NekoJSCorePlugin implements NekoJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(PlayerEvents.GROUP);
        registry.register(ServerEvents.GROUP);
        registry.register(BlockEvents.GROUP);
        registry.register(ItemEvents.GROUP);
        registry.register(EntityEvents.GROUP);
        registry.register(CommandEvents.GROUP);
        registry.register(RegistryEvents.GROUP);
        registry.register(LevelEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingsRegister registry) {
        registry.register(Binding.of("Ingredient", new IngredientJS()));
        registry.register(Binding.of("NativeEvents", new NativeEventsJS()));
        registry.register(Binding.of("TriState", TriState.class));
        registry.register(Binding.of("Network", NetworkJS.class));
        registry.register(Binding.of("ItemStack", ItemStack.class));
        registry.register(Binding.of("Items", Items.class));
        registry.register(Binding.of("Item", Item.class));
        registry.register(Binding.of("BlockPos", BlockPos.class));
        registry.register(Binding.of("Direction", Direction.class));
        registry.register(Binding.of("Vec3", Vec3.class));
        registry.register(Binding.of("AABB", AABB.class));
        registry.register(Binding.of("MutableComponent", MutableComponent.class));
        registry.register(Binding.of("DyeColor", DyeColor.class));
        registry.register(Binding.of("SoundEvents", SoundEvents.class));
        registry.register(Binding.of("ParticleTypes", ParticleTypes.class));
        registry.register(Binding.of("Blocks", Blocks.class));
        registry.register(Binding.of("EntityType", EntityType.class));
        registry.register(Binding.of("CompoundTag", CompoundTag.class));
        registry.register(Binding.of("Identifier", Identifier.class));
        registry.register(Binding.of("MobEffects", MobEffects.class));
        registry.register(Binding.of("MobEffectInstance", MobEffectInstance.class));
        registry.register(Binding.of("DamageTypes", DamageTypes.class));


    }

    @Override
    public void registerClientBindings(BindingsRegister registry) {
        registry.register(Binding.of(ScriptType.CLIENT, "Minecraft", Minecraft.class));
        registry.register(Binding.of(ScriptType.CLIENT, "Screen", Screen.class));
        registry.register(Binding.of(ScriptType.CLIENT, "Window", Window.class));
        registry.register(Binding.of(ScriptType.CLIENT, "KeyMapping", KeyMapping.class));
        registry.register(Binding.of(ScriptType.CLIENT, "InputConstants", InputConstants.class));
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegister registry) {
        registry.register(new ItemStackAdapter());
        registry.register(new IngredientAdapter());
        registry.register(new IdentifierAdapter());
        registry.register(new RecipeFilterAdapter());
        registry.register(new JsonObjectAdapter());
        registry.register(new ComponentAdapter());
        registry.register(new EntityTypeAdapter());
        registry.register(new BlockAdapter());
        registry.register(new CompoundTagTypeAdapter());
        registry.register(new TagKeyAdapter());
        registry.register(new ItemAdapter());
    }

    @Override
    public void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
        registry.register("minecraft", MinecraftRecipeHandler::new);
    }
}