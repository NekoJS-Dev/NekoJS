import { $KeyBindEvents$KeyBindEventJS } from "java:com/tkisor/nekojs/bindings/event/client";
import { $PostEffectEventJS } from "java:com/tkisor/nekojs/client/posteffect";
import { $BlockEntityTickEvent, $RandomTickEvent } from "java:com/tkisor/nekojs/event/level";
import { $DataGeneratorJS, $LangGeneratorJS } from "java:com/tkisor/nekojs/wrapper";
import { $PainterJS, $ScreenRenderEventJS } from "java:com/tkisor/nekojs/wrapper/client";
import { $BlockBrokenEventJS } from "java:com/tkisor/nekojs/wrapper/event/block";
import { $GoalRegisterEventJS } from "java:com/tkisor/nekojs/wrapper/event/entity";
import { $InventoryChangedEventJS } from "java:com/tkisor/nekojs/wrapper/event/player";
import { $CapabilityRegistryEventJS } from "java:com/tkisor/nekojs/wrapper/event/registry";
import { $BlockModificationEventJS, $ItemModificationEventJS, $LootTableEventJS, $RecipeEventJS, $TagEventJS } from "java:com/tkisor/nekojs/wrapper/event/server";
import { $RecipeViewerCategoryListJS, $RecipeViewerEntryListJS, $RecipeViewerInformationJS, $RecipeViewerRecipeListJS } from "java:com/tkisor/nekojs/wrapper/viewer";
import { $String } from "java:java/lang";
import { $Identifier } from "java:net/minecraft/resources";
import { $EntityType } from "java:net/minecraft/world/entity";
import { $Item } from "java:net/minecraft/world/item";
import { $Block } from "java:net/minecraft/world/level/block";
import { $BlockEntityType } from "java:net/minecraft/world/level/block/entity";
import { $ClientPlayerNetworkEvent$Clone, $ClientPlayerNetworkEvent$LoggingIn, $ClientPlayerNetworkEvent$LoggingOut, $ClientTickEvent$Post, $ClientTickEvent$Pre, $EntityRenderersEvent$RegisterRenderers, $InputEvent$InteractionKeyMappingTriggered, $RegisterClientCommandsEvent, $RegisterKeyMappingsEvent, $RegisterMenuScreensEvent, $RegisterParticleProvidersEvent } from "java:net/neoforged/neoforge/client/event";
import { $CommandEvent, $LootTableLoadEvent, $OnDatapackSyncEvent, $RegisterCommandsEvent, $ServerChatEvent, $TagsUpdatedEvent } from "java:net/neoforged/neoforge/event";
import { $EntityJoinLevelEvent, $EntityLeaveLevelEvent } from "java:net/neoforged/neoforge/event/entity";
import { $ItemTossEvent } from "java:net/neoforged/neoforge/event/entity/item";
import { $FinalizeSpawnEvent, $LivingDamageEvent$Post, $LivingDamageEvent$Pre, $LivingDeathEvent, $LivingDropsEvent, $LivingEntityUseItemEvent$Finish, $LivingEntityUseItemEvent$Start, $LivingEntityUseItemEvent$Stop, $LivingEntityUseItemEvent$Tick } from "java:net/neoforged/neoforge/event/entity/living";
import { $AdvancementEvent$AdvancementEarnEvent, $ItemEntityPickupEvent$Post, $ItemEntityPickupEvent$Pre, $ItemTooltipEvent, $PlayerContainerEvent$Close, $PlayerContainerEvent$Open, $PlayerDestroyItemEvent, $PlayerEvent$Clone, $PlayerEvent$ItemCraftedEvent, $PlayerEvent$ItemSmeltedEvent, $PlayerEvent$PlayerChangedDimensionEvent, $PlayerEvent$PlayerLoggedInEvent, $PlayerEvent$PlayerLoggedOutEvent, $PlayerEvent$PlayerRespawnEvent, $PlayerInteractEvent$EntityInteract, $PlayerInteractEvent$LeftClickBlock, $PlayerInteractEvent$RightClickBlock, $PlayerInteractEvent$RightClickItem } from "java:net/neoforged/neoforge/event/entity/player";
import { $BlockEvent$BlockToolModificationEvent, $BlockEvent$EntityMultiPlaceEvent, $BlockEvent$EntityPlaceEvent, $BlockEvent$FarmlandTrampleEvent, $BlockEvent$FluidPlaceBlockEvent, $BlockEvent$NeighborNotifyEvent, $BlockEvent$PortalSpawnEvent, $ExplosionEvent$Detonate, $ExplosionEvent$Start, $LevelEvent$Load, $LevelEvent$Save, $LevelEvent$Unload } from "java:net/neoforged/neoforge/event/level";
import { $ServerAboutToStartEvent, $ServerStartedEvent, $ServerStartingEvent, $ServerStoppedEvent, $ServerStoppingEvent } from "java:net/neoforged/neoforge/event/server";
import { $EntityTickEvent$Post, $EntityTickEvent$Pre, $LevelTickEvent$Post, $LevelTickEvent$Pre, $PlayerTickEvent$Post, $PlayerTickEvent$Pre, $ServerTickEvent$Post, $ServerTickEvent$Pre } from "java:net/neoforged/neoforge/event/tick";

