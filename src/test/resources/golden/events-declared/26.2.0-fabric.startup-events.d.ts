import { $KeyBindEvents$KeyBindEventJS } from "java:com/tkisor/nekojs/bindings/event/client";
import { $PainterJS, $ScreenRenderEventJS } from "java:com/tkisor/nekojs/wrapper/client";
import { $BlockBrokenEventJS } from "java:com/tkisor/nekojs/wrapper/event/block";
import { $ClientTickEventJS } from "java:com/tkisor/nekojs/wrapper/event/client";
import { $EntityJoinLevelEventJS, $EntityLeaveLevelEventJS, $EntityTickEventJS, $GoalRegisterEventJS, $LivingDamageEventJS, $LivingDeathEventJS, $LivingDropsEventJS, $MobFinalizeSpawnEventJS } from "java:com/tkisor/nekojs/wrapper/event/entity";
import { $ItemDroppedEventJS, $ItemEntityPickupEventJS, $ItemRightClickEventJS, $ItemTooltipEventJS, $ItemUseFinishedEventJS, $PlayerEntityInteractEventJS } from "java:com/tkisor/nekojs/wrapper/event/item";
import { $LevelEventJS } from "java:com/tkisor/nekojs/wrapper/event/level";
import { $InventoryChangedEventJS, $PlayerAdvancementEventJS, $PlayerChangedDimensionEventJS, $PlayerCloneEventJS, $PlayerContainerEventJS, $PlayerCraftedEventJS, $PlayerDestroyItemEventJS, $PlayerEntityInteractEventJS, $PlayerLifecycleEventJS, $PlayerRespawnEventJS, $PlayerTickEventJS, $ServerChatEventJS } from "java:com/tkisor/nekojs/wrapper/event/player";
import { $CapabilityRegistryEventJS } from "java:com/tkisor/nekojs/wrapper/event/registry";
import { $BlockModificationEventJS, $CommandRegistryEventJS, $DatapackSyncEventJS, $ItemModificationEventJS, $LootTableLoadEventJS, $RecipeEventJS, $ServerLifecycleEventJS, $ServerTickEventJS, $TagUpdatedEventJS } from "java:com/tkisor/nekojs/wrapper/event/server";
import { $RecipeViewerCategoryListJS, $RecipeViewerEntryListJS, $RecipeViewerInformationJS, $RecipeViewerRecipeListJS } from "java:com/tkisor/nekojs/wrapper/viewer";
import { $String } from "java:java/lang";
import { $EntityType } from "java:net/minecraft/world/entity";
import { $Item } from "java:net/minecraft/world/item";
import { $Block } from "java:net/minecraft/world/level/block";

declare module "@side-only/startup/events" {
}

export {};

