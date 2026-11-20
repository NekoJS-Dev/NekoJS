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
import com.tkisor.nekojs.wrapper.entity.StableEntities;
import com.tkisor.nekojs.wrapper.event.block.BlockBrokenEventJS;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
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
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerLifecycleEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Dedicated-server-safe NeoForge projections for portable event snapshots and live entity roles,
 * plus string selector resolvers for registry-backed dispatch keys.
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
        return new StableDatapackSyncEvent(
                player == null ? "" : player.getScoreboardName(), count, player == null, StableEntities.wrap(player));
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
        var explosion = event.getExplosion();
        Vec3 c = explosion.center();
        return new StableExplosionEvent(
                dimensionOf(event.getLevel()),
                c.x,
                c.y,
                c.z,
                explosion.radius(),
                0,
                0,
                StableEntities.wrap(explosion.getDirectSourceEntity()),
                StableEntities.wrap(explosion.getIndirectSourceEntity()));
    }

    public static StableExplosionEvent explosionDetonate(ExplosionEvent.Detonate event) {
        var explosion = event.getExplosion();
        Vec3 c = explosion.center();
        return new StableExplosionEvent(
                dimensionOf(event.getLevel()),
                c.x,
                c.y,
                c.z,
                explosion.radius(),
                event.getAffectedBlocks().size(),
                event.getAffectedEntities().size(),
                StableEntities.wrap(explosion.getDirectSourceEntity()),
                StableEntities.wrap(explosion.getIndirectSourceEntity()));
    }

    public static StablePlayerEvent playerSnapshot(Player player) {
        return playerSnapshot(player, null, null);
    }

    private static StablePlayerEvent playerSnapshot(Player player, Player originalPlayer, Entity target) {
        var original = StableEntities.wrap(originalPlayer == player ? null : originalPlayer);
        var interactedEntity = StableEntities.wrap(target);
        if (player == null) {
            return new StablePlayerEvent("", "", "", 0.0, 0.0, 0.0, null, original, interactedEntity);
        }
        return new StablePlayerEvent(
                player.getScoreboardName(),
                player.getStringUUID(),
                dimensionOf(player.level()),
                player.getX(),
                player.getY(),
                player.getZ(),
                StableEntities.wrap(player),
                original,
                interactedEntity);
    }

    public static StablePlayerEvent player(PlayerEvent event) {
        Player original = event instanceof PlayerEvent.Clone clone ? clone.getOriginal() : null;
        Entity target = event instanceof PlayerInteractEvent.EntityInteract interaction ? interaction.getTarget() : null;
        return playerSnapshot(event.getEntity(), original, target);
    }

    public static StablePlayerChatEvent playerChat(ServerChatEvent event) {
        var p = event.getPlayer();
        return new StablePlayerChatEvent(
                event.getUsername(), p == null ? "" : p.getStringUUID(), event.getRawText(), StableEntities.wrap(p));
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
                to,
                StableEntities.wrap(p));
    }

    public static StablePlayerAdvancementEvent playerAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        Player p = event.getEntity();
        String advId = event.getAdvancement() == null ? "" : event.getAdvancement().id().toString();
        return new StablePlayerAdvancementEvent(
                p == null ? "" : p.getScoreboardName(),
                p == null ? "" : p.getStringUUID(),
                advId,
                StableEntities.wrap(p));
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
                count,
                StableEntities.wrap(player));
    }

    public static StableCommandRegistrationEvent commandRegister(RegisterCommandsEvent event) {
        return new StableCommandRegistrationEvent(
                event.getCommandSelection() == null ? "" : event.getCommandSelection().name());
    }

    public static StableCommandExecuteEvent commandExecute(CommandEvent event) {
        var parse = event.getParseResults();
        String input = parse == null ? "" : parse.getReader().getString();
        var source = parse == null ? null : parse.getContext().getSource();
        String sourceName = source == null ? "" : source.getTextName();
        return new StableCommandExecuteEvent(input, sourceName,
                StableEntities.wrap(source == null ? null : source.getEntity()));
    }

    public static StableBlockEvent blockBroken(BlockBrokenEventJS event) {
        BlockPos pos = event.getPos();
        String blockId = event.getBlock() == null ? "" : String.valueOf(BuiltInRegistries.BLOCK.getKey(event.getBlock()));
        Player actor = event.getPlayer();
        return new StableBlockEvent(
                dimensionOf(event.getLevel()),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actor == null ? "" : actor.getScoreboardName(),
                StableEntities.wrap(actor));
    }

    public static StableBlockEvent blockEvent(BlockEvent event) {
        BlockPos pos = event.getPos();
        String blockId = event.getState() == null
                ? ""
                : String.valueOf(BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()));
        Entity actor = null;
        if (event instanceof BlockEvent.EntityPlaceEvent placement) {
            actor = placement.getEntity();
        } else if (event instanceof BlockEvent.FarmlandTrampleEvent trample) {
            actor = trample.getEntity();
        } else if (event instanceof BlockEvent.BlockToolModificationEvent modification) {
            actor = modification.getPlayer();
        }
        return new StableBlockEvent(
                dimensionOf(event.getLevel()),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actor == null ? "" : actor.getScoreboardName(),
                StableEntities.wrap(actor));
    }

    public static StableBlockEvent blockInteract(PlayerInteractEvent event) {
        Level lvl = event.getLevel();
        BlockPos pos = event.getPos();
        String blockId = lvl == null || pos == null
                ? ""
                : String.valueOf(BuiltInRegistries.BLOCK.getKey(lvl.getBlockState(pos).getBlock()));
        Player actor = event.getEntity();
        return new StableBlockEvent(
                dimensionOf(lvl),
                pos == null ? 0 : pos.getX(),
                pos == null ? 0 : pos.getY(),
                pos == null ? 0 : pos.getZ(),
                blockId,
                actor == null ? "" : actor.getScoreboardName(),
                StableEntities.wrap(actor));
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
        return itemStackEvent(stack, actor, level, null);
    }

    static StableItemEvent itemStackEvent(ItemStack stack, Entity actor, Level level, Entity target) {
        String actorName = actor == null ? "" : actor.getScoreboardName();
        return itemStackEvent(stack, actorName, dimensionOf(level), actor, target);
    }

    static StableItemEvent itemTooltip(ItemTooltipEvent event) {
        // The native viewer is optional; tooltip snapshot fields remain independent of it.
        return itemStackEvent(event.getItemStack(), "", "", event.getEntity(), null);
    }

    private static StableItemEvent itemStackEvent(
            ItemStack stack, String actorName, String dimension, Entity actor, Entity target) {
        String itemId = stack == null || stack.isEmpty()
                ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        int count = stack == null ? 0 : stack.getCount();
        return new StableItemEvent(itemId, count, actorName, dimension, StableEntities.wrap(actor), StableEntities.wrap(target));
    }

    public static StableEntityEvent entityEvent(Entity entity, float amount) {
        return entityEvent(entity, entity == null ? null : entity.level(), amount);
    }

    public static StableEntityEvent entityEvent(Entity entity, LevelAccessor level, float amount) {
        return entityEvent(entity, level, amount, null);
    }

    static StableEntityEvent entityDamage(Entity entity, DamageSource source, float amount) {
        return entityEvent(entity, entity == null ? null : entity.level(), amount, source);
    }

    private static StableEntityEvent entityEvent(Entity entity, LevelAccessor level, float amount, DamageSource source) {
        var sourceEntity = StableEntities.wrap(source == null ? null : source.getEntity());
        var directSourceEntity = StableEntities.wrap(source == null ? null : source.getDirectEntity());
        if (entity == null) {
            return new StableEntityEvent("", "", "", 0.0, 0.0, 0.0, amount, null, sourceEntity, directSourceEntity);
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return new StableEntityEvent(
                key == null ? "" : key.toString(),
                entity.getStringUUID(),
                dimensionOf(level),
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                amount,
                StableEntities.wrap(entity),
                sourceEntity,
                directSourceEntity);
    }
}
//?}
