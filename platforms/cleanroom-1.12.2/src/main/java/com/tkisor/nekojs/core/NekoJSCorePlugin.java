package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.*;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.plugin.NekoCommonManualDeclarations;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.data.JSTypeAdapterRegistry;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.core.compiler.NekoJsxLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoTypeScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NodeModuleTypeDocs;
import com.tkisor.nekojs.bindings.static_access.ColorJS;
import com.tkisor.nekojs.bindings.static_access.IngredientFactory;
import com.tkisor.nekojs.bindings.static_access.BlockJS;
import com.tkisor.nekojs.bindings.static_access.ItemJS;
import com.tkisor.nekojs.js.DelegatingBinding;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.bindings.static_access.FluidJS;
import com.tkisor.nekojs.bindings.static_access.FluidIngredientJS;
import com.tkisor.nekojs.bindings.static_access.NativeEventsJS;
import com.tkisor.nekojs.bindings.static_access.StringUtilsJS;
import com.tkisor.nekojs.bindings.static_access.TestJS;
import com.tkisor.nekojs.bindings.static_access.TimeJS;
import com.tkisor.nekojs.bindings.static_access.UUIDJS;
import com.tkisor.nekojs.bindings.static_access.UtilsJS;
import com.tkisor.nekojs.bindings.RecipeSchemaBinding;
import com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister;
import com.tkisor.nekojs.js.type_adapter.*;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.wrapper.FluidAmounts;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Set;

