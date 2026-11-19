import { $StableBlockEvent, $StableCommandExecuteEvent, $StableEntityEvent } from "java:com/tkisor/nekojs/api/event";
import { $VillagerTradeDeclarationEvent, $VillagerTradeReloadEvent } from "java:com/tkisor/nekojs/api/villager";
import { $BlockBrokenEventJS, $BlockEntityTickEventJS, $BlockFarmlandTrampleEventJS, $BlockFluidPlacedEventJS, $BlockLeftClickEventJS, $BlockNeighborNotifyEventJS, $BlockPlacedEventJS, $BlockPortalSpawnEventJS, $BlockRandomTickEventJS, $BlockRightClickEventJS } from "java:com/tkisor/nekojs/wrapper/event/block";
import { $EntityJoinLevelEventJS, $EntityLeaveLevelEventJS, $EntityTickEventJS, $LivingDamageEventJS, $LivingDeathEventJS, $LivingDropsEventJS, $MobFinalizeSpawnEventJS } from "java:com/tkisor/nekojs/wrapper/event/entity";
import { $ItemDroppedEventJS, $ItemEntityPickupEventJS, $ItemRightClickEventJS, $ItemUseFinishedEventJS, $PlayerEntityInteractEventJS as $NekoImport$PlayerEntityInteractEventJS } from "java:com/tkisor/nekojs/wrapper/event/item";
import { $LevelEventJS, $LevelExplosionEventJS, $LevelSavedEventJS } from "java:com/tkisor/nekojs/wrapper/event/level";
import { $InventoryChangedEventJS, $PlayerAdvancementEventJS, $PlayerChangedDimensionEventJS, $PlayerCloneEventJS, $PlayerContainerEventJS, $PlayerCraftedEventJS, $PlayerDestroyItemEventJS, $PlayerEntityInteractEventJS as $NekoImport$PlayerEntityInteractEventJS1, $PlayerLifecycleEventJS, $PlayerRespawnEventJS, $PlayerTickEventJS, $ServerChatEventJS } from "java:com/tkisor/nekojs/wrapper/event/player";
import { $BlockModificationEventJS, $CommandRegistryEventJS, $DatapackSyncEventJS, $ItemModificationEventJS, $LootTableLoadEventJS, $RecipeEventJS, $ServerLifecycleEventJS, $ServerTickEventJS, $TagUpdatedEventJS } from "java:com/tkisor/nekojs/wrapper/event/server";
import { $EntityType } from "java:net/minecraft/world/entity";
import { $Item } from "java:net/minecraft/world/item";
import { $Block } from "java:net/minecraft/world/level/block";
import { $BlockEntityType } from "java:net/minecraft/world/level/block/entity";

declare module "@side-only/server/events" {
}

export {};

