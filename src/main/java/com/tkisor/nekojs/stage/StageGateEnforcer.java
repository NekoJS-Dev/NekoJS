package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageReadView;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.RegionGate;
import com.tkisor.nekojs.core.stage.ClientStageMirror;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared loader-neutral stage gate enforcement helper with throttled player feedback.
 *
 * <p>Evaluates item, block, crop, recipe, crafter, container, dimension, region, entity, mob effect,
 * enchantment, anvil, loot drop, and client tooltip checks against {@link StageDomainOwner#activeSnapshot()}
 * and {@link ClientStageMirror#SHARED}. Locked actions send an action-bar message at most once per
 * {@code 20} game ticks per {@code (UUID, actionKey)} pair.
 */
public final class StageGateEnforcer {

    private static final long FEEDBACK_COOLDOWN_TICKS = 20L;
    private static final Map<CooldownKey, Long> FEEDBACK_COOLDOWNS = new ConcurrentHashMap<>();

    private StageGateEnforcer() {
    }

    /** Paired player and server stage views used when evaluating a gate query. */
    public record StageViews(StageReadView player, StageReadView server) {
    }

    /**
     * Resolves the {@link StageViews} for {@code player}:
     * <ul>
     *   <li>{@code (StageHandle.EMPTY, activeStore().serverHandle())} when {@code player} is null or fake</li>
     *   <li>{@code (ClientStageMirror.SHARED.playerView(), ClientStageMirror.SHARED.serverView())} on the client</li>
     *   <li>{@code (((PlayerExtension) player).neko$getStages(), activeStore().serverHandle())} on the server</li>
     * </ul>
     */
    public static StageViews viewsFor(@Nullable Player player) {
        if (player == null) {
            return new StageViews(StageHandle.EMPTY, StageDomainOwner.activeStore().serverHandle());
        }
        if (player.level() != null && player.level().isClientSide()) {
            return new StageViews(ClientStageMirror.SHARED.playerView(), ClientStageMirror.SHARED.serverView());
        }
        if (isFakePlayer(player)) {
            return new StageViews(StageHandle.EMPTY, StageDomainOwner.activeStore().serverHandle());
        }
        StageReadView playerStages = ((PlayerExtension) player).neko$getStages();
        return new StageViews(
                playerStages != null ? playerStages : StageHandle.EMPTY,
                StageDomainOwner.activeStore().serverHandle()
        );
    }

    /** Returns {@code true} when {@code player} is a loader fake player instance. */
    public static boolean isFakePlayer(@Nullable Player player) {
        if (player == null) {
            return false;
        }
//? if neoforge {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return true;
        }
//?}
        for (Class<?> cls = player.getClass();
                cls != null && cls != Player.class && cls != Object.class;
                cls = cls.getSuperclass()) {
            if (cls.getName().contains("FakePlayer")) {
                return true;
            }
        }
        return false;
    }

    /** Checks whether right-click using {@code stack} is permitted for {@code player}. */
    public static boolean allowUseItem(@Nullable Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NekoId id = itemId(stack);
        if (id == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canUseItem(id, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(player, snapshot.lockedItemRule(id, views.player(), views.server()), "item_use:" + id);
        return false;
    }

    public static boolean allowEquipItem(@Nullable Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        NekoId id = itemId(stack);
        if (id == null) return true;
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canEquipItem(id, views.player(), views.server())
                && snapshot.canStoreInInventory(id, views.player(), views.server())) return true;
        notifyLockedRule(player, snapshot.lockedItemRule(id, views.player(), views.server()), "item_equip:" + id);
        return false;
    }

    /** Checks whether picking up {@code stack} from the world is permitted for {@code player}. */
    public static boolean allowPickUpItem(@Nullable Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NekoId id = itemId(stack);
        if (id == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canPickUpItem(id, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(player, snapshot.lockedItemRule(id, views.player(), views.server()), "item_pickup:" + id);
        return false;
    }

    /** Checks whether dropping {@code stack} is permitted for {@code player}. */
    public static boolean allowDropItem(@Nullable Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NekoId id = itemId(stack);
        if (id == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canDropItem(id, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(player, snapshot.lockedItemRule(id, views.player(), views.server()), "item_drop:" + id);
        return false;
    }

    /** Checks whether attacking with {@code stack} is permitted for {@code player}. */
    public static boolean allowAttackWithItem(@Nullable Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        NekoId id = itemId(stack);
        if (id == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canAttackWithItem(id, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(player, snapshot.lockedItemRule(id, views.player(), views.server()), "item_attack:" + id);
        return false;
    }

    /** Checks whether breaking {@code state} at {@code pos} in {@code level} is permitted for {@code player}. */
    public static boolean allowBreakBlock(
            @Nullable Player player,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);

        NekoId blockId = state != null ? blockId(state) : null;
        Integer cropAge = extractCropAge(state);
        if (blockId != null && !snapshot.canBreakBlock(blockId, cropAge, views.player(), views.server())) {
            notifyLockedRule(
                    player,
                    snapshot.lockedBlockRule(blockId, cropAge, views.player(), views.server()),
                    "block_break:" + blockId
            );
            return false;
        }

        NekoId dimId = dimensionId(level);
        var lockedRegion = dimId == null || pos == null ? null : snapshot.deniedRegionRule(
                dimId, pos.getX(), pos.getY(), pos.getZ(), views.player(), views.server(), RegionGate::allowBreak);
        if (lockedRegion != null) {
            notifyLockedRule(player, lockedRegion, "region_break:" + dimId);
            return false;
        }

        return true;
    }

    /** Checks whether placing {@code state} at {@code pos} in {@code level} is permitted for {@code player}. */
    public static boolean allowPlaceBlock(
            @Nullable Player player,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);

        NekoId blockId = state != null ? blockId(state) : null;
        if (blockId != null && !snapshot.canPlaceBlock(blockId, views.player(), views.server())) {
            notifyLockedRule(
                    player,
                    snapshot.lockedBlockRule(blockId, 0, views.player(), views.server()),
                    "block_place:" + blockId
            );
            return false;
        }

        NekoId dimId = dimensionId(level);
        var lockedRegion = dimId == null || pos == null ? null : snapshot.deniedRegionRule(
                dimId, pos.getX(), pos.getY(), pos.getZ(), views.player(), views.server(), RegionGate::allowPlace);
        if (lockedRegion != null) {
            notifyLockedRule(player, lockedRegion, "region_place:" + dimId);
            return false;
        }

        return true;
    }

    /** Checks whether right-click interacting with {@code state} at {@code pos} is permitted for {@code player}. */
    public static boolean allowInteractBlock(
            @Nullable Player player,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);

        NekoId blockId = state != null ? blockId(state) : null;
        Integer cropAge = extractCropAge(state);
        if (blockId != null && !snapshot.canInteractBlock(blockId, cropAge, views.player(), views.server())) {
            notifyLockedRule(
                    player,
                    snapshot.lockedBlockRule(blockId, cropAge, views.player(), views.server()),
                    "block_interact:" + blockId
            );
            return false;
        }

        NekoId dimId = dimensionId(level);
        var lockedRegion = dimId == null || pos == null ? null : snapshot.deniedRegionRule(
                dimId, pos.getX(), pos.getY(), pos.getZ(), views.player(), views.server(), RegionGate::allowInteract);
        if (lockedRegion != null) {
            notifyLockedRule(player, lockedRegion, "region_interact:" + dimId);
            return false;
        }

        return true;
    }

    /** Checks whether tool block modification (stripping, tilling, flattening) is permitted for {@code player}. */
    public static boolean allowToolModifyBlock(
            @Nullable Player player,
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);

        NekoId blockId = state != null ? blockId(state) : null;
        if (blockId != null && !snapshot.canToolModifyBlock(blockId, views.player(), views.server())) {
            notifyLockedRule(
                    player,
                    snapshot.lockedBlockRule(blockId, null, views.player(), views.server()),
                    "block_tool:" + blockId
            );
            return false;
        }

        NekoId dimId = dimensionId(level);
        var lockedRegion = dimId == null || pos == null ? null : snapshot.deniedRegionRule(
                dimId, pos.getX(), pos.getY(), pos.getZ(), views.player(), views.server(), RegionGate::allowInteract);
        if (lockedRegion != null) {
            notifyLockedRule(player, lockedRegion, "region_tool:" + dimId);
            return false;
        }

        return true;
    }

    /** Checks whether crafting or assembling {@code recipeId} producing {@code output} is permitted for {@code player}. */
    public static boolean allowAssembleRecipe(
            @Nullable Player player,
            @Nullable NekoId recipeId,
            @Nullable NekoId recipeTypeId,
            ItemStack output
    ) {
        if (recipeId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        NekoId outputId = (output != null && !output.isEmpty()) ? itemId(output) : null;
        var locked = snapshot.lockedRecipeRule(recipeId, recipeTypeId, outputId, views.player(), views.server());
        if (locked == null) return true;
        if (locked.declaration() != null) {
            notifyLockedRule(player, locked.declaration(), "recipe:" + recipeId);
        } else {
            StageDefinition definition = StageDomainOwner.activeStore().definition(locked.stage());
            String label = definition == null || definition.displayName().isBlank()
                    ? locked.stage() : definition.displayName();
            notifyLockedMessage(player, "Requires stage: " + label, "recipe:" + recipeId);
        }
        return false;
    }

    /** Checks whether an automated Crafter block may assemble {@code recipeId} using server stage state only. */
    public static boolean allowCrafterRecipe(
            @Nullable NekoId recipeId,
            @Nullable NekoId recipeTypeId,
            ItemStack output
    ) {
        if (recipeId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageReadView serverView = StageDomainOwner.activeStore().serverHandle();
        NekoId outputId = (output != null && !output.isEmpty()) ? itemId(output) : null;
        return snapshot.canAssembleRecipe(recipeId, recipeTypeId, outputId, serverView, serverView);
    }

    /** Checks whether opening {@code menu} is permitted for {@code player}. */
    public static boolean allowOpenContainer(
            @Nullable Player player,
            @Nullable AbstractContainerMenu menu
    ) {
        if (menu == null || menu instanceof InventoryMenu) {
            return true;
        }
        NekoId menuId = resolveMenuId(menu);
        if (menuId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canOpenContainer(menuId, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(
                player,
                snapshot.lockedContainerRule(menuId, views.player(), views.server()),
                "container_open:" + menuId
        );
        return false;
    }

    /** Checks whether storing or transferring {@code stack} in {@code menu} is permitted for {@code player}. */
    public static boolean allowContainerSlotClick(
            @Nullable Player player,
            @Nullable AbstractContainerMenu menu,
            ItemStack stack
    ) {
        return allowContainerSlotClick(player, menu, -1, stack);
    }

    public static boolean allowContainerSlotClick(
            @Nullable Player player, @Nullable AbstractContainerMenu menu, int slot, ItemStack stack
    ) {
        if (stack == null || stack.isEmpty() || menu == null || menu instanceof InventoryMenu) {
            return true;
        }
        NekoId id = itemId(stack);
        if (id == null) {
            return true;
        }
        NekoId menuId = resolveMenuId(menu);
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if ((menuId != null && slot >= 0 && snapshot.isContainerSlotWhitelisted(menuId, slot))
                || snapshot.canStoreInContainer(id, menuId, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(
                player,
                snapshot.lockedItemRule(id, views.player(), views.server()),
                "container_store:" + id
        );
        return false;
    }

    /** Checks whether {@code entity} may travel into {@code targetDimensionId}. */
    public static boolean allowEnterDimension(@Nullable Entity entity, NekoId targetDimensionId) {
        if (!(entity instanceof Player player) || targetDimensionId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canEnterDimension(targetDimensionId, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(
                player,
                snapshot.lockedDimensionRule(targetDimensionId, views.player(), views.server()),
                "dimension:" + targetDimensionId
        );
        return false;
    }

    /** Checks whether {@code player} may attack {@code target} (including main-hand weapon gate checks). */
    public static boolean allowAttackEntity(@Nullable Player player, @Nullable Entity target) {
        if (player != null) {
            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty() && !allowAttackWithItem(player, mainHand)) {
                return false;
            }
        }
        return allowDamageEntity(player, target);
    }

    public static boolean allowDamageEntity(@Nullable Player player, @Nullable Entity target) {
        if (target == null) {
            return true;
        }
        NekoId typeId = entityTypeId(target);
        if (typeId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canAttackEntity(typeId, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(
                player,
                snapshot.lockedEntityRule(typeId, views.player(), views.server()),
                "entity_attack:" + typeId
        );
        return false;
    }

    /** Checks whether {@code player} may right-click interact with {@code target} while holding {@code heldStack}. */
    public static boolean allowInteractEntity(
            @Nullable Player player,
            @Nullable Entity target,
            ItemStack heldStack
    ) {
        if (heldStack != null && !heldStack.isEmpty() && !allowUseItem(player, heldStack)) {
            return false;
        }
        if (target == null) {
            return true;
        }
        NekoId typeId = entityTypeId(target);
        if (typeId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canInteractEntity(typeId, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(
                player,
                snapshot.lockedEntityRule(typeId, views.player(), views.server()),
                "entity_interact:" + typeId
        );
        return false;
    }

    /** Checks whether {@code rider} may mount {@code mount}. */
    public static boolean allowMountEntity(@Nullable Entity rider, @Nullable Entity mount) {
        if (!(rider instanceof Player player) || mount == null) {
            return true;
        }
        NekoId typeId = entityTypeId(mount);
        if (typeId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        if (snapshot.canMountEntity(typeId, views.player(), views.server())) {
            return true;
        }
        notifyLockedRule(
                player,
                snapshot.lockedEntityRule(typeId, views.player(), views.server()),
                "entity_mount:" + typeId
        );
        return false;
    }

    /** Checks whether mob effect {@code effectHolder} may be applied to {@code entity}. */
    public static boolean allowApplyEffect(
            @Nullable LivingEntity entity,
            @Nullable Holder<MobEffect> effectHolder
    ) {
        if (!(entity instanceof Player player) || effectHolder == null) {
            return true;
        }
        NekoId effectId = resolveEffectId(effectHolder);
        if (effectId == null) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);
        var locked = snapshot.lockedEffectRule(effectId, views.player(), views.server());
        if (locked == null) return true;
        notifyLockedRule(player, locked, "effect:" + effectId);
        return false;
    }

    /**
     * Checks whether {@code player} may claim anvil {@code output}, verifying both item gates and
     * direct/stored enchantment gates on the resulting stack.
     */
    public static boolean allowAnvilResult(@Nullable Player player, ItemStack output) {
        if (output == null || output.isEmpty()) {
            return true;
        }
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageViews views = viewsFor(player);

        NekoId outId = itemId(output);
        if (outId != null && !snapshot.canUseItem(outId, views.player(), views.server())) {
            notifyLockedRule(
                    player,
                    snapshot.lockedItemRule(outId, views.player(), views.server()),
                    "anvil_item:" + outId
            );
            return false;
        }

        ItemEnchantments directEnchants = output.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!checkEnchantmentComponent(player, directEnchants, snapshot, views)) {
            return false;
        }

        ItemEnchantments storedEnchants = output.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        return checkEnchantmentComponent(player, storedEnchants, snapshot, views);
    }

    /**
     * Filters entity death {@code drops} in-place against active {@code LootGate} declarations,
     * removing disallowed drops or replacing them with configured fallback items.
     */
    public static void filterEntityDrops(
            @Nullable LivingEntity victim,
            @Nullable Entity attacker,
            Collection<ItemEntity> drops
    ) {
        filterEntityDrops(victim, attacker, null, drops);
    }

    public static void filterEntityDrops(
            @Nullable LivingEntity victim,
            @Nullable Entity attacker,
            @Nullable NekoId lootTableId,
            Collection<ItemEntity> drops
    ) {
        if (drops == null || drops.isEmpty()) return;
        Player killer = attacker instanceof Player player ? player : null;
        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemEntity drop = iterator.next();
            if (drop == null || drop.getItem().isEmpty()) continue;
            ItemStack original = drop.getItem();
            NekoId sourceTable = drop instanceof StageLootDrop loot ? loot.nekojs$takeLootTableId() : null;
            ItemStack filtered = filterLootStack(killer, sourceTable == null ? lootTableId : sourceTable, victim, original);
            if (filtered.isEmpty()) iterator.remove();
            else if (filtered != original) drop.setItem(filtered);
        }
    }

    public static ItemStack filterLootStack(
            @Nullable Player actor,
            @Nullable NekoId lootTableId,
            @Nullable Entity sourceEntity,
            ItemStack stack
    ) {
        if (stack.isEmpty()) return stack;
        NekoId id = itemId(stack);
        if (id == null) return stack;
        StageViews views = viewsFor(actor);
        var locked = StageDomainOwner.activeSnapshot().lockedLootRule(
                id, lootTableId, sourceEntity == null ? null : entityTypeId(sourceEntity),
                views.player(), views.server());
        if (locked == null) return stack;
        notifyLockedRule(actor, locked, "loot:" + locked.id());
        Item replacement = resolveReplacementItem(locked.replacementItem());
        return replacement == null || replacement == Items.AIR
                ? ItemStack.EMPTY : new ItemStack(replacement, stack.getCount());
    }

    /**
     * Appends stage requirement lines (and applies optional locked display name masking) to client
     * item tooltips using {@link ClientStageMirror#SHARED}.
     */
    public static void appendClientItemStageTooltip(ItemStack stack, List<Component> tooltipLines) {
        if (stack == null || stack.isEmpty() || tooltipLines == null) {
            return;
        }
        NekoId id = itemId(stack);
        if (id == null) {
            return;
        }

        ClientStageMirror mirror = ClientStageMirror.SHARED;
        StageReadView playerView = mirror.playerView();
        StageReadView serverView = mirror.serverView();

        boolean hidden = mirror.isItemHidden(id);
        String lockedName = mirror.lockedItemName(id);
        List<String> requiredStages = mirror.requiredStagesForItem(id);

        List<String> missingStages = new ArrayList<>();
        for (String stage : requiredStages) {
            if (!playerView.has(stage) && !serverView.has(stage)) {
                missingStages.add(stage);
            }
        }
        if (missingStages.isEmpty() && (hidden || (lockedName != null && !lockedName.isBlank()))) {
            for (String stage : requiredStages) {
                if (!playerView.has(stage) || !serverView.has(stage)) {
                    missingStages.add(stage);
                }
            }
            if (missingStages.isEmpty() && !requiredStages.isEmpty()) {
                missingStages.addAll(requiredStages);
            }
        }

        boolean isLocked = hidden || (lockedName != null && !lockedName.isBlank()) || !missingStages.isEmpty();
        if (!isLocked) {
            return;
        }

        if (lockedName != null && !lockedName.isBlank() && !tooltipLines.isEmpty()) {
            tooltipLines.set(0, Component.literal(lockedName).withStyle(ChatFormatting.RED));
        }

        if (!missingStages.isEmpty()) {
            tooltipLines.add(
                    Component.literal("Requires stage: ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(String.join(", ", missingStages)).withStyle(ChatFormatting.RED))
            );
        } else {
            tooltipLines.add(Component.literal("Requires unlocked stage").withStyle(ChatFormatting.RED));
        }
    }

    // ---- Shared internal helpers ----

    /** Resolves the portable {@link NekoId} for a non-empty {@link ItemStack}'s item. */
    public static @Nullable NekoId itemId(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? MinecraftStageRegistryAccess.fromMcId(key) : null;
    }

    /** Resolves the portable dimension {@link NekoId} of {@code level}. */
    public static @Nullable NekoId dimensionId(@Nullable Level level) {
        if (level == null || level.dimension() == null) {
            return null;
        }
        return MinecraftStageRegistryAccess.fromResourceKey(level.dimension());
    }

    /** Resolves the portable {@link NekoId} of a {@link Holder} wrapping a {@link MobEffect}. */
    public static @Nullable NekoId resolveEffectId(@Nullable Holder<MobEffect> effectHolder) {
        if (effectHolder == null) {
            return null;
        }
        NekoId unwrapped = effectHolder.unwrapKey()
                .map(MinecraftStageRegistryAccess::fromResourceKey)
                .orElse(null);
        if (unwrapped != null) {
            return unwrapped;
        }
        if (effectHolder.isBound()) {
            Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(effectHolder.value());
            if (key != null) {
                return MinecraftStageRegistryAccess.fromMcId(key);
            }
        }
        return null;
    }

    static void notifyLockedRule(
            @Nullable Player player,
            @Nullable StageGateDeclaration rule,
            String cooldownKey
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String message;
        if (rule != null && !rule.lockedMessage().isBlank()) {
            message = rule.lockedMessage();
        } else if (rule != null) {
            StageDefinition def = StageDomainOwner.activeStore().definition(rule.stage());
            String stageLabel = (def != null && !def.displayName().isBlank())
                    ? def.displayName()
                    : rule.stage();
            message = "Requires stage: " + stageLabel;
        } else {
            message = "Action locked by progression stage";
        }
        notifyLockedMessage(serverPlayer, message, cooldownKey);
    }

    static void notifyLockedMessage(
            @Nullable Player player,
            String rawMessage,
            String cooldownKey
    ) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level() == null) {
            return;
        }
        long nowTick = serverPlayer.level().getGameTime();
        CooldownKey key = new CooldownKey(serverPlayer.getUUID(), cooldownKey);
        Long previousTick = FEEDBACK_COOLDOWNS.get(key);
        if (previousTick != null && Math.abs(nowTick - previousTick) < FEEDBACK_COOLDOWN_TICKS) {
            return;
        }
        FEEDBACK_COOLDOWNS.put(key, nowTick);
        String formatted = rawMessage.replace("{player}", serverPlayer.getScoreboardName());
        MinecraftStagePlatformHooks.sendActionBar(
                serverPlayer,
                Component.literal(formatted).withStyle(ChatFormatting.RED)
        );
    }

    static void clearCooldowns() {
        FEEDBACK_COOLDOWNS.clear();
    }

    static void clearCooldownsFor(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        FEEDBACK_COOLDOWNS.keySet().removeIf(entry -> playerUuid.equals(entry.playerUuid()));
    }

    public static boolean allowUseEnchantment(@Nullable Player actor, Holder<Enchantment> enchantment, int level) {
        StageViews views = viewsFor(actor);
        return allowUseEnchantment(actor, enchantment, level, StageDomainOwner.activeSnapshot(), views);
    }

    private static boolean allowUseEnchantment(@Nullable Player actor, Holder<Enchantment> enchantment,
                                               int level, CompiledStageGateSnapshot snapshot, StageViews views) {
        NekoId id = enchantment.unwrapKey().map(MinecraftStageRegistryAccess::fromResourceKey).orElse(null);
        var locked = snapshot.lockedEnchantmentRule(id, level, views.player(), views.server());
        if (locked == null) return true;
        notifyLockedRule(actor, locked, "enchant:" + id);
        return false;
    }

    private static boolean checkEnchantmentComponent(
            @Nullable Player player,
            ItemEnchantments enchantments,
            CompiledStageGateSnapshot snapshot,
            StageViews views
    ) {
        if (enchantments == null || enchantments.isEmpty()) {
            return true;
        }
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            int level = enchantments.getLevel(holder);
            if (level <= 0) {
                continue;
            }
            if (!allowUseEnchantment(player, holder, level, snapshot, views)) return false;
        }
        return true;
    }

    private static @Nullable NekoId blockId(BlockState state) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null ? MinecraftStageRegistryAccess.fromMcId(key) : null;
    }

    private static @Nullable NekoId entityTypeId(Entity entity) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null ? MinecraftStageRegistryAccess.fromMcId(key) : null;
    }

    private static @Nullable Integer extractCropAge(@Nullable BlockState state) {
        if (state == null) {
            return null;
        }
        Property<?> property = state.getBlock().getStateDefinition().getProperty("age");
        if (property instanceof IntegerProperty ageProperty && state.hasProperty(ageProperty)) {
            return state.getValue(ageProperty);
        }
        return null;
    }

    private static @Nullable NekoId resolveMenuId(AbstractContainerMenu menu) {
        MenuType<?> menuType;
        try {
            menuType = menu.getType();
        } catch (UnsupportedOperationException unregistered) {
            return null;
        }
        if (menuType == null) {
            return null;
        }
        Identifier key = BuiltInRegistries.MENU.getKey(menuType);
        return key != null ? MinecraftStageRegistryAccess.fromMcId(key) : null;
    }

    private static @Nullable Item resolveReplacementItem(@Nullable NekoId replacementId) {
        Identifier mcId = MinecraftStageRegistryAccess.toMcIdOrNull(replacementId);
        if (mcId == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(mcId).orElse(null);
    }

    private record CooldownKey(UUID playerUuid, String actionKey) {
    }
}