@RegisterNekoJSPlugin(priority = NekoJSPlugin.CORE_PRIORITY)
public class NekoJSCorePlugin implements NekoJSPlugin {

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.register(NekoTypeScriptLanguagePlugin.INSTANCE);
        registry.register(NekoJsxLanguagePlugin.INSTANCE);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(com.tkisor.nekojs.bindings.event.ServerEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.PlayerEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.BlockEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.ItemEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.EntityEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.GoalEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.CommandEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.RegistryEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.LevelEvents.GROUP);
        registry.register(com.tkisor.nekojs.bindings.event.NetworkEvents.GROUP);
        registry.register(ScriptEvents.GROUP);
    }

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        registry.register(com.tkisor.nekojs.bindings.event.client.ClientEvents.GROUP);
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register("Ingredient", new IngredientFactory());
        registry.register("RecipeSchema", new RecipeSchemaBinding());
        registry.register("FluidAmounts", FluidAmounts.class);
        registry.register("FluidStack", FluidStack.class);
        registry.register("Fluids", net.minecraftforge.fluids.FluidRegistry.class);
        registry.register("Color", new ColorJS());
        registry.register("UUID", new UUIDJS());
        registry.register("StringUtils", new StringUtilsJS());
        registry.register("Time", new TimeJS());
        registry.register("Utils", new UtilsJS());
        registry.register(ScriptType.STARTUP, "NativeEvents", new NativeEventsJS());
        registry.register(ScriptType.TEST, "Test", new TestJS());
        registry.register("ItemStack", ItemStack.class);
        registry.register("Items", Items.class);
        ItemJS itemHelper = new ItemJS();
        // 全局 Item 是 ProxyObject 代理委托（of/empty/id/idOf 走 ItemJS，其余委托 MC Item 类）。
        // 代理的动态成员 Java 反射不到，preflight 会误报 "has no member 'of'"，
        // 故用 Binding.of(name, value, valueType) 显式声明 valueType=ItemJS。
        registry.register(Binding.of("Item", new DelegatingBinding(itemHelper, net.minecraft.item.Item.class, Set.of("of", "empty", "id", "idOf")), ItemJS.class));
        registry.register("BlockPos", BlockPos.class);
        registry.register("Direction", EnumFacing.class);
        registry.register("Vec3", Vec3d.class);
        registry.register("AABB", AxisAlignedBB.class);
        registry.register("SoundEvents", SoundEvents.class);
        registry.register("Blocks", Blocks.class);
        registry.register("NBTTagCompound", NBTTagCompound.class);
        registry.register("ResourceLocation", ResourceLocation.class);
        // 全局 Block 同样走代理委托（id/idOf 走 BlockJS，其余委托 MC Block 类）。
        BlockJS blockHelper = new BlockJS();
        registry.register(Binding.of("Block", new DelegatingBinding(blockHelper, Block.class, Set.of("id", "idOf")), BlockJS.class));
        registry.register("EntityEntry", net.minecraftforge.fml.common.registry.EntityEntry.class);
        registry.register("SoundEvent", net.minecraft.util.SoundEvent.class);
        registry.register("Potion", net.minecraft.potion.Potion.class);
        registry.register("MobEffects", net.minecraft.init.MobEffects.class);
        registry.register("Fluid", new FluidJS());
        registry.register("FluidIngredient", new FluidIngredientJS());
        registry.register("PotionEffect", net.minecraft.potion.PotionEffect.class);
        registry.register("TextComponent", net.minecraft.util.text.ITextComponent.class);
        registry.register("DyeColor", net.minecraft.item.EnumDyeColor.class);
        registry.register("ParticleTypes", net.minecraft.util.EnumParticleTypes.class);
        registry.register("Network", com.tkisor.nekojs.wrapper.network.NetworkJS.class);

        if (registry.scriptType() == ScriptType.CLIENT) {
            registry.register("Minecraft", net.minecraft.client.Minecraft.class);
        }
    }

    @Override
    public void registerAdapters(JSTypeAdapterRegistry registry) {
        registry.register(new ItemStackAdapter());
        registry.register(new IngredientAdapter());
        registry.register(new SizedIngredientAdapter());
        registry.register(new FluidStackAdapter());
        // FluidIngredientAdapter intentionally omitted on 1.12.2: there is no FluidIngredient
        // class, so it targeted List.class, which hijacked GraalJS's Value→List mapping and
        // broke every List<X> recipe parameter (and polluted the $List_ probe alias).
        registry.register(new SizedFluidIngredientAdapter());
        registry.register(new ResourceLocationAdapter());
        registry.register(new BlockAdapter());
        registry.register(new CompoundTagAdapter());
        registry.register(new ItemAdapter());
        registry.register(new TagKeyAdapter());
        registry.register(new ComponentAdapter());
        registry.register(new SoundEventAdapter());
        registry.register(new EntityTypeAdapter());
        registry.register(new PotionAdapter());
        registry.register(new RecipeFilterAdapter());
        registry.register(new RecipeJsonValueAdapter());
        registry.register(new TileEntityAdapter());
    }

    @Override
    public void registerRecipeNamespaces(com.tkisor.nekojs.core.plugin.RecipeNamespaceRegister registry) {
        registry.register(new RecipeNamespaceEntry("minecraft",
                event -> new com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler(
                        (com.tkisor.nekojs.wrapper.event.server.RecipeEventJS) event),
                com.tkisor.nekojs.bindings.recipe.MinecraftRecipeHandler.class));
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
        registry.register(TypeDocCatalogEntry.binding("Item", "NekoItemHelper",
                "Script-friendly ItemStack factory and helpers (KubeJS-style: Item.of(id)); delegates of/empty to the helper, rest to MC Item.",
                List.of("Item.of('minecraft:stone')", "Item.of('minecraft:stone', 4)", "Item.empty()")));
        registry.register(TypeDocCatalogEntry.binding("Ingredient", "NekoIngredientHelper",
                "Script-friendly Ingredient helper.",
                List.of("Ingredient.of('minecraft:stone')", "Ingredient.tag('minecraft:planks')")));
        registry.register(TypeDocCatalogEntry.binding("ServerEvents", null,
                "Server-side event group, including recipe editing.",
                List.of("ServerEvents.recipes(event => { })", "ServerEvents.afterRecipes(event => { })")));
        registry.register(TypeDocCatalogEntry.binding(ScriptType.STARTUP, "NativeEvents", null,
                "Startup-side native Forge event bridge.",
                List.of("NativeEvents.onEvent('event.class.Name', event => { })")));
        registry.register(TypeDocCatalogEntry.binding("Fluid", "NekoFluidHelper",
                "Script-friendly FluidStack helper.",
                List.of("Fluid.of('minecraft:water')", "Fluid.water()", "Fluid.of({ fluid: 'minecraft:water', amount: 250 })")));
        registry.register(TypeDocCatalogEntry.binding("FluidIngredient", "NekoFluidIngredientHelper",
                "Script-friendly FluidIngredient helper (resolves to FluidStack lists on 1.12.2).",
                List.of("FluidIngredient.of('minecraft:water')", "FluidIngredient.fluid('minecraft:lava')")));

        NekoCommonManualDeclarations.register(registry);
    }

    @Override
    public void registerNodeTypeDocs(TypeDocsRegister registry) {
        NodeModuleTypeDocs.registerBuiltin(registry);
    }
}
