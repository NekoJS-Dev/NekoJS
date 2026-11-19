//~ mc_legacy_api
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.StableBlockEvent;
import com.tkisor.nekojs.api.event.StableCommandExecuteEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.api.event.StableDatapackSyncEvent;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import com.tkisor.nekojs.api.event.StableExplosionEvent;
import com.tkisor.nekojs.api.event.StableItemEvent;
import com.tkisor.nekojs.api.event.StableLevelEvent;
import com.tkisor.nekojs.api.event.StableLootTableLoadEvent;
import com.tkisor.nekojs.api.event.StablePlayerAdvancementEvent;
import com.tkisor.nekojs.api.event.StablePlayerChatEvent;
import com.tkisor.nekojs.api.event.StablePlayerDimensionChangeEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.api.event.StablePlayerItemEvent;
import com.tkisor.nekojs.api.event.StableServerLifecycleEvent;
import com.tkisor.nekojs.api.event.StableTagsUpdatedEvent;
import com.tkisor.nekojs.event.level.BlockEntityTickEvent;
import com.tkisor.nekojs.event.level.RandomTickEvent;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerLifecycleEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Dedicated-server-safe projections from NeoForge native and wrapper events to portable stable event
 * snapshots, plus string selector resolvers for registry-backed dispatch keys.
 */
public final class NeoForgeStableEventProjections {
    private NeoForgeStableEventProjections() {}

