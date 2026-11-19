package com.tkisor.nekojs.fabric.event;

import com.mojang.brigadier.ParseResults;
import com.tkisor.nekojs.api.event.StableBlockEvent;
import com.tkisor.nekojs.api.event.StableCommandExecuteEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import com.tkisor.nekojs.api.event.StableExplosionEvent;
import com.tkisor.nekojs.api.event.StableItemEvent;
import com.tkisor.nekojs.api.event.StableLevelEvent;
import com.tkisor.nekojs.api.event.StablePlayerAdvancementEvent;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.api.event.StablePlayerDimensionChangeEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StablePlayerItemEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockEntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFarmlandTrampleEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockFluidPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockLeftClickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockNeighborNotifyEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPlacedEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockPortalSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRandomTickEventJS;
import com.tkisor.nekojs.wrapper.event.block.BlockRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityJoinLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityLeaveLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.EntityTickEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDamageEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.entity.MobFinalizeSpawnEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelExplosionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCloneEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerLifecycleEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerRespawnEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerTickEventJS;
import com.tkisor.nekojs.wrapper.event.player.ServerChatEventJS;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Dedicated-server-safe projections from Fabric and loader-neutral wrapper events to portable
 * stable event snapshots, plus string selector resolvers for registry-backed dispatch keys.
 *
 * <p>This class deliberately avoids importing any {@code net.minecraft.client.*} or
 * {@code net.fabricmc.fabric.api.client.*} types so it can be safely loaded on dedicated servers.
 */
public final class FabricStableEventProjections {
    private FabricStableEventProjections() {}

