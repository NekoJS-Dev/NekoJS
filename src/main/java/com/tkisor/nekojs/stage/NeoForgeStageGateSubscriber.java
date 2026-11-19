//? if neoforge {
package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
//? if >=26 {
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
//?} else {
/*import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
*///?}

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic NeoForge gameplay gate subscriber and player stage lifecycle listener.
 *
 * <p>Registers permanent {@link EventPriority#HIGHEST} listeners on {@link NeoForge#EVENT_BUS}
 * once during mod initialization so declarative stage gates always run ahead of normal-priority
 * {@code EventBusForgeBridge} script callbacks and stay intact across {@code /nekojs reload}.
 */
public final class NeoForgeStageGateSubscriber {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private NeoForgeStageGateSubscriber() {
    }

    /** Idempotently installs all stage gate and player lifecycle listeners onto {@link NeoForge#EVENT_BUS}. */
    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        IEventBus bus = NeoForge.EVENT_BUS;

        bus.addListener(EventPriority.HIGHEST, false,
                PlayerInteractEvent.RightClickItem.class, NeoForgeStageGateSubscriber::onRightClickItem);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerInteractEvent.RightClickBlock.class, NeoForgeStageGateSubscriber::onRightClickBlock);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerInteractEvent.LeftClickBlock.class, NeoForgeStageGateSubscriber::onLeftClickBlock);
//? if >=26 {
        bus.addListener(EventPriority.HIGHEST, false,
                BreakBlockEvent.class, NeoForgeStageGateSubscriber::onBreakBlock);
//?} else {
        /*bus.addListener(EventPriority.HIGHEST, false,
                BreakEvent.class, NeoForgeStageGateSubscriber::onBreakBlock);
*///?}
        bus.addListener(EventPriority.HIGHEST, false,
                BlockEvent.EntityPlaceEvent.class, NeoForgeStageGateSubscriber::onEntityPlaceBlock);
        bus.addListener(EventPriority.HIGHEST, false,
                BlockEvent.BlockToolModificationEvent.class, NeoForgeStageGateSubscriber::onBlockToolModification);
        bus.addListener(EventPriority.HIGHEST, false,
                ItemEntityPickupEvent.Pre.class, NeoForgeStageGateSubscriber::onItemPickupPre);
        bus.addListener(EventPriority.HIGHEST, false,
                ItemTossEvent.class, NeoForgeStageGateSubscriber::onItemToss);
        bus.addListener(EventPriority.HIGHEST, false,
                LivingEntityUseItemEvent.Start.class, NeoForgeStageGateSubscriber::onLivingUseItemStart);
        bus.addListener(EventPriority.HIGHEST, false,
                AttackEntityEvent.class, NeoForgeStageGateSubscriber::onAttackEntity);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerInteractEvent.EntityInteract.class, NeoForgeStageGateSubscriber::onEntityInteract);
//? if <26.2 {
/*        bus.addListener(EventPriority.HIGHEST, false,
                PlayerInteractEvent.EntityInteractSpecific.class, NeoForgeStageGateSubscriber::onEntityInteractSpecific);
*///?}
        bus.addListener(EventPriority.HIGHEST, false,
                EntityMountEvent.class, NeoForgeStageGateSubscriber::onEntityMount);
        bus.addListener(EventPriority.HIGHEST, false,
                EntityTravelToDimensionEvent.class, NeoForgeStageGateSubscriber::onEntityTravelToDimension);
        bus.addListener(EventPriority.HIGHEST, false,
                MobEffectEvent.Applicable.class, NeoForgeStageGateSubscriber::onMobEffectApplicable);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerContainerEvent.Open.class, NeoForgeStageGateSubscriber::onContainerOpen);
        bus.addListener(EventPriority.HIGHEST, false,
                LivingDropsEvent.class, NeoForgeStageGateSubscriber::onLivingDrops);
        bus.addListener(EventPriority.HIGHEST, false,
                ItemTooltipEvent.class, NeoForgeStageGateSubscriber::onItemTooltip);

        bus.addListener(EventPriority.HIGHEST, false,
                PlayerEvent.PlayerLoggedInEvent.class, NeoForgeStageGateSubscriber::onPlayerLoggedIn);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerEvent.PlayerLoggedOutEvent.class, NeoForgeStageGateSubscriber::onPlayerLoggedOut);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerEvent.PlayerRespawnEvent.class, NeoForgeStageGateSubscriber::onPlayerRespawn);
        bus.addListener(EventPriority.HIGHEST, false,
                PlayerEvent.PlayerChangedDimensionEvent.class, NeoForgeStageGateSubscriber::onPlayerChangedDimension);
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()
                && !StageGateEnforcer.allowUseItem(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!StageGateEnforcer.allowInteractBlock(
                event.getEntity(), event.getLevel(), event.getPos(), state)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        } else if (!StageGateEnforcer.allowUseItem(event.getEntity(), event.getItemStack())) {
            event.setUseItem(TriState.FALSE);
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!StageGateEnforcer.allowAttackWithItem(event.getEntity(), event.getItemStack())
                || !StageGateEnforcer.allowInteractBlock(event.getEntity(), event.getLevel(), event.getPos(), state)
                || !StageGateEnforcer.allowBreakBlock(
                        event.getEntity(), event.getLevel(), event.getPos(), state)) {
            event.setCanceled(true);
        }
    }

