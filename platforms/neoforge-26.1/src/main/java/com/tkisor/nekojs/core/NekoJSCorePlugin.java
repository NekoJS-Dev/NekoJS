package com.tkisor.nekojs.core;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tkisor.nekojs.api.*;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.NekoCommonManualDeclarations;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.bindings.event.*;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.bindings.static_access.ColorJS;
import com.tkisor.nekojs.bindings.static_access.FluidJS;
import com.tkisor.nekojs.bindings.static_access.FluidIngredientJS;
import com.tkisor.nekojs.bindings.static_access.IDJS;
import com.tkisor.nekojs.bindings.RecipeSchemaBinding;
import com.tkisor.nekojs.bindings.static_access.IngredientFactory;
import com.tkisor.nekojs.bindings.static_access.ItemJS;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.bindings.static_access.StringUtilsJS;
import com.tkisor.nekojs.bindings.static_access.TestJS;
import com.tkisor.nekojs.bindings.static_access.TimeJS;
import com.tkisor.nekojs.bindings.static_access.UUIDJS;
import com.tkisor.nekojs.bindings.static_access.UtilsJS;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.fluid.FluidAmounts;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

@RegisterNekoJSPlugin
public class NekoJSCorePlugin implements NekoJSPlugin {

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        registry.register(NekoJsxLanguagePlugin.INSTANCE);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(PlayerEvents.GROUP);
        registry.register(ServerEvents.GROUP);
        registry.register(BlockEvents.GROUP);
        registry.register(ItemEvents.GROUP);
        registry.register(EntityEvents.GROUP);
        registry.register(GoalEvents.GROUP);
        registry.register(CommandEvents.GROUP);
        registry.register(RegistryEvents.GROUP);
        registry.register(LevelEvents.GROUP);
        registry.register(ScriptEvents.GROUP);
    }

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        registry.register(ClientEvents.GROUP);
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register("Ingredient", new IngredientFactory());
        registry.register("RecipeSchema", new RecipeSchemaBinding());
        registry.register("Fluid", new FluidJS());
        registry.register("FluidIngredient", new FluidIngredientJS());
        registry.register("FluidAmounts", FluidAmounts.class);
        registry.register("Fluids", Fluids.class);
        registry.register("FluidStack", FluidStack.class);
        registry.register("ItemJS", new ItemJS());
        registry.register("ID", new IDJS());
        registry.register("Color", new ColorJS());
        registry.register("Platform", Platform.class);
        registry.register("UUID", new UUIDJS());
        registry.register("StringUtils", new StringUtilsJS());
        registry.register("Time", new TimeJS());
        registry.register("Utils", new UtilsJS());
        registry.register(ScriptType.STARTUP, "NativeEvents", new NativeEventsJS());
        registry.register(ScriptType.TEST, "Test", new TestJS());
        registry.register("TriState", TriState.class);
        registry.register("Network", NetworkJS.class);
        registry.register("ItemStack", ItemStack.class);
        registry.register("Items", Items.class);
        registry.register("Item", Item.class);
        registry.register("BlockPos", BlockPos.class);
        registry.register("Direction", Direction.class);
        registry.register("Vec3", Vec3.class);
        registry.register("AABB", AABB.class);
        registry.register("MutableComponent", MutableComponent.class);
        registry.register("DyeColor", DyeColor.class);
        registry.register("SoundEvents", SoundEvents.class);
        registry.register("ParticleTypes", ParticleTypes.class);
        registry.register("Blocks", Blocks.class);
        registry.register("EntityType", EntityType.class);
        registry.register("CompoundTag", CompoundTag.class);
        registry.register("Identifier", Identifier.class);
        registry.register("MobEffects", MobEffects.class);
        registry.register("MobEffectInstance", MobEffectInstance.class);
        registry.register("DamageTypes", DamageTypes.class);

        if (registry.scriptType() == ScriptType.CLIENT) {
            registry.register(ScriptType.CLIENT, "Minecraft", Minecraft.class);
            registry.register(ScriptType.CLIENT, "Screen", Screen.class);
            registry.register(ScriptType.CLIENT, "Window", Window.class);
            registry.register(ScriptType.CLIENT, "KeyMapping", KeyMapping.class);
            registry.register(ScriptType.CLIENT, "InputConstants", InputConstants.class);
        }
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegister registry) {
        registry.register(new ItemStackAdapter());
        registry.register(new IngredientAdapter());
        registry.register(new SizedIngredientAdapter());
        registry.register(new FluidStackAdapter());
        registry.register(new FluidIngredientAdapter());
        registry.register(new SizedFluidIngredientAdapter());
        registry.register(new IdentifierAdapter());
        registry.register(new RecipeFilterAdapter());
        registry.register(new RecipeJsonValueAdapter());
        registry.register(new JsonObjectAdapter());
        registry.register(new ComponentAdapter());
        registry.register(new EntityTypeAdapter());
        registry.register(new BlockAdapter());
        registry.register(new CompoundTagAdapter());
        registry.register(new TagKeyAdapter());
        registry.register(new ItemAdapter());
    }

    @Override
    public void registerRecipeNamespaces(RecipeNamespaceRegister registry) {
//        registry.registerSchema();
        registry.register(RecipeNamespaceEntry.of("minecraft", MinecraftRecipeHandler::new, MinecraftRecipeHandler.class));
    }

    @Override
    public void registerScriptProperty(ScriptPropertyRegistry registry) {
        registry.register(ScriptProperty.AFTER);
        registry.register(ScriptProperty.MODLOADED);
        registry.register(ScriptProperty.DISABLE);
        registry.register(ScriptProperty.PRIORITY);
    }

    @Override
    public void registerTypeDocs(TypeDocsRegister registry) {
        registry.register(TypeDocCatalogEntry.binding("ItemJS", "NekoItemHelper", "Script-friendly ItemStack factory and helpers.", List.of("ItemJS.of('minecraft:stone')", "ItemJS.empty()")));
        registry.register(TypeDocCatalogEntry.binding("Ingredient", "NekoIngredientHelper", "Script-friendly Ingredient and IngredientJS helper.", List.of("Ingredient.of('minecraft:stone')", "Ingredient.tag('minecraft:planks')")));
        registry.register(TypeDocCatalogEntry.binding("Fluid", "NekoFluidHelper", "Script-friendly FluidStack helper.", List.of("Fluid.of('minecraft:water', FluidAmounts.BUCKET)", "Fluid.of({ fluid: 'minecraft:water', amount: 250 })")));
        registry.register(TypeDocCatalogEntry.binding("FluidIngredient", "NekoFluidIngredientHelper", "Script-friendly FluidIngredient and SizedFluidIngredient helper.", List.of("FluidIngredient.of('minecraft:water')", "FluidIngredient.sized('minecraft:water', 250)")));
        registry.register(TypeDocCatalogEntry.binding("ServerEvents", null, "Server-side event group, including recipe editing.", List.of("ServerEvents.recipes(event => { })", "ServerEvents.afterRecipes(event => { })")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.TEST, "Test", "NekoTestHelper", "Test-script assertion and smoke test helper.", List.of("Test.section('recipes').assertTrue(true, 'ready').summary()")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "NativeEvents", null, "Startup-side native NeoForge event bridge.", List.of("NativeEvents.onEvent('event.class.Name', event => { })")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "ScriptEvents", null, "Startup-side custom server/client event method registration event group.", List.of("ScriptEvents.server(event => event.register('CustomServerEvents', 'playerTick', 'net.neoforged.neoforge.event.tick.PlayerTickEvent.Post'))")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "RegistryEvents", null, "Startup-side registry builders, including scripted entity types.", List.of("RegistryEvents.entityType(event => { })")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "GoalEvents", null, "Startup-side goal registration for existing or scripted entity types.", List.of("GoalEvents.register(event => { })")));

        NekoCommonManualDeclarations.register(registry);
    }
}