declare module "@side-only/startup/events" {
}

export {};

declare global {
    namespace BlockEvents {
        function broken(handler: ((event: $BlockBrokenEventJS) => void)): void;
        function broken(extra: $Block, handler: ((event: $BlockBrokenEventJS) => void)): void;
        function modification(handler: ((event: $BlockModificationEventJS) => void)): void;
        function entityPlaced(handler: ((event: $BlockEvent$EntityPlaceEvent) => void)): void;
        function entityPlaced(extra: $Block, handler: ((event: $BlockEvent$EntityPlaceEvent) => void)): void;
        function entityMultiPlaced(handler: ((event: $BlockEvent$EntityMultiPlaceEvent) => void)): void;
        function entityMultiPlaced(extra: $Block, handler: ((event: $BlockEvent$EntityMultiPlaceEvent) => void)): void;
        function neighborNotify(handler: ((event: $BlockEvent$NeighborNotifyEvent) => void)): void;
        function neighborNotify(extra: $Block, handler: ((event: $BlockEvent$NeighborNotifyEvent) => void)): void;
        function fluidPlaced(handler: ((event: $BlockEvent$FluidPlaceBlockEvent) => void)): void;
        function fluidPlaced(extra: $Block, handler: ((event: $BlockEvent$FluidPlaceBlockEvent) => void)): void;
        function farmlandTrample(handler: ((event: $BlockEvent$FarmlandTrampleEvent) => void)): void;
        function farmlandTrample(extra: $Block, handler: ((event: $BlockEvent$FarmlandTrampleEvent) => void)): void;
        function portalSpawn(handler: ((event: $BlockEvent$PortalSpawnEvent) => void)): void;
        function portalSpawn(extra: $Block, handler: ((event: $BlockEvent$PortalSpawnEvent) => void)): void;
        function toolModification(handler: ((event: $BlockEvent$BlockToolModificationEvent) => void)): void;
        function toolModification(extra: $Block, handler: ((event: $BlockEvent$BlockToolModificationEvent) => void)): void;
        function rightClicked(handler: ((event: $PlayerInteractEvent$RightClickBlock) => void)): void;
        function rightClicked(extra: $Block, handler: ((event: $PlayerInteractEvent$RightClickBlock) => void)): void;
        function placed(handler: ((event: $BlockEvent$EntityPlaceEvent) => void)): void;
        function placed(extra: $Block, handler: ((event: $BlockEvent$EntityPlaceEvent) => void)): void;
        function leftClicked(handler: ((event: $PlayerInteractEvent$LeftClickBlock) => void)): void;
        function leftClicked(extra: $Block, handler: ((event: $PlayerInteractEvent$LeftClickBlock) => void)): void;
        function randomTick(handler: ((event: $RandomTickEvent) => void)): void;
        function randomTick(extra: $Block, handler: ((event: $RandomTickEvent) => void)): void;
        function blockEntityTick(handler: ((event: $BlockEntityTickEvent) => void)): void;
        function blockEntityTick(extra: $BlockEntityType, handler: ((event: $BlockEntityTickEvent) => void)): void;
    }

    namespace CapabilityEvents {
        function register(handler: ((event: $CapabilityRegistryEventJS) => void)): void;
    }

    namespace ClientEvents {
        function tickPre(handler: ((event: $ClientTickEvent$Pre) => void)): void;
        function tickPost(handler: ((event: $ClientTickEvent$Post) => void)): void;
        function tick(handler: ((event: $ClientTickEvent$Post) => void)): void;
        function playerTickPre(handler: ((event: $PlayerTickEvent$Pre) => void)): void;
        function playerTickPost(handler: ((event: $PlayerTickEvent$Post) => void)): void;
        function interactionKey(handler: ((event: $InputEvent$InteractionKeyMappingTriggered) => void)): void;
        function loggedIn(handler: ((event: $ClientPlayerNetworkEvent$LoggingIn) => void)): void;
        function loggedOut(handler: ((event: $ClientPlayerNetworkEvent$LoggingOut) => void)): void;
        function cloned(handler: ((event: $ClientPlayerNetworkEvent$Clone) => void)): void;
        function commandRegistry(handler: ((event: $RegisterClientCommandsEvent) => void)): void;
        function registerKeyMappings(handler: ((event: $RegisterKeyMappingsEvent) => void)): void;
        function registerMenuScreens(handler: ((event: $RegisterMenuScreensEvent) => void)): void;
        function registerRenderers(handler: ((event: $EntityRenderersEvent$RegisterRenderers) => void)): void;
        function registerEntityRenderers(handler: ((event: $EntityRenderersEvent$RegisterRenderers) => void)): void;
        function registerBlockEntityRenderers(handler: ((event: $EntityRenderersEvent$RegisterRenderers) => void)): void;
        function registerParticleProviders(handler: ((event: $RegisterParticleProvidersEvent) => void)): void;
        function generateAssets(handler: ((event: $DataGeneratorJS) => void)): void;
        function generateAssets(extra: string, handler: ((event: $DataGeneratorJS) => void)): void;
        function lang(handler: ((event: $LangGeneratorJS) => void)): void;
        function lang(extra: string, handler: ((event: $LangGeneratorJS) => void)): void;
        function postEffects(handler: ((event: $PostEffectEventJS) => void)): void;
        function hud(handler: ((event: $PainterJS) => void)): void;
        function screenRender(handler: ((event: $ScreenRenderEventJS) => void)): void;
        function hudRender(handler: ((event: $Object) => void)): void;
        function worldRender(handler: ((event: $Object) => void)): void;
    }

    namespace CommandEvents {
        function register(handler: ((event: $RegisterCommandsEvent) => void)): void;
        function command(handler: ((event: $CommandEvent) => void)): void;
    }

    namespace EntityEvents {
        function damagePre(handler: ((event: $LivingDamageEvent$Pre) => void)): void;
        function damagePre(extra: $EntityType, handler: ((event: $LivingDamageEvent$Pre) => void)): void;
        function damagePost(handler: ((event: $LivingDamageEvent$Post) => void)): void;
        function damagePost(extra: $EntityType, handler: ((event: $LivingDamageEvent$Post) => void)): void;
        function death(handler: ((event: $LivingDeathEvent) => void)): void;
        function death(extra: $EntityType, handler: ((event: $LivingDeathEvent) => void)): void;
        function drops(handler: ((event: $LivingDropsEvent) => void)): void;
        function drops(extra: $EntityType, handler: ((event: $LivingDropsEvent) => void)): void;
        function finalizeSpawn(handler: ((event: $FinalizeSpawnEvent) => void)): void;
        function finalizeSpawn(extra: $EntityType, handler: ((event: $FinalizeSpawnEvent) => void)): void;
        function tickPre(handler: ((event: $EntityTickEvent$Pre) => void)): void;
        function tickPre(extra: $EntityType, handler: ((event: $EntityTickEvent$Pre) => void)): void;
        function tickPost(handler: ((event: $EntityTickEvent$Post) => void)): void;
        function tickPost(extra: $EntityType, handler: ((event: $EntityTickEvent$Post) => void)): void;
        function joinLevel(handler: ((event: $EntityJoinLevelEvent) => void)): void;
        function joinLevel(extra: $EntityType, handler: ((event: $EntityJoinLevelEvent) => void)): void;
        function leaveLevel(handler: ((event: $EntityLeaveLevelEvent) => void)): void;
        function leaveLevel(extra: $EntityType, handler: ((event: $EntityLeaveLevelEvent) => void)): void;
        function useItemStarted(handler: ((event: $LivingEntityUseItemEvent$Start) => void)): void;
        function useItemStarted(extra: $Item, handler: ((event: $LivingEntityUseItemEvent$Start) => void)): void;
        function useItemStopped(handler: ((event: $LivingEntityUseItemEvent$Stop) => void)): void;
        function useItemStopped(extra: $Item, handler: ((event: $LivingEntityUseItemEvent$Stop) => void)): void;
        function useItemFinished(handler: ((event: $LivingEntityUseItemEvent$Finish) => void)): void;
        function useItemFinished(extra: $Item, handler: ((event: $LivingEntityUseItemEvent$Finish) => void)): void;
        function useItemTick(handler: ((event: $LivingEntityUseItemEvent$Tick) => void)): void;
        function useItemTick(extra: $Item, handler: ((event: $LivingEntityUseItemEvent$Tick) => void)): void;
    }

    namespace GoalEvents {
        function register(handler: ((event: $GoalRegisterEventJS) => void)): void;
    }

    namespace ItemEvents {
        function rightClicked(handler: ((event: $PlayerInteractEvent$RightClickItem) => void)): void;
        function rightClicked(extra: $Item, handler: ((event: $PlayerInteractEvent$RightClickItem) => void)): void;
        function modification(handler: ((event: $ItemModificationEventJS) => void)): void;
        function tooltip(handler: ((event: $ItemTooltipEvent) => void)): void;
        function tooltip(extra: $Item, handler: ((event: $ItemTooltipEvent) => void)): void;
        function canPickUp(handler: ((event: $ItemEntityPickupEvent$Pre) => void)): void;
        function canPickUp(extra: $Item, handler: ((event: $ItemEntityPickupEvent$Pre) => void)): void;
        function pickedUpPre(handler: ((event: $ItemEntityPickupEvent$Pre) => void)): void;
        function pickedUpPre(extra: $Item, handler: ((event: $ItemEntityPickupEvent$Pre) => void)): void;
        function pickedUp(handler: ((event: $ItemEntityPickupEvent$Post) => void)): void;
        function pickedUp(extra: $Item, handler: ((event: $ItemEntityPickupEvent$Post) => void)): void;
        function dropped(handler: ((event: $ItemTossEvent) => void)): void;
        function dropped(extra: $Item, handler: ((event: $ItemTossEvent) => void)): void;
        function entityInteracted(handler: ((event: $PlayerInteractEvent$EntityInteract) => void)): void;
        function entityInteracted(extra: $Item, handler: ((event: $PlayerInteractEvent$EntityInteract) => void)): void;
        function foodEaten(handler: ((event: $LivingEntityUseItemEvent$Finish) => void)): void;
        function foodEaten(extra: $Item, handler: ((event: $LivingEntityUseItemEvent$Finish) => void)): void;
    }

    namespace KeyBindEvents {
        function pressed(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function pressed(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function released(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function released(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function tick(handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function tick(extra: string, handler: ((event: $KeyBindEvents$KeyBindEventJS) => void)): void;
        function register(handler: ((event: $Object) => void)): void;
    }

    namespace LevelEvents {
        function loaded(handler: ((event: $LevelEvent$Load) => void)): void;
        function unloaded(handler: ((event: $LevelEvent$Unload) => void)): void;
        function saved(handler: ((event: $LevelEvent$Save) => void)): void;
        function tickPre(handler: ((event: $LevelTickEvent$Pre) => void)): void;
        function tickPost(handler: ((event: $LevelTickEvent$Post) => void)): void;
        function tick(handler: ((event: $LevelTickEvent$Post) => void)): void;
        function explosionStart(handler: ((event: $ExplosionEvent$Start) => void)): void;
        function beforeExplosion(handler: ((event: $ExplosionEvent$Start) => void)): void;
        function explosionDetonate(handler: ((event: $ExplosionEvent$Detonate) => void)): void;
        function afterExplosion(handler: ((event: $ExplosionEvent$Detonate) => void)): void;
    }

    namespace PlayerEvents {
        function loggedIn(handler: ((event: $PlayerEvent$PlayerLoggedInEvent) => void)): void;
        function loggedOut(handler: ((event: $PlayerEvent$PlayerLoggedOutEvent) => void)): void;
        function chat(handler: ((event: $ServerChatEvent) => void)): void;
        function tickPost(handler: ((event: $PlayerTickEvent$Post) => void)): void;
        function tickPre(handler: ((event: $PlayerTickEvent$Pre) => void)): void;
        function cloned(handler: ((event: $PlayerEvent$Clone) => void)): void;
        function respawned(handler: ((event: $PlayerEvent$PlayerRespawnEvent) => void)): void;
        function changedDimension(handler: ((event: $PlayerEvent$PlayerChangedDimensionEvent) => void)): void;
        function advancement(handler: ((event: $AdvancementEvent$AdvancementEarnEvent) => void)): void;
        function containerOpened(handler: ((event: $PlayerContainerEvent$Open) => void)): void;
        function inventoryOpened(handler: ((event: $PlayerContainerEvent$Open) => void)): void;
        function containerClosed(handler: ((event: $PlayerContainerEvent$Close) => void)): void;
        function inventoryClosed(handler: ((event: $PlayerContainerEvent$Close) => void)): void;
        function entityInteract(handler: ((event: $PlayerInteractEvent$EntityInteract) => void)): void;
        function crafted(handler: ((event: $PlayerEvent$ItemCraftedEvent) => void)): void;
        function crafted(extra: $Item, handler: ((event: $PlayerEvent$ItemCraftedEvent) => void)): void;
        function smelted(handler: ((event: $PlayerEvent$ItemSmeltedEvent) => void)): void;
        function smelted(extra: $Item, handler: ((event: $PlayerEvent$ItemSmeltedEvent) => void)): void;
        function destroyed(handler: ((event: $PlayerDestroyItemEvent) => void)): void;
        function destroyed(extra: $Item, handler: ((event: $PlayerDestroyItemEvent) => void)): void;
        function inventoryChanged(handler: ((event: $InventoryChangedEventJS) => void)): void;
        function inventoryChanged(extra: $Item, handler: ((event: $InventoryChangedEventJS) => void)): void;
    }

    namespace RecipeViewerEvents {
        function addEntries(handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function addEntries(extra: string, handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeEntries(handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeEntries(extra: string, handler: ((event: $RecipeViewerEntryListJS) => void)): void;
        function removeRecipes(handler: ((event: $RecipeViewerRecipeListJS) => void)): void;
        function removeCategories(handler: ((event: $RecipeViewerCategoryListJS) => void)): void;
        function addInformation(handler: ((event: $RecipeViewerInformationJS) => void)): void;
    }

    namespace ServerEvents {
        function tickPre(handler: ((event: $ServerTickEvent$Pre) => void)): void;
        function tickPost(handler: ((event: $ServerTickEvent$Post) => void)): void;
        function recipes(handler: ((event: $RecipeEventJS) => void)): void;
        function afterRecipes(handler: ((event: $RecipeEventJS) => void)): void;
        function generateData(handler: ((event: $DataGeneratorJS) => void)): void;
        function generateData(extra: string, handler: ((event: $DataGeneratorJS) => void)): void;
        function aboutToStart(handler: ((event: $ServerAboutToStartEvent) => void)): void;
        function starting(handler: ((event: $ServerStartingEvent) => void)): void;
        function started(handler: ((event: $ServerStartedEvent) => void)): void;
        function stopping(handler: ((event: $ServerStoppingEvent) => void)): void;
        function stopped(handler: ((event: $ServerStoppedEvent) => void)): void;
        function datapackSync(handler: ((event: $OnDatapackSyncEvent) => void)): void;
        function tagsUpdated(handler: ((event: $TagsUpdatedEvent) => void)): void;
        function lootTableLoad(handler: ((event: $LootTableLoadEvent) => void)): void;
        function lootTables(handler: ((event: $LootTableEventJS) => void)): void;
        function tags(handler: ((event: $TagEventJS) => void)): void;
        function tags(extra: $Identifier, handler: ((event: $TagEventJS) => void)): void;
        function tradeDeclaration(handler: ((event: $VillagerTradeDeclarationEventJS) => void)): void;
        function tradeReload(handler: ((event: $VillagerTradeReloadEventJS) => void)): void;
    }

}