declare global {
    namespace BlockEvents {
        function broken(handler: ((event: $BlockBrokenEventJS) => void)): void;
        function broken(extra: $Block, handler: ((event: $BlockBrokenEventJS) => void)): void;
        function modification(handler: ((event: $BlockModificationEventJS) => void)): void;
        function rightClicked(handler: ((event: $BlockRightClickEventJS) => void)): void;
        function rightClicked(extra: $Block, handler: ((event: $BlockRightClickEventJS) => void)): void;
        function leftClicked(handler: ((event: $BlockLeftClickEventJS) => void)): void;
        function leftClicked(extra: $Block, handler: ((event: $BlockLeftClickEventJS) => void)): void;
        function portalSpawn(handler: ((event: $BlockPortalSpawnEventJS) => void)): void;
        function portalSpawn(extra: $Block, handler: ((event: $BlockPortalSpawnEventJS) => void)): void;
        function neighborNotify(handler: ((event: $BlockNeighborNotifyEventJS) => void)): void;
        function neighborNotify(extra: $Block, handler: ((event: $BlockNeighborNotifyEventJS) => void)): void;
        function farmlandTrample(handler: ((event: $BlockFarmlandTrampleEventJS) => void)): void;
        function farmlandTrample(extra: $Block, handler: ((event: $BlockFarmlandTrampleEventJS) => void)): void;
        function placed(handler: ((event: $BlockPlacedEventJS) => void)): void;
        function placed(extra: $Block, handler: ((event: $BlockPlacedEventJS) => void)): void;
        function entityPlaced(handler: ((event: $BlockPlacedEventJS) => void)): void;
        function entityPlaced(extra: $Block, handler: ((event: $BlockPlacedEventJS) => void)): void;
        function fluidPlaced(handler: ((event: $BlockFluidPlacedEventJS) => void)): void;
        function fluidPlaced(extra: $Block, handler: ((event: $BlockFluidPlacedEventJS) => void)): void;
        function randomTick(handler: ((event: $BlockRandomTickEventJS) => void)): void;
        function randomTick(extra: $Block, handler: ((event: $BlockRandomTickEventJS) => void)): void;
        function blockEntityTick(handler: ((event: $BlockEntityTickEventJS) => void)): void;
        function blockEntityTick(
            extra: $BlockEntityType<any>,
            handler: ((event: $BlockEntityTickEventJS) => void)
        ): void;
        function entityMultiPlaced(handler: ((event: $StableBlockEvent) => void)): void;
        function entityMultiPlaced(extra: $Block, handler: ((event: $StableBlockEvent) => void)): void;
        function toolModification(handler: ((event: $StableBlockEvent) => void)): void;
        function toolModification(extra: $Block, handler: ((event: $StableBlockEvent) => void)): void;
    }

    namespace CommandEvents {
        function register(handler: ((event: $CommandRegistryEventJS) => void)): void;
        function command(handler: ((event: $StableCommandExecuteEvent) => void)): void;
    }

    namespace EntityEvents {
        function joinLevel(handler: ((event: $EntityJoinLevelEventJS) => void)): void;
        function joinLevel(extra: $EntityType<any>, handler: ((event: $EntityJoinLevelEventJS) => void)): void;
        function death(handler: ((event: $LivingDeathEventJS) => void)): void;
        function death(extra: $EntityType<any>, handler: ((event: $LivingDeathEventJS) => void)): void;
        function damagePre(handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePre(extra: $EntityType<any>, handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePost(handler: ((event: $LivingDamageEventJS) => void)): void;
        function damagePost(extra: $EntityType<any>, handler: ((event: $LivingDamageEventJS) => void)): void;
        function drops(handler: ((event: $LivingDropsEventJS) => void)): void;
        function drops(extra: $EntityType<any>, handler: ((event: $LivingDropsEventJS) => void)): void;
        function finalizeSpawn(handler: ((event: $MobFinalizeSpawnEventJS) => void)): void;
        function finalizeSpawn(extra: $EntityType<any>, handler: ((event: $MobFinalizeSpawnEventJS) => void)): void;
        function tickPre(handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPre(extra: $EntityType<any>, handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPost(handler: ((event: $EntityTickEventJS) => void)): void;
        function tickPost(extra: $EntityType<any>, handler: ((event: $EntityTickEventJS) => void)): void;
        function leaveLevel(handler: ((event: $EntityLeaveLevelEventJS) => void)): void;
        function leaveLevel(extra: $EntityType<any>, handler: ((event: $EntityLeaveLevelEventJS) => void)): void;
        function useItemStarted(handler: ((event: $StableEntityEvent) => void)): void;
        function useItemStarted(extra: $Item, handler: ((event: $StableEntityEvent) => void)): void;
        function useItemStopped(handler: ((event: $StableEntityEvent) => void)): void;
        function useItemStopped(extra: $Item, handler: ((event: $StableEntityEvent) => void)): void;
        function useItemFinished(handler: ((event: $StableEntityEvent) => void)): void;
        function useItemFinished(extra: $Item, handler: ((event: $StableEntityEvent) => void)): void;
        function useItemTick(handler: ((event: $StableEntityEvent) => void)): void;
        function useItemTick(extra: $Item, handler: ((event: $StableEntityEvent) => void)): void;
    }

    namespace ItemEvents {
        function rightClicked(handler: ((event: $ItemRightClickEventJS) => void)): void;
        function rightClicked(extra: $Item, handler: ((event: $ItemRightClickEventJS) => void)): void;
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
        function entityInteracted(handler: ((event: $NekoImport$PlayerEntityInteractEventJS) => void)): void;
        function entityInteracted(
            extra: $Item,
            handler: ((event: $NekoImport$PlayerEntityInteractEventJS) => void)
        ): void;
        function modification(handler: ((event: $ItemModificationEventJS) => void)): void;
    }

    namespace LevelEvents {
        function loaded(handler: ((event: $LevelEventJS) => void)): void;
        function unloaded(handler: ((event: $LevelEventJS) => void)): void;
        function tickPre(handler: ((event: $LevelEventJS) => void)): void;
        function tickPost(handler: ((event: $LevelEventJS) => void)): void;
        function saved(handler: ((event: $LevelSavedEventJS) => void)): void;
        function explosionStart(handler: ((event: $LevelExplosionEventJS) => void)): void;
        function beforeExplosion(handler: ((event: $LevelExplosionEventJS) => void)): void;
        function explosionDetonate(handler: ((event: $LevelExplosionEventJS) => void)): void;
        function afterExplosion(handler: ((event: $LevelExplosionEventJS) => void)): void;
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
        function entityInteract(handler: ((event: $NekoImport$PlayerEntityInteractEventJS1) => void)): void;
        function changedDimension(handler: ((event: $PlayerChangedDimensionEventJS) => void)): void;
        function inventoryChanged(handler: ((event: $InventoryChangedEventJS) => void)): void;
        function inventoryChanged(extra: $Item, handler: ((event: $InventoryChangedEventJS) => void)): void;
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
        function tradeDeclaration(handler: ((event: $VillagerTradeDeclarationEvent) => void)): void;
        function tradeReload(handler: ((event: $VillagerTradeReloadEvent) => void)): void;
    }

}
