package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.definition.LegacyRecipeSchemaScanner;
import com.tkisor.nekojs.api.recipe.definition.RecipeSchemaAutoDiscovery;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.bindings.event.RegistryEvents;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
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
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

import graal.graalvm.polyglot.PolyglotException;

/**
 * Bridges Forge 1.12.2 registry events to the script-facing EventJS wrappers.
 *
 * <p>The wrapper events (e.g. {@link BlockRegistryEventJS}) are NOT Forge events; they are
 * plain Java objects posted onto the {@link RegistryEvents} startup buses. This listener is
 * the only place that:
 * <ol>
 *   <li>constructs the wrapper from the raw Forge {@link RegistryEvent.Register},</li>
 *   <li>posts it to its bus so scripts populate builders, then</li>
 *   <li>invokes {@code registerAll()} to flush those builders into the Forge registry.</li>
 * </ol>
 * Order matters: in 1.12.2, {@code Register<Block>} fires before {@code Register<Item>},
 * which is what lets {@code BlockRegistryEventJS.PENDING_BLOCK_ITEMS} stage item generation
 * for {@code ItemRegistryEventJS} to complete.
 */
public class RegistryEventListener {

    @SubscribeEvent
    public static void onRegister(RegistryEvent.Register<?> event) {
        Class<?> type = event.getRegistry().getRegistrySuperType();
        if (type == Block.class) {
            // 流体与创造标签页先注册：1.12.2 的 FluidRegistry 是静态注册表（无 Register<Fluid> 事件）、
            // CreativeTabs 无 registry 事件（实例构造即加入静态数组）；
            // BLOCK 分支是最早的注册事件（Register<Block> 先于 Register<Item>）
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<Block> rawBlock = (RegistryEvent.Register<Block>) event;
            RegistryEvents.FLUID.post(new FluidRegistryEventJS());
            FluidRegistryEventJS.registerAll(rawBlock);

            CreativeTabRegistryEventJS tabJS = new CreativeTabRegistryEventJS();
            RegistryEvents.CREATIVE_MODE_TAB.post(tabJS);
            tabJS.registerAll();

            @SuppressWarnings("unchecked")
            RegistryEvent.Register<Block> raw = (RegistryEvent.Register<Block>) event;
            BlockRegistryEventJS js = new BlockRegistryEventJS(raw);
            RegistryEvents.BLOCK.post(js);
            js.registerAll();
        } else if (type == Item.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<Item> raw = (RegistryEvent.Register<Item>) event;
            ItemRegistryEventJS js = new ItemRegistryEventJS(raw);
            RegistryEvents.ITEM.post(js);
            js.registerAll();
            // 流体桶（须在 ItemRegistryEventJS 之后；末尾清理跨分支状态）
            FluidRegistryEventJS.registerBucketItems(raw);
        } else if (type == EntityEntry.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<EntityEntry> raw = (RegistryEvent.Register<EntityEntry>) event;
            EntityTypeRegistryEventJS js = new EntityTypeRegistryEventJS(raw);
            RegistryEvents.ENTITY_TYPE.post(js);
            js.registerAll();
        } else if (type == net.minecraft.enchantment.Enchantment.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<net.minecraft.enchantment.Enchantment> raw =
                    (RegistryEvent.Register<net.minecraft.enchantment.Enchantment>) event;
            EnchantmentRegistryEventJS js = new EnchantmentRegistryEventJS(raw);
            RegistryEvents.ENCHANTMENT.post(js);
            js.registerAll();
        } else if (type == net.minecraft.potion.Potion.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<net.minecraft.potion.Potion> raw =
                    (RegistryEvent.Register<net.minecraft.potion.Potion>) event;
            MobEffectRegistryEventJS js = new MobEffectRegistryEventJS(raw);
            RegistryEvents.MOB_EFFECT.post(js);
            js.registerAll();
        } else if (type == net.minecraft.potion.PotionType.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<net.minecraft.potion.PotionType> raw =
                    (RegistryEvent.Register<net.minecraft.potion.PotionType>) event;
            PotionRegistryEventJS js = new PotionRegistryEventJS(raw);
            RegistryEvents.POTION.post(js);
            js.registerAll();
        } else if (type == net.minecraft.util.SoundEvent.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<net.minecraft.util.SoundEvent> raw =
                    (RegistryEvent.Register<net.minecraft.util.SoundEvent>) event;
            SoundEventRegistryEventJS js = new SoundEventRegistryEventJS(raw);
            RegistryEvents.SOUND_EVENT.post(js);
            js.registerAll();
        } else if (type == net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession.class) {
            @SuppressWarnings("unchecked")
            RegistryEvent.Register<net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession> raw =
                    (RegistryEvent.Register<net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession>) event;
            VillagerTypeRegistryEventJS js = new VillagerTypeRegistryEventJS(raw);
            RegistryEvents.VILLAGER_TYPE.post(js);
            js.registerAll();
        }
    }

