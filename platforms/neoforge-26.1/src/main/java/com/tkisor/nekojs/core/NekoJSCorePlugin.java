package com.tkisor.nekojs.core;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tkisor.nekojs.api.*;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocsRegister;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegister;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingsRegister;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceRegister;
import com.tkisor.nekojs.bindings.event.*;
import com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler;
import com.tkisor.nekojs.bindings.static_access.ColorJS;
import com.tkisor.nekojs.bindings.static_access.FluidJS;
import com.tkisor.nekojs.bindings.static_access.FluidIngredientJS;
import com.tkisor.nekojs.bindings.static_access.IDJS;
import com.tkisor.nekojs.bindings.static_access.IngredientJS;
import com.tkisor.nekojs.bindings.static_access.ItemJS;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.bindings.static_access.StringUtilsJS;
import com.tkisor.nekojs.bindings.static_access.TimeJS;
import com.tkisor.nekojs.bindings.static_access.UUIDJS;
import com.tkisor.nekojs.bindings.static_access.UtilsJS;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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
        registry.register(Binding.of("Fluid", new FluidJS()));
        registry.register(Binding.of("FluidIngredient", new FluidIngredientJS()));
        registry.register(Binding.of("FluidAmounts", FluidAmounts.class));
        registry.register(Binding.of("Fluids", Fluids.class));
        registry.register(Binding.of("FluidStack", FluidStack.class));
        registry.register(Binding.of("ItemJS", new ItemJS()));
        registry.register(Binding.of("ID", new IDJS()));
        registry.register(Binding.of("Color", new ColorJS()));
        registry.register(Binding.of("Platform", Platform.class));
        registry.register(Binding.of("UUID", new UUIDJS()));
        registry.register(Binding.of("StringUtils", new StringUtilsJS()));
        registry.register(Binding.of("Time", new TimeJS()));
        registry.register(Binding.of("Utils", new UtilsJS()));
        registry.register(Binding.of(ScriptType.STARTUP, "NativeEvents", new NativeEventsJS()));
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
        registry.register(TypeDocCatalogEntry.binding("ServerEvents", null, "Server-side event group, including recipe editing.", List.of("ServerEvents.recipes(event => { })")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "NativeEvents", null, "Startup-side native NeoForge event bridge.", List.of("NativeEvents.onEvent('event.class.Name', event => { })")));

        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of("nekojs.recipe-json-value", "type RecipeJsonValue = null | boolean | number | string | JsonObject | JsonArray | IngredientJS | SizedIngredientJS | ItemStack | FluidStack | FluidIngredientJS | SizedFluidIngredient;", "RecipeJsonValue boundary accepted by recipe builder/custom/path APIs.", List.of("builder.property('ingredients', [Ingredient.of('minecraft:stone')])")));
        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of("nekojs.recipe-filter", "type RecipeFilterInput = string | RecipeFilterInput[] | { mod?: string; type?: string; group?: string; id?: string; input?: unknown; output?: unknown; and?: RecipeFilterInput[]; or?: RecipeFilterInput[]; not?: RecipeFilterInput; idStartsWith?: string; idEndsWith?: string; idContains?: string; };", "Recipe filter input accepted by query/remove/replace/dump APIs.", List.of("event.remove({ output: 'minecraft:stick' })")));
        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of("nekojs.ingredient-js", "interface IngredientJS { or(ingredient: IngredientInput): IngredientJS; and(ingredient: IngredientInput): IngredientJS; except(ingredient: IngredientInput): IngredientJS; matches(value: unknown): boolean; first(): ItemStack; stacks(): ItemStack[]; withCount(count: number): SizedIngredientJS; unwrap(): Ingredient; }", "Script wrapper returned by Ingredient helper methods.", List.of("Ingredient.of('minecraft:stone').withCount(3)")));
        registry.registerManualDeclaration(ManualDeclarationCatalogEntry.of("nekojs.recipe-json-builder", "interface RecipeJsonBuilder { id(id: string): RecipeJsonBuilder; property(key: string, value: RecipeJsonValue): RecipeJsonBuilder; setPath(path: string, value: RecipeJsonValue): RecipeJsonBuilder; setPaths(values: Record<string, RecipeJsonValue>): RecipeJsonBuilder; removePath(path: string): RecipeJsonBuilder; removePaths(paths: string[]): RecipeJsonBuilder; validate(): RecipeJsonBuilder; }", "JSON-first recipe builder with RecipeJsonValue boundary.", List.of("event.builder('minecraft:crafting_shapeless').property('ingredients', [Ingredient.of('minecraft:stone')])")));
    }
}