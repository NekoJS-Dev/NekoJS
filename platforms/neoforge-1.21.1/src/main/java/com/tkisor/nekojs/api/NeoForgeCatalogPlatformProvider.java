package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.catalog.HostExtensionSource;
import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.RecipeNamespaceCatalogEntry;
import com.tkisor.nekojs.api.inject.*;
import com.tkisor.nekojs.api.recipe.NekoRecipeNamespaces;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.List;

public class NeoForgeCatalogPlatformProvider implements NekoCatalogPlatformProvider {
    @Override
    public Collection<RecipeNamespaceCatalogEntry> recipeNamespaces() {
        return NekoRecipeNamespaces.getHandlerClasses().entrySet().stream()
                .map(entry -> RecipeNamespaceCatalogEntry.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public Collection<HostExtensionSource> hostExtensions() {
        return List.of(
                HostExtensionSource.common(ItemStack.class, ItemStackExtension.class),
                HostExtensionSource.common(Item.class, ItemExtension.class),
                HostExtensionSource.common(Block.class, BlockExtension.class),
                HostExtensionSource.common(BlockState.class, BlockStateExtension.class),
                HostExtensionSource.common(Entity.class, EntityExtension.class),
                HostExtensionSource.common(LivingEntity.class, LivingEntityExtension.class),
                HostExtensionSource.common(Player.class, PlayerExtension.class),
                HostExtensionSource.common(Level.class, LevelExtension.class)
        );
    }
}