    /**
     * Parses a registry selector string into a {@link Identifier}, defaulting to the
     * {@code minecraft} namespace when no colon is present.
     */
    public static Identifier parseSelectorId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4057] Event selector id must not be blank");
        }
        String trimmed = raw.trim();
        return trimmed.contains(":")
                ? Identifier.parse(trimmed)
                : Identifier.fromNamespaceAndPath("minecraft", trimmed);
    }

    /** Resolves a block registry selector id or throws {@code [NEKO-4057]} if unknown. */
    public static Block resolveBlockSelector(String id) {
        return BuiltInRegistries.BLOCK
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown block selector: " + id));
    }

    /** Resolves an item registry selector id or throws {@code [NEKO-4057]} if unknown. */
    public static Item resolveItemSelector(String id) {
        return BuiltInRegistries.ITEM
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown item selector: " + id));
    }

    /** Resolves an entity type registry selector id or throws {@code [NEKO-4057]} if unknown. */
    public static EntityType<?> resolveEntityTypeSelector(String id) {
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown entity type selector: " + id));
    }

    /** Resolves a block entity type registry selector id or throws {@code [NEKO-4057]} if unknown. */
    public static BlockEntityType<?> resolveBlockEntityTypeSelector(String id) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown block entity type selector: " + id));
    }

    /** Extracts the namespaced dimension identifier of {@code access}, or {@code ""} when unavailable. */
    public static String dimensionOf(LevelAccessor access) {
        return access instanceof Level lvl && lvl.dimension() != null
                ? lvl.dimension().identifier().toString()
                : "";
    }

    /** Projects a {@link LevelAccessor} into a {@link StableLevelEvent} snapshot. */
    public static StableLevelEvent levelSnapshot(LevelAccessor access) {
        if (access instanceof Level lvl) {
            return new StableLevelEvent(dimensionOf(lvl), access.isClientSide(), lvl.getGameTime(), lvl.getOverworldClockTime());
        }
        return new StableLevelEvent("", false, 0L, 0L);
    }

    /** Projects a {@link LevelEventJS} (including {@code LevelSavedEventJS}) into a {@link StableLevelEvent}. */
    public static StableLevelEvent levelEvent(LevelEventJS event) {
        return levelSnapshot(event == null ? null : event.getLevel());
    }

    /** Projects an explosion start wrapper event into a {@link StableExplosionEvent}. */
    public static StableExplosionEvent explosionStart(LevelExplosionEventJS event) {
        return explosionSnapshot(event);
    }

    /** Projects an explosion detonation wrapper event into a {@link StableExplosionEvent}. */
    public static StableExplosionEvent explosionDetonate(LevelExplosionEventJS event) {
        return explosionSnapshot(event);
    }

    private static StableExplosionEvent explosionSnapshot(LevelExplosionEventJS event) {
        if (event == null) {
            return new StableExplosionEvent("", 0.0, 0.0, 0.0, 0.0f, 0, 0);
        }
        boolean missingExplosion = event.getExplosion() == null && event.getClass() == LevelExplosionEventJS.class;
        Vec3 center = missingExplosion ? null : event.getCenter();
        float radius = missingExplosion ? 0.0f : event.getRadius();
        var detonation = event instanceof FabricLevelEventBindingsV2.ExplosionDetonation data ? data : null;
        return new StableExplosionEvent(
                dimensionOf(event.getLevel()),
                center == null ? 0.0 : center.x,
                center == null ? 0.0 : center.y,
                center == null ? 0.0 : center.z,
                radius,
                detonation == null ? 0 : detonation.blockCount,
                detonation == null ? 0 : detonation.entityCount);
    }

    /** Projects a {@link Player} into a {@link StablePlayerEvent} snapshot. */
    public static StablePlayerEvent playerSnapshot(Player player) {
        if (player == null) {
            return new StablePlayerEvent("", "", "", 0.0, 0.0, 0.0);
        }
        return new StablePlayerEvent(
                player.getScoreboardName(),
                player.getStringUUID(),
                dimensionOf(player.level()),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    /** Projects a player login/logout wrapper event into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent playerLifecycle(PlayerLifecycleEventJS e) {
        return playerSnapshot(e == null ? null : e.getPlayer());
    }

    /** Projects a player tick wrapper event into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent playerTick(PlayerTickEventJS e) {
        return playerSnapshot(e == null ? null : e.getPlayer());
    }

    /** Projects a player clone wrapper event into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent playerClone(PlayerCloneEventJS e) {
        return playerSnapshot(e == null ? null : e.getPlayer());
    }

    /** Projects a player respawn wrapper event into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent playerRespawn(PlayerRespawnEventJS e) {
        return playerSnapshot(e == null ? null : e.getPlayer());
    }

    /** Projects a player container open/close wrapper event into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent playerContainer(PlayerContainerEventJS e) {
        return playerSnapshot(e == null ? null : e.getPlayer());
    }

    /** Projects a player entity interaction wrapper event into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent playerEntityInteract(
            com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS e) {
        return playerSnapshot(e == null ? null : e.getPlayer());
    }

    /** Projects a server chat wrapper event into a {@link StablePlayerChatEvent}. */
    public static StablePlayerChatEvent playerChat(ServerChatEventJS e) {
        if (e == null) {
            return new StablePlayerChatEvent("", "", "");
        }
        Player p = e.getPlayer();
        return new StablePlayerChatEvent(
                e.getUsername(),
                p == null ? "" : p.getStringUUID(),
                e.getMessage());
    }

    /** Projects a player dimension change wrapper event into a {@link StablePlayerDimensionChangeEvent}. */
    public static StablePlayerDimensionChangeEvent playerChangedDimension(PlayerChangedDimensionEventJS e) {
        if (e == null) {
            return new StablePlayerDimensionChangeEvent("", "", "", "");
        }
        Player p = e.getPlayer();
        ResourceKey<Level> from = e.getFrom();
        ResourceKey<Level> to = e.getTo();
        return new StablePlayerDimensionChangeEvent(
                p == null ? "" : p.getScoreboardName(),
                p == null ? "" : p.getStringUUID(),
                from == null ? "" : from.identifier().toString(),
                to == null ? "" : to.identifier().toString());
    }

    /** Projects a player advancement wrapper event into a {@link StablePlayerAdvancementEvent}. */
    public static StablePlayerAdvancementEvent playerAdvancement(PlayerAdvancementEventJS e) {
        if (e == null) {
            return new StablePlayerAdvancementEvent("", "", "");
        }
        Player p = e.getPlayer();
        Identifier id = e.getId();
        return new StablePlayerAdvancementEvent(
                p == null ? "" : p.getScoreboardName(),
                p == null ? "" : p.getStringUUID(),
                id == null ? "" : id.toString());
    }

    /** Projects a player and item stack pair into a {@link StablePlayerItemEvent}. */
    public static StablePlayerItemEvent playerItem(Player player, ItemStack stack) {
        String itemId = stack == null || stack.isEmpty()
                ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        int count = stack == null ? 0 : stack.getCount();
        return new StablePlayerItemEvent(
                player == null ? "" : player.getScoreboardName(),
                player == null ? "" : player.getStringUUID(),
                itemId,
                count);
    }

    /** Projects a crafting or smelting wrapper event into a {@link StablePlayerItemEvent}. */
    public static StablePlayerItemEvent playerCrafted(PlayerCraftedEventJS e) {
        return e == null ? playerItem(null, null) : playerItem(e.getPlayer(), e.getResult());
    }

    /** Projects an item destruction wrapper event into a {@link StablePlayerItemEvent}. */
    public static StablePlayerItemEvent playerDestroyed(PlayerDestroyItemEventJS e) {
        return e == null ? playerItem(null, null) : playerItem(e.getPlayer(), e.getItemStack());
    }

    /** Projects a server command registration wrapper event into a {@link StableCommandRegistrationEvent}. */
    public static StableCommandRegistrationEvent commandRegister(CommandRegistryEventJS e) {
        return new StableCommandRegistrationEvent(
                e == null || e.getCommandSelection() == null ? "" : e.getCommandSelection());
    }

    /** Projects Brigadier parse results and optional raw command text into a {@link StableCommandExecuteEvent}. */
    public static StableCommandExecuteEvent commandExecute(
            ParseResults<CommandSourceStack> parseResults, String command) {
        String input = parseResults == null || parseResults.getReader() == null
                ? command
                : parseResults.getReader().getString();
        String sourceName = parseResults == null
                || parseResults.getContext() == null
                || parseResults.getContext().getSource() == null
                ? ""
                : parseResults.getContext().getSource().getTextName();
        return new StableCommandExecuteEvent(input, sourceName);
    }

    /** Projects block context fields into a {@link StableBlockEvent} snapshot. */
    public static StableBlockEvent blockSnapshot(
            LevelAccessor level, BlockPos pos, Block block, Entity actor) {
        String blockId = block == null ? "" : String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
        String actorName = actor == null ? "" : actor.getScoreboardName();
        return new StableBlockEvent(
                dimensionOf(level),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actorName);
    }

    /** Projects a block broken wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockBroken(BlockBrokenEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockBrokenEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, e.getPlayer());
    }

    /** Projects a block right-click wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockRightClick(BlockRightClickEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockRightClickEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, e.getPlayer());
    }

    /** Projects a block left-click wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockLeftClick(BlockLeftClickEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockLeftClickEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, e.getPlayer());
    }

    /** Projects a portal spawn wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockPortalSpawn(BlockPortalSpawnEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockPortalSpawnEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, null);
    }

    /** Projects a block neighbor notify wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockNeighborNotify(BlockNeighborNotifyEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockNeighborNotifyEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, null);
    }

    /** Projects a farmland trample wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockFarmlandTrample(BlockFarmlandTrampleEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockFarmlandTrampleEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, e.getEntity());
    }

    /** Projects a block placed wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockPlaced(BlockPlacedEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockPlacedEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, e.getEntity());
    }

    /** Projects a fluid-placed block wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockFluidPlaced(BlockFluidPlacedEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getNewState() == null && e.getClass() == BlockFluidPlacedEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, null);
    }

    /** Projects a block random tick wrapper event into a {@link StableBlockEvent}. */
    public static StableBlockEvent blockRandomTick(BlockRandomTickEventJS e) {
        if (e == null) {
            return blockSnapshot(null, null, null, null);
        }
        Block block = (e.getState() == null && e.getClass() == BlockRandomTickEventJS.class) ? null : e.getBlock();
        return blockSnapshot(e.getLevel(), e.getPos(), block, null);
    }

    /** Projects a block entity tick wrapper event into a {@link StableBlockEvent} keyed by block entity type id. */
    public static StableBlockEvent blockEntityTick(BlockEntityTickEventJS e) {
        if (e == null) {
            return new StableBlockEvent("", 0, 0, 0, "", "");
        }
        BlockEntity be = e.getBlockEntity();
        var actual = e instanceof BlockEntityTickEventJS.Actual tick ? tick : null;
        BlockPos pos = actual != null ? actual.pos() : be == null ? null : be.getBlockPos();
        Level lvl = actual != null ? actual.level() : be == null ? null : be.getLevel();
        BlockEntityType<?> type = be != null
                ? e.getType()
                : (e.getClass() == BlockEntityTickEventJS.class ? null : e.getType());
        Identifier key = type == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        return new StableBlockEvent(
                dimensionOf(lvl),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                key == null ? "" : key.toString(),
                "");
    }

    /** Projects an item stack, acting entity, and level into a {@link StableItemEvent}. */
    public static StableItemEvent itemStackEvent(ItemStack stack, Entity actor, Level level) {
        String itemId = stack == null || stack.isEmpty()
                ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        int count = stack == null ? 0 : stack.getCount();
        String actorName = actor == null ? "" : actor.getScoreboardName();
        return new StableItemEvent(itemId, count, actorName, dimensionOf(level));
    }

    /** Projects an item right-click wrapper event into a {@link StableItemEvent}. */
    public static StableItemEvent itemRightClick(ItemRightClickEventJS e) {
        if (e == null) {
            return itemStackEvent(null, null, null);
        }
        Player player = e.getPlayer();
        Level level = e.getLevel() != null ? e.getLevel() : (player == null ? null : player.level());
        return itemStackEvent(e.getItemStack(), player, level);
    }

    /** Projects a client item tooltip wrapper event into a {@link StableItemEvent} with empty actor and dimension. */
    public static StableItemEvent itemTooltip(ItemTooltipEventJS e) {
        return itemStackEvent(e == null ? null : e.getItemStack(), null, null);
    }

    /** Projects an item pickup wrapper event into a {@link StableItemEvent}. */
    public static StableItemEvent itemPickup(ItemEntityPickupEventJS e) {
        if (e == null) {
            return itemStackEvent(null, null, null);
        }
        Player player = e.getPlayer();
        ItemEntity itemEntity = e.getItemEntity();
        ItemStack stack = itemEntity != null
                ? e.getItem()
                : (e.getClass() == ItemEntityPickupEventJS.class ? null : e.getItem());
        Level level = player != null ? player.level() : (itemEntity == null ? null : itemEntity.level());
        return itemStackEvent(stack, player, level);
    }

    /** Projects an item dropped wrapper event into a {@link StableItemEvent}. */
    public static StableItemEvent itemDropped(ItemDroppedEventJS e) {
        if (e == null) {
            return itemStackEvent(null, null, null);
        }
        Player player = e.getPlayer();
        return itemStackEvent(e.getItem(), player, player == null ? null : player.level());
    }

    /** Projects an item-dispatched player entity interaction wrapper event into a {@link StableItemEvent}. */
    public static StableItemEvent itemEntityInteract(
            com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS e) {
        if (e == null) {
            return itemStackEvent(null, null, null);
        }
        Player player = e.getPlayer();
        Entity target = e.getTarget();
        Level level = player != null ? player.level() : (target == null ? null : target.level());
        return itemStackEvent(e.getItem(), player, level);
    }

    /** Projects a finished item use wrapper event into a {@link StableItemEvent}. */
    public static StableItemEvent itemUseFinished(ItemUseFinishedEventJS e) {
        if (e == null) {
            return itemStackEvent(null, null, null);
        }
        LivingEntity entity = e.getEntity();
        return itemStackEvent(e.getItem(), entity, entity == null ? null : entity.level());
    }

    /** Projects an entity and numeric amount into a {@link StableEntityEvent} snapshot. */
    public static StableEntityEvent entityEvent(Entity entity, float amount) {
        return entityEvent(entity, entity == null ? null : entity.level(), amount);
    }

    private static StableEntityEvent entityEvent(Entity entity, LevelAccessor level, float amount) {
        if (entity == null) {
            return new StableEntityEvent("", "", "", 0.0, 0.0, 0.0, amount);
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return new StableEntityEvent(
                key == null ? "" : key.toString(),
                entity.getStringUUID(),
                dimensionOf(level),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                amount);
    }

    /** Projects an entity join-level wrapper event into a {@link StableEntityEvent}. */
    public static StableEntityEvent entityJoin(EntityJoinLevelEventJS e) {
        return e == null ? entityEvent(null, 0.0f) : entityEvent(e.getEntity(), e.getLevel(), 0.0f);
    }

    /** Projects an entity leave-level wrapper event into a {@link StableEntityEvent}. */
    public static StableEntityEvent entityLeave(EntityLeaveLevelEventJS e) {
        return e == null ? entityEvent(null, 0.0f) : entityEvent(e.getEntity(), e.getLevel(), 0.0f);
    }

    /** Projects an entity tick wrapper event into a {@link StableEntityEvent}. */
    public static StableEntityEvent entityTick(EntityTickEventJS e) {
        return entityEvent(e == null ? null : e.getEntity(), 0.0f);
    }

    /** Projects a living death wrapper event into a {@link StableEntityEvent}. */
    public static StableEntityEvent livingDeath(LivingDeathEventJS e) {
        return entityEvent(e == null ? null : e.getEntity(), 0.0f);
    }

    /** Projects a living damage wrapper event into a {@link StableEntityEvent} using {@code e.getAmount()}. */
    public static StableEntityEvent livingDamage(LivingDamageEventJS e) {
        return e == null ? entityEvent(null, 0.0f) : entityEvent(e.getEntity(), e.getAmount());
    }

    /** Projects a living drops wrapper event into a {@link StableEntityEvent}. */
    public static StableEntityEvent livingDrops(LivingDropsEventJS e) {
        return entityEvent(e == null ? null : e.getEntity(), 0.0f);
    }

    /** Projects a mob finalize-spawn wrapper event into a {@link StableEntityEvent} using {@code e.getMob()}. */
    public static StableEntityEvent mobFinalizeSpawn(MobFinalizeSpawnEventJS e) {
        return entityEvent(e == null ? null : e.getMob(), 0.0f);
    }
}