declare global {
    namespace BlockEvents {
        function broken(handler: ((event: $BlockBrokenEventJS) => void)): void;
        function broken(extra: $Block, handler: ((event: $BlockBrokenEventJS) => void)): void;
        function modification(handler: ((event: $BlockModificationEventJS) => void)): void;
    }

    namespace CapabilityEvents {
        function register(handler: ((event: $CapabilityRegistryEventJS) => void)): void;
    }

    namespace ClientEvents {
        function tickPre(handler: ((event: $ClientTickEventJS) => void)): void;
        function tickPost(handler: ((event: $ClientTickEventJS) => void)): void;
        function tick(handler: ((event: $ClientTickEventJS) => void)): void;
        function hud(handler: ((event: $PainterJS) => void)): void;
        function screenRender(handler: ((event: $ScreenRenderEventJS) => void)): void;
    }

    namespace CommandEvents {
        function register(handler: ((event: $CommandRegistryEventJS) => void)): void;
    }

    namespace EntityEvents {
        function joinLevel(handler: ((event: $EntityJoinLevelEventJS) => void)): void;
        function joinLevel(extra: $EntityType, handler: ((event: $EntityJoinLevelEventJS) => void)): void;
        function death(handler: ((event: $LivingDeathEventJS) => void)): void;
        function death(extra: $EntityType, handler: ((event: $LivingDeathEventJS) => void)): void;
        function damagePre(handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePre(extra: $EntityType, handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePost(handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePost(extra: $EntityType, handler: ((event: $LivingDamageEventJS) => void)): void;
        function drops(handler: ((event: $LivingDropsEventJS) => void)): void;
        function drops(extra: $EntityType, handler: ((event: $LivingDropsEventJS) => void)): void;
        function finalizeSpawn(handler: ((event: $MobFinalizeSpawnEventJS) => void)): void;
        function finalizeSpawn(extra: $EntityType, handler: ((event: $MobFinalizeSpawnEventJS) => void)): void;
        function tickPre(handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPre(extra: $EntityType, handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPost(handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPost(extra: $EntityType, handler: ((event: $EntityTickEventJS) => void)): void;
        function leaveLevel(handler: ((event: $EntityLeaveLevelEventJS) => void)): void;
        function leaveLevel(extra: $EntityType, handler: ((event: $EntityLeaveLevelEventJS) => void)): void;
    }

    namespace GoalEvents {
        function register(handler: ((event: $GoalRegisterEventJS) => void)): void;
    }

    namespace ItemEvents {
        function rightClicked(handler: ((event: $ItemRightClickEventJS) => void)): void;
        function rightClicked(extra: $Item, handler: ((event: $ItemRightClickEventJS) => void)): void;
        function tooltip(handler: ((event: $ItemTooltipEventJS) => void)): void;
        function tooltip(extra: $Item, handler: ((event: $ItemTooltipEventJS) => void)): void;
        function canPickUp(handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function canPickUp(extra: $Item, handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUpPre(handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUpPre(extra: $Item, handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUp(handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function pickedUp(extra: $Item, handler: ((event: $ItemEntityPickupEventJS) => void)): void;
        function dropped(handler: ((event: $ItemDroppedEventJS) => void)): void;
        function dropped(extra: $Item, handler: ((event: $ItemDroppedEventJS) => void)): void;
        function foodEaten(handler: ((event: $ItemUseFinishedEventJS) => void)): void;
        function foodEaten(extra: $Item, handler: ((event: $ItemUseFinishedEventJS) => void)): void;
        function entityInteracted(handler: ((event: $PlayerEntityInteractEventJS) => void)): void;
        function entityInteracted(extra: $Item, handler: ((event: $PlayerEntityInteractEventJS) => void)): void;
        function modification(handler: ((event: $ItemModificationEventJS) => void)): void;
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
        function loaded(handler: ((event: $LevelEventJS) => void)): void;
        function unloaded(handler: ((event: $LevelEventJS) => void)): void;
        function tickPre(handler: ((event: $LevelEventJS) => void)): void;
        function tickPost(handler: ((event: $LevelEventJS) => void)): void;
    }

    namespace PlayerEvents {
        function loggedIn(handler: ((event: $PlayerLifecycleEventJS) => void)): void;
        function loggedOut(handler: ((event: $PlayerLifecycleEventJS) => void)): void;
        function tickPre(handler: ((event: $PlayerTickEventJS) => void)): void;
        function tickPost(handler: ((event: $PlayerTickEventJS) => void)): void;
        function cloned(handler: ((event: $PlayerCloneEventJS) => void)): void;
        function respawned(handler: ((event: $PlayerRespawnEventJS) => void)): void;
        function chat(handler: ((event: $ServerChatEventJS) => void)): void;
        function containerOpened(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function inventoryOpened(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function containerClosed(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function inventoryClosed(handler: ((event: $PlayerContainerEventJS) => void)): void;
        function crafted(handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function crafted(extra: $Item, handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function smelted(handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function smelted(extra: $Item, handler: ((event: $PlayerCraftedEventJS) => void)): void;
        function destroyed(handler: ((event: $PlayerDestroyItemEventJS) => void)): void;
        function destroyed(extra: $Item, handler: ((event: $PlayerDestroyItemEventJS) => void)): void;
        function advancement(handler: ((event: $PlayerAdvancementEventJS) => void)): void;
        function entityInteract(handler: ((event: $PlayerEntityInteractEventJS) => void)): void;
        function changedDimension(handler: ((event: $PlayerChangedDimensionEventJS) => void)): void;
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
        function aboutToStart(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function starting(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function started(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function stopping(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function stopped(handler: ((event: $ServerLifecycleEventJS) => void)): void;
        function tickPre(handler: ((event: $ServerTickEventJS) => void)): void;
        function tickPost(handler: ((event: $ServerTickEventJS) => void)): void;
        function datapackSync(handler: ((event: $DatapackSyncEventJS) => void)): void;
        function tagsUpdated(handler: ((event: $TagUpdatedEventJS) => void)): void;
        function lootTableLoad(handler: ((event: $LootTableLoadEventJS) => void)): void;
        function recipes(handler: ((event: $RecipeEventJS) => void)): void;
        function afterRecipes(handler: ((event: $RecipeEventJS) => void)): void;
        function tradeDeclaration(handler: ((event: $VillagerTradeDeclarationEventJS) => void)): void;
        function tradeReload(handler: ((event: $VillagerTradeReloadEventJS) => void)): void;
    }

}