    /** Guards against double application (RegistryEvent + postInit fallback). */
    private static final java.util.concurrent.atomic.AtomicBoolean RECIPE_SCRIPTS_APPLIED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** 自动配方 schema 只扫一次（注册表内容在 postInit 已完整且 reload 不变；nekojs 自身配方已排除）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean SCHEMA_SCANNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void scanRecipeSchemasOnce() {
        if (!SCHEMA_SCANNED.compareAndSet(false, true)) return;
        try {
            RecipeTypeDefinitionStorage.setAutoDiscovered(
                    RecipeSchemaAutoDiscovery.discover(LegacyRecipeSchemaScanner::scan));
            ScriptType.SERVER.logger().info("Recipe schemas auto-discovered.");
        } catch (Throwable e) {
            SCHEMA_SCANNED.set(false); // 扫描失败允许下次重试
            ScriptType.SERVER.logger().error("Recipe schema auto-discovery failed", e);
        }
    }

    /**
     * Run recipe scripts once. On Cleanroom 1.12.2 the IRecipe registry is populated very early
     * (before nekojs preInit registers this listener), so {@link #onRegisterRecipe} typically
     * misses {@code RegistryEvent.Register<IRecipe>}. The reliable trigger is therefore
     * {@code NekoJSMod.postInit} (registry still unfrozen — freezes at LoadComplete, per CraftTweaker).
     * Idempotent via {@link #RECIPE_SCRIPTS_APPLIED}.
     *
     * <p>Scripts triggered by {@code RegistryEvent.Register<IRecipe>} run at LOWEST priority,
     * so vanilla + mod recipes are already registered and the id snapshot is complete.
     *
     * <p>1.12.2 recipes are buildtime {@link IRecipe} objects: the registry freezes after load,
     * so script-defined recipes must be registered while it is still open — the CraftTweaker
     * model. {@code MinecraftRecipeHandler.shaped/shapeless} then call
     * {@code ForgeRegistries.RECIPES.register(...)} successfully.
     *
     * <p>Recipe handler methods stay auto-discovered: {@code collectHandlerMethods} reflects
     * the handler class, and {@code RecipeRegistryProxy.getMember} returns the handler for
     * GraalJS to reflect on its own.
     */
    public static void applyRecipeScripts() {
        if (!RECIPE_SCRIPTS_APPLIED.compareAndSet(false, true)) return;
        scanRecipeSchemasOnce();
        ScriptType.SERVER.logger().info("Applying recipe scripts...");

        List<String> recipeIds = new ArrayList<>();
        for (ResourceLocation id : CraftingManager.REGISTRY.getKeys()) {
            recipeIds.add(id.toString());
        }
        RecipeEventJS recipeEvent = new RecipeEventJS(recipeIds);
        try {
            NekoRuntimeAccess.get().beforeRecipeLoading(recipeEvent);
            ServerEvents.RECIPES.post(recipeEvent);
            ServerEvents.AFTER_RECIPES.post(recipeEvent);
            NekoRuntimeAccess.get().afterRecipes(recipeEvent);
            recipeEvent.flushPendingRecipeBuilders();
        } catch (PolyglotException e) {
            if (NekoJSMod.RUNTIME_ROOT != null) {
                NekoJSMod.RUNTIME_ROOT.errorTracker().recordEventError(ScriptType.SERVER, e);
            } else {
                ScriptType.SERVER.logger().error("Recipe script execution crashed", e);
            }
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Recipe script execution crashed", e);
        }
        ScriptType.SERVER.logger().info("Recipe scripts applied, total recipes: {}", recipeIds.size());
    }