//? if >=26 {
    private static void onBreakBlock(BreakBlockEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof Level level
                && !level.isClientSide()
                && event.getPlayer() != null
                && !StageGateEnforcer.allowBreakBlock(
                        event.getPlayer(), level, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }
//?} else {
    /*private static void onBreakBlock(BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof Level level
                && !level.isClientSide()
                && event.getPlayer() != null
                && !StageGateEnforcer.allowBreakBlock(
                        event.getPlayer(), level, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }
*///?}

    private static void onEntityPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            Level level = event.getLevel() instanceof Level lvl ? lvl : player.level();
            if (!StageGateEnforcer.allowPlaceBlock(
                    player, level, event.getPos(), event.getPlacedBlock())) {
                event.setCanceled(true);
            }
        }
    }

    private static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        Player player = event.getPlayer();
        if (!event.isSimulated() && player != null && !player.level().isClientSide()) {
            Level level = event.getLevel() instanceof Level lvl ? lvl : player.level();
            if (!StageGateEnforcer.allowToolModifyBlock(
                    player, level, event.getPos(), event.getState())) {
                event.setCanceled(true);
            }
        }
    }

    private static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!event.getPlayer().level().isClientSide()
                && !StageGateEnforcer.allowPickUpItem(
                        event.getPlayer(), event.getItemEntity().getItem())) {
            denyItemPickup(event);
        }
    }

    public static void denyItemPickup(ItemEntityPickupEvent.Pre event) {
        event.setCanPickup(TriState.FALSE);
    }

    private static void onItemToss(ItemTossEvent event) {
        if (!event.getPlayer().level().isClientSide()
                && !StageGateEnforcer.allowDropItem(
                        event.getPlayer(), event.getEntity().getItem())) {
            event.setCanceled(true);
            event.getPlayer().getInventory().placeItemBackInInventory(
                    event.getEntity().getItem().copy());
        }
    }

    private static void onLivingUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player
                && !player.level().isClientSide()
                && !StageGateEnforcer.allowUseItem(player, event.getItem())) {
            event.setCanceled(true);
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide()
                && !StageGateEnforcer.allowAttackEntity(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()
                && !StageGateEnforcer.allowInteractEntity(
                        event.getEntity(), event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

//? if <26.2 {
/*    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide()
                && !StageGateEnforcer.allowInteractEntity(
                        event.getEntity(), event.getTarget(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
*///?}

    private static void onEntityMount(EntityMountEvent event) {
        if (event.isMounting()
                && !event.getLevel().isClientSide()
                && !StageGateEnforcer.allowMountEntity(
                        event.getEntityMounting(), event.getEntityBeingMounted())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getDimension() != null) {
            NekoId targetDimId = MinecraftStageRegistryAccess.fromResourceKey(event.getDimension());
            if (!StageGateEnforcer.allowEnterDimension(event.getEntity(), targetDimId)) {
                event.setCanceled(true);
            }
        }
    }

    private static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEntity().level().isClientSide()
                && event.getEffectInstance() != null
                && !StageGateEnforcer.allowApplyEffect(
                        event.getEntity(), event.getEffectInstance().getEffect())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer sp
                && !StageGateEnforcer.allowOpenContainer(sp, event.getContainer())) {
            com.tkisor.nekojs.bindings.event.NeoForgeContainerEvents.closeDeniedOpen(sp);
        }
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        StageGateEnforcer.filterEntityDrops(
                event.getEntity(), event.getSource().getEntity(), event.getDrops());
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getEntity() == null || event.getEntity().level().isClientSide()) {
            StageGateEnforcer.appendClientItemStageTooltip(
                    event.getItemStack(), event.getToolTip());
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StageDomainOwner owner = StageDomainOwner.current();
            if (owner != null) {
                owner.onPlayerJoin(sp);
            }
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StageDomainOwner owner = StageDomainOwner.current();
            if (owner != null) {
                owner.onPlayerLeave(sp);
            }
        }
    }

    private static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StageDomainOwner owner = StageDomainOwner.current();
            if (owner != null) {
                owner.onPlayerRespawn(sp);
            }
        }
    }

    private static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StageDomainOwner owner = StageDomainOwner.current();
            if (owner != null) {
                owner.onPlayerRespawn(sp);
            }
        }
    }
}
//?}
