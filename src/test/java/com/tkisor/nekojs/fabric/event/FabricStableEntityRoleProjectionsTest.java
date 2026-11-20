//? if fabric {
/*package com.tkisor.nekojs.fabric.event;

import com.mojang.authlib.GameProfile;
import com.tkisor.nekojs.api.entity.StableServerPlayer;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import com.tkisor.nekojs.wrapper.entity.StableEntities;
import com.tkisor.nekojs.wrapper.event.entity.LivingDamageEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDropsEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCloneEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerRespawnEventJS;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricStableEntityRoleProjectionsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cloneAndRespawnKeepDistinctNativePlayersWithTheSameUuid() throws Exception {
        var uuid = UUID.randomUUID();
        var replacement = player(ServerPlayer.class, uuid);
        var original = player(ServerPlayer.class, uuid);
        var clone = FabricStableEventProjections.playerClone(new PlayerCloneEventJS(replacement, original, false));
        var respawn = FabricStableEventProjections.playerRespawn(new PlayerRespawnEventJS(replacement, original, false));
        for (var event : List.of(clone, respawn)) {
            assertEquals(uuid.toString(), event.getPlayerUuid());
            assertInstanceOf(StableServerPlayer.class, event.getPlayer());
            assertSame(replacement, StableEntities.unwrap(event.getPlayer()));
            assertSame(original, StableEntities.unwrap(event.getOriginalPlayer()));
            assertNull(event.getTarget());
        }
        assertNull(FabricStableEventProjections.playerClone(
                new PlayerCloneEventJS(replacement, replacement, true)).getOriginalPlayer());
        assertNull(FabricStableEventProjections.playerRespawn(
                new PlayerRespawnEventJS(replacement, null, true)).getOriginalPlayer());
    }

    @Test
    void livingDamageDeathAndDropsKeepVictimAndDamageSourcesDistinct() throws Exception {
        var victim = entity(Pig.class, FabricStableEventProjections.resolveEntityTypeSelector("minecraft:pig"), UUID.randomUUID());
        var attacker = player(ServerPlayer.class, UUID.randomUUID());
        var direct = entity(ItemEntity.class, FabricStableEventProjections.resolveEntityTypeSelector("minecraft:item"), UUID.randomUUID());
        var type = Holder.direct(new DamageType("fabric_test", 0.0f));
        var source = new DamageSource(type, direct, attacker);
        var hit = FabricStableEventProjections.livingDamage(new LivingDamageEventJS(victim, source, 4.5f));
        var death = FabricStableEventProjections.livingDeath(new LivingDeathEventJS(victim, source));
        var drops = FabricStableEventProjections.livingDrops(new LivingDropsEventJS(victim, source, List.of()));
        assertEquals(4.5f, hit.getAmount());
        for (StableEntityEvent event : List.of(hit, death, drops)) {
            assertSame(victim, StableEntities.unwrap(event.getEntity()));
            assertSame(attacker, StableEntities.unwrap(event.getSourceEntity()));
            assertSame(direct, StableEntities.unwrap(event.getDirectSourceEntity()));
        }
        var environment = FabricStableEventProjections.livingDamage(
                new LivingDamageEventJS(victim, new DamageSource(type), 1.0f));
        assertSame(victim, StableEntities.unwrap(environment.getEntity()));
        assertNull(environment.getSourceEntity());
        assertNull(environment.getDirectSourceEntity());
    }

    @Test
    void interactionsUseTheNativeTargetAndAllowRolesToBeAbsent() throws Exception {
        var player = player(ServerPlayer.class, UUID.randomUUID());
        var target = entity(Pig.class, FabricStableEventProjections.resolveEntityTypeSelector("minecraft:pig"), UUID.randomUUID());
        var playerInteraction = FabricStableEventProjections.playerEntityInteract(
                new PlayerEntityInteractEventJS(player, target, InteractionHand.MAIN_HAND));
        var itemInteraction = FabricStableEventProjections.itemEntityInteract(
                new com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS(
                        player, target, ItemStack.EMPTY, InteractionHand.MAIN_HAND));
        assertSame(player, StableEntities.unwrap(playerInteraction.getPlayer()));
        assertSame(target, StableEntities.unwrap(playerInteraction.getTarget()));
        assertSame(player, StableEntities.unwrap(itemInteraction.getActor()));
        assertSame(target, StableEntities.unwrap(itemInteraction.getTarget()));
        assertSame(target, StableEntities.unwrap(
                FabricStableEventProjections.blockSnapshot(null, null, null, target).getActor()));
        assertNull(FabricStableEventProjections.blockSnapshot(null, null, null, null).getActor());
        assertNull(FabricStableEventProjections.itemDropped(new ItemDroppedEventJS(player, ItemStack.EMPTY)).getTarget());
        assertNull(FabricStableEventProjections.itemTooltip(new ItemTooltipEventJS(ItemStack.EMPTY, List.of())).getActor());
    }

    @Test
    void clientCloneAndInputWrapTheActualLocalPlayers() throws Exception {
        var uuid = UUID.randomUUID();
        var local = player(LocalPlayer.class, uuid);
        var original = player(LocalPlayer.class, uuid);
        var clone = FabricClientStableEventProjections.clientPlayer(local, original);
        assertSame(local, StableEntities.unwrap(clone.getPlayer()));
        assertSame(original, StableEntities.unwrap(clone.getOriginalPlayer()));
        assertNull(FabricClientStableEventProjections.clientPlayer(local, local).getOriginalPlayer());

        var input = FabricClientStableEventProjections.clientInteractionKey(false, true, false, InteractionHand.OFF_HAND, local);
        assertTrue(input.isUseItem());
        assertEquals("OFF_HAND", input.getHand());
        assertSame(local, StableEntities.unwrap(input.getPlayer()));
        assertNull(FabricClientStableEventProjections.clientInteractionKey(true, false, false, null, null).getPlayer());
    }

    private static <P extends Player> P player(Class<P> entityClass, UUID uuid) throws Exception {
        P player = entity(entityClass, FabricStableEventProjections.resolveEntityTypeSelector("minecraft:player"), uuid);
        var profile = Player.class.getDeclaredField("gameProfile");
        profile.setAccessible(true);
        profile.set(player, new GameProfile(uuid, "Player"));
        return player;
    }

    @SuppressWarnings("removal")
    private static <E extends Entity> E entity(Class<E> entityClass, EntityType<?> type, UUID uuid) throws Exception {
        // Allocate native fixtures without constructing a world; projections read only the initialized fields.
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        E entity = entityClass.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(entityClass));
        var entityType = Entity.class.getDeclaredField("type");
        entityType.setAccessible(true);
        entityType.set(entity, type);
        var position = Entity.class.getDeclaredField("position");
        position.setAccessible(true);
        position.set(entity, Vec3.ZERO);
        entity.setUUID(uuid);
        return entity;
    }
}
*///?}