    /**
     * Hot-reload recipe scripts at runtime — called after {@code /nekojs reload server}
     * (which has just re-executed SERVER scripts and registered fresh recipe listeners).
     *
     * <p>1.12.2 recipe registry is frozen after LoadComplete, so we:
     * <ol>
     *   <li>{@code unfreeze()} the registry (public on {@code ForgeRegistry}, reached via cast),</li>
     *   <li>remove every previously-registered {@code nekojs:*} recipe,</li>
     *   <li>reset {@link #RECIPE_SCRIPTS_APPLIED} so {@link #applyRecipeScripts()} re-posts
     *       the recipe event (firing the new listeners),</li>
     *   <li>{@code freeze()} again in a {@code finally} so the registry is never left open.</li>
     * </ol>
     * {@code CraftingManager.findMatchingRecipe} iterates the live REGISTRY with no cache,
     * and single-player shares one JVM registry between client and integrated server, so
     * the new recipes are effective immediately in the crafting table.
     *
     * <p>No DummyRecipe / backup machinery (unlike GroovyScript): NekoJS scripts only ever
     * register their own {@code nekojs:*} recipes, so remove-and-reregister is enough. id
     * holes left in the availability map are harmless (recipe count is small).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public synchronized static void reloadRecipes() {
        net.minecraftforge.registries.ForgeRegistry reg;
        try {
            reg = (net.minecraftforge.registries.ForgeRegistry) ForgeRegistries.RECIPES;
        } catch (ClassCastException e) {
            ScriptType.SERVER.logger().error("Cannot cast ForgeRegistries.RECIPES to ForgeRegistry — recipe reload aborted", e);
            return;
        }

        ScriptType.SERVER.logger().info("Hot-reloading recipe scripts...");
        reg.unfreeze();
        try {
            // Remove every previously-registered nekojs recipe so ids don't collide on re-register.
            List<ResourceLocation> nekoIds = new ArrayList<>();
            for (ResourceLocation id : CraftingManager.REGISTRY.getKeys()) {
                if ("nekojs".equals(id.getNamespace())) nekoIds.add(id);
            }
            for (ResourceLocation id : nekoIds) {
                reg.remove(id);
            }
            ScriptType.SERVER.logger().info("Removed {} nekojs recipes before re-run", nekoIds.size());

            // Reset the guard so applyRecipeScripts() re-posts the recipe event. Listeners
            // were already refreshed by reload(SERVER) (which clears all old listeners).
            RECIPE_SCRIPTS_APPLIED.set(false);
            applyRecipeScripts();
        } catch (Throwable t) {
            ScriptType.SERVER.logger().error("Recipe hot-reload failed", t);
        } finally {
            reg.freeze();
        }
        // Rebuild JEI/HEI's display cache on the client render thread. The live registry
        // already holds the new recipes (single-player shares one JVM between client and
        // integrated server), but HEI caches them for its recipe panel — without this the
        // UI stays stale even though the crafting table already works. Skipped on a
        // dedicated server (no client thread).
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
            try {
                com.tkisor.nekojs.client.HeiRefresher.scheduleRefresh(true);
            } catch (Throwable t) {
                ScriptType.SERVER.logger().warn("Failed to schedule JEI/HEI refresh", t);
            }
        }
        ScriptType.SERVER.logger().info("Recipe hot-reload complete");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterRecipe(RegistryEvent.Register<?> event) {
        if (event.getRegistry().getRegistrySuperType() != IRecipe.class) return;
        ScriptType.SERVER.logger().info("RegistryEvent.Register<IRecipe> received — applying recipe scripts early");
        applyRecipeScripts();
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        GoalRegistry.onEntityJoinWorld(event);
    }
}