    public static Identifier parseSelectorId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4057] Event selector id must not be blank");
        }
        String trimmed = raw.trim();
        return trimmed.contains(":")
                ? Identifier.parse(trimmed)
                : Identifier.fromNamespaceAndPath("minecraft", trimmed);
    }

    public static Block resolveBlockSelector(String id) {
        return BuiltInRegistries.BLOCK
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown block selector: " + id));
    }

    public static Item resolveItemSelector(String id) {
        return BuiltInRegistries.ITEM
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown item selector: " + id));
    }

    public static EntityType<?> resolveEntityTypeSelector(String id) {
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown entity type selector: " + id));
    }

    public static BlockEntityType<?> resolveBlockEntityTypeSelector(String id) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getOptional(parseSelectorId(id))
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4057] Unknown block entity type selector: " + id));
    }

    public static StableServerLifecycleEvent serverLifecycle(String stage, ServerLifecycleEvent event) {
        return new StableServerLifecycleEvent(stage, event.getServer().getTickCount());
    }

    public static StableDatapackSyncEvent datapackSync(OnDatapackSyncEvent event) {
        var player = event.getPlayer();
        int count = player != null ? 1 : (event.getPlayerList() == null ? 0 : event.getPlayerList().getPlayers().size());
        return new StableDatapackSyncEvent(player == null ? "" : player.getScoreboardName(), count, player == null);
    }

    public static StableTagsUpdatedEvent tagsUpdated(TagsUpdatedEvent event) {
        var cause = event.getUpdateCause();
        return new StableTagsUpdatedEvent(
                cause == null ? "" : cause.name(),
                cause == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED);
    }

    public static StableLootTableLoadEvent lootTableLoad(LootTableLoadEvent event) {
        return new StableLootTableLoadEvent(event.getName() == null ? "" : event.getName().toString());
    }

    public static String dimensionOf(LevelAccessor access) {
        return access instanceof Level lvl && lvl.dimension() != null
                ? lvl.dimension().identifier().toString()
                : "";
    }

    private static long dayTimeOf(Level lvl) {
        //? if >=26 {
        return lvl.getOverworldClockTime();
        //?} else {
        /*return lvl.getDayTime();*/
        //?}
    }

    public static StableLevelEvent levelEvent(LevelEvent event) {
        LevelAccessor access = event.getLevel();
        if (access instanceof Level lvl) {
            return new StableLevelEvent(dimensionOf(lvl), access.isClientSide(), lvl.getGameTime(), dayTimeOf(lvl));
        }
        return new StableLevelEvent("", access != null && access.isClientSide(), 0L, 0L);
    }

    public static StableLevelEvent levelTick(LevelTickEvent event) {
        Level lvl = event.getLevel();
        return new StableLevelEvent(dimensionOf(lvl), lvl.isClientSide(), lvl.getGameTime(), dayTimeOf(lvl));
    }

    public static StableExplosionEvent explosionStart(ExplosionEvent.Start event) {
        Vec3 c = event.getExplosion().center();
        return new StableExplosionEvent(
                dimensionOf(event.getLevel()), c.x, c.y, c.z, event.getExplosion().radius(), 0, 0);
    }

    public static StableExplosionEvent explosionDetonate(ExplosionEvent.Detonate event) {
        Vec3 c = event.getExplosion().center();
        return new StableExplosionEvent(
                dimensionOf(event.getLevel()),
                c.x,
                c.y,
                c.z,
                event.getExplosion().radius(),
                event.getAffectedBlocks().size(),
                event.getAffectedEntities().size());
    }

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

    public static StablePlayerEvent player(PlayerEvent event) {
        return playerSnapshot(event.getEntity());
    }

    public static StablePlayerChatEvent playerChat(ServerChatEvent event) {
        var p = event.getPlayer();
        return new StablePlayerChatEvent(event.getUsername(), p == null ? "" : p.getStringUUID(), event.getRawText());
    }

    public static StablePlayerDimensionChangeEvent playerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        Player p = event.getEntity();
        String from = event.getFrom() == null ? "" : event.getFrom().identifier().toString();
        String to = event.getTo() == null ? "" : event.getTo().identifier().toString();
        return new StablePlayerDimensionChangeEvent(
                p == null ? "" : p.getScoreboardName(),
                p == null ? "" : p.getStringUUID(),
                from,
                to);
    }

    public static StablePlayerAdvancementEvent playerAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        Player p = event.getEntity();
        String advId = event.getAdvancement() == null ? "" : event.getAdvancement().id().toString();
        return new StablePlayerAdvancementEvent(
                p == null ? "" : p.getScoreboardName(),
                p == null ? "" : p.getStringUUID(),
                advId);
    }

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

    public static StableCommandRegistrationEvent commandRegister(RegisterCommandsEvent event) {
        return new StableCommandRegistrationEvent(
                event.getCommandSelection() == null ? "" : event.getCommandSelection().name());
    }

    public static StableCommandExecuteEvent commandExecute(CommandEvent event) {
        var parse = event.getParseResults();
        String input = parse == null ? "" : parse.getReader().getString();
        String sourceName = parse == null ? "" : parse.getContext().getSource().getTextName();
        return new StableCommandExecuteEvent(input, sourceName);
    }

    public static StableBlockEvent blockBroken(BlockBrokenEventJS event) {
        BlockPos pos = event.getPos();
        String blockId = event.getBlock() == null ? "" : String.valueOf(BuiltInRegistries.BLOCK.getKey(event.getBlock()));
        String actor = event.getPlayer() == null ? "" : event.getPlayer().getScoreboardName();
        return new StableBlockEvent(
                dimensionOf(event.getLevel()),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actor);
    }

    public static StableBlockEvent blockEvent(BlockEvent event) {
        BlockPos pos = event.getPos();
        String blockId = event.getState() == null
                ? ""
                : String.valueOf(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()));
        String actor = "";
        if (event instanceof BlockEvent.EntityPlaceEvent ep && ep.getEntity() != null) {
            actor = ep.getEntity().getScoreboardName();
        } else if (event instanceof BlockEvent.FarmlandTrampleEvent ft && ft.getEntity() != null) {
            actor = ft.getEntity().getScoreboardName();
        } else if (event instanceof BlockEvent.BlockToolModificationEvent tm && tm.getPlayer() != null) {
            actor = tm.getPlayer().getScoreboardName();
        }
        return new StableBlockEvent(
                dimensionOf(event.getLevel()),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actor);
    }

    public static StableBlockEvent blockInteract(PlayerInteractEvent event) {
        Level lvl = event.getLevel();
        BlockPos pos = event.getPos();
        String blockId = lvl == null || pos == null
                ? ""
                : String.valueOf(BuiltInRegistries.BLOCK.getKey(lvl.getBlockState(pos).getBlock()));
        String actor = event.getEntity() == null ? "" : event.getEntity().getScoreboardName();
        return new StableBlockEvent(
                dimensionOf(lvl),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actor);
    }

    public static StableBlockEvent randomTick(RandomTickEvent event) {
        BlockPos pos = event.getPos();
        String blockId = event.getBlock() == null ? "" : String.valueOf(BuiltInRegistries.BLOCK.getKey(event.getBlock()));
        return new StableBlockEvent(
                dimensionOf(event.getLevel()),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                "");
    }

    public static StableBlockEvent blockEntityTick(BlockEntityTickEvent event) {
        BlockEntity be = event.getBlockEntity();
        var actual = event instanceof BlockEntityTickEvent.Actual tick ? tick : null;
        BlockPos pos = actual != null ? actual.pos() : be == null ? null : be.getBlockPos();
        Level lvl = actual != null ? actual.level() : be == null ? null : be.getLevel();
        BlockEntityType<?> type = be != null
                ? event.getType()
                : (event.getClass() == BlockEntityTickEvent.class ? null : event.getType());
        Identifier key = type == null ? null : BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        return new StableBlockEvent(
                dimensionOf(lvl),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                key == null ? "" : key.toString(),
                "");
    }

    public static StableItemEvent itemStackEvent(ItemStack stack, Entity actor, Level level) {
        String itemId = stack == null || stack.isEmpty()
                ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        int count = stack == null ? 0 : stack.getCount();
        String actorName = actor == null ? "" : actor.getScoreboardName();
        return new StableItemEvent(itemId, count, actorName, dimensionOf(level));
    }

    public static StableEntityEvent entityEvent(Entity entity, float amount) {
        return entityEvent(entity, entity == null ? null : entity.level(), amount);
    }

    public static StableEntityEvent entityEvent(Entity entity, LevelAccessor level, float amount) {
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
}
//?}
