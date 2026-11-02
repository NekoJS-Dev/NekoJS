package com.tkisor.nekojs.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/** Read-only item exports and live registry tag queries shared by all supported loaders. */
public final class InformationCommands {
    private InformationCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> addTo(
            LiteralArgumentBuilder<CommandSourceStack> root, Function<ItemStack, Fluid> containedFluid) {
        return root.then(literal("hand").executes(context -> hand(context.getSource(), containedFluid)))
                .then(literal("hotbar").executes(context -> inventory(context.getSource(), Inventory.getSelectionSize())))
                .then(literal("inventory").executes(context -> inventory(context.getSource(), Inventory.INVENTORY_SIZE)))
                .then(literal("list-tag")
                        .then(identifier("registry")
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().registryAccess().registries()
                                                .map(entry -> InformationCommandPlatform.keyId(entry.key())), builder))
                                .executes(context -> listTags(context.getSource(), registry(context)))
                                .then(identifier("tag")
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                registry(context).listTagIds().map(tag -> tag.location().toString()), builder))
                                        .executes(context -> listTag(context.getSource(), registry(context),
                                                context.getArgument("tag", Identifier.class))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> identifier(String name) {
        return RequiredArgumentBuilder.argument(name, InformationCommandPlatform.identifierArgument());
    }

    private static int hand(CommandSourceStack source, Function<ItemStack, Fluid> containedFluid) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendSystemMessage(Component.translatable("nekojs.command.info.empty_hand"));
            return 1;
        }
        JsonElement data = stackData(source.registryAccess(), stack);
        source.sendSystemMessage(Component.translatable("nekojs.command.info.hand"));
        sendCopy(source, new JsonPrimitive(itemString(source.registryAccess(), stack)).toString(), "item", ChatFormatting.GREEN);
        sendCopy(source, data.toString(), "stack_data", ChatFormatting.WHITE);
        describeTags(source, source.registryAccess().lookupOrThrow(Registries.ITEM), stack.getItem());
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        long namespaceSize = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(namespace)).count();
        source.sendSystemMessage(InformationCommandPlatform.copy("'@" + namespace + "'",
                Component.translatable("nekojs.command.info.mod_size", namespaceSize)).withStyle(ChatFormatting.AQUA));
        for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.contains(stack)) {
                source.sendSystemMessage(InformationCommandPlatform.copy(
                        new JsonPrimitive(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString()).toString(),
                        Component.translatable("nekojs.command.info.tab_size", tab.getDisplayName(),
                                tab.getDisplayItems().size(), tab.getSearchTabDisplayItems().size()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        if (stack.getItem() instanceof BlockItem item) {
            source.sendSystemMessage(Component.translatable("nekojs.command.info.block"));
            sendCopy(source, new JsonPrimitive(BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString()).toString(), "id", ChatFormatting.GREEN);
            describeTags(source, source.registryAccess().lookupOrThrow(Registries.BLOCK), item.getBlock());
        }
        // Inspect a copy: capability providers must never receive the player's live stack.
        Fluid fluid = containedFluid.apply(stack.copy());
        if (fluid != null) {
            source.sendSystemMessage(Component.translatable("nekojs.command.info.fluid"));
            sendCopy(source, new JsonPrimitive(BuiltInRegistries.FLUID.getKey(fluid).toString()).toString(), "id", ChatFormatting.GREEN);
            describeTags(source, source.registryAccess().lookupOrThrow(Registries.FLUID), fluid);
        }
        return 1;
    }

    private static <T> void describeTags(CommandSourceStack source, HolderLookup.RegistryLookup<T> registry, T value) {
        registry.listTags().filter(tag -> tag.stream().anyMatch(holder -> holder.value() == value))
                .sorted(Comparator.comparing(tag -> tag.key().location().toString()))
                .forEach(tag -> source.sendSystemMessage(InformationCommandPlatform.copy(
                        "'#" + tag.key().location() + "'",
                        Component.translatable("nekojs.command.info.tag_size", tag.size()))
                        .withStyle(ChatFormatting.YELLOW)));
    }

    private static int inventory(CommandSourceStack source, int size) throws CommandSyntaxException {
        Inventory inventory = source.getPlayerOrException().getInventory();
        JsonArray data = new JsonArray();
        JsonArray items = new JsonArray();
        for (ItemStack stack : selectedItems(inventory::getItem, size)) {
            data.add(stackData(source.registryAccess(), stack));
            items.add(itemString(source.registryAccess(), stack));
        }
        source.sendSystemMessage(Component.translatable(size == Inventory.getSelectionSize()
                ? "nekojs.command.info.hotbar" : "nekojs.command.info.inventory"));
        sendCopy(source, items.toString(), "item_list", ChatFormatting.GREEN);
        sendCopy(source, data.toString(), "stack_list", ChatFormatting.WHITE);
        return 1;
    }

    static List<ItemStack> selectedItems(IntFunction<ItemStack> slots, int size) {
        return IntStream.range(0, size).mapToObj(slots).filter(stack -> !stack.isEmpty()).toList();
    }

    static String itemString(HolderLookup.Provider registries, ItemStack stack) throws CommandSyntaxException {
        try {
            return com.tkisor.nekojs.js.type_adapter.ItemStackStrings.format(stack, registries);
        } catch (RuntimeException exception) {
            throw failure("nekojs.command.info.encode_failed", exception.getMessage());
        }
    }

    static JsonElement stackData(HolderLookup.Provider registries, ItemStack stack) throws CommandSyntaxException {
        var encoded = ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack);
        if (encoded.error().isPresent()) {
            throw failure("nekojs.command.info.encode_failed", encoded.error().orElseThrow().message());
        }
        return encoded.result().orElseThrow();
    }

    private static HolderLookup.RegistryLookup<Object> registry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = context.getArgument("registry", Identifier.class);
        ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(id);
        return context.getSource().registryAccess().lookup(key)
                .orElseThrow(() -> failure("nekojs.command.info.unknown_registry", id.toString()));
    }

    private static int listTags(CommandSourceStack source, HolderLookup.RegistryLookup<?> registry) {
        List<? extends HolderSet.Named<?>> tags = registry.listTags()
                .sorted(Comparator.comparing(tag -> tag.key().location().toString())).toList();
        String registryId = InformationCommandPlatform.keyId(registry.key());
        for (var tag : tags) {
            String id = tag.key().location().toString();
            source.sendSystemMessage(InformationCommandPlatform.tagLink("#" + id,
                    "/nekojs list-tag " + registryId + " " + id).withStyle(ChatFormatting.GREEN));
        }
        source.sendSystemMessage(Component.translatable("nekojs.command.info.tag_count", tags.size()));
        return tags.size();
    }

    private static <T> int listTag(CommandSourceStack source, HolderLookup.RegistryLookup<T> registry, Identifier id)
            throws CommandSyntaxException {
        var tag = registry.listTags().filter(candidate -> candidate.key().location().equals(id)).findFirst()
                .orElseThrow(() -> failure("nekojs.command.info.unknown_tag", id.toString()));
        List<String> entries = tag.stream().map(holder -> holder.unwrapKey()
                .map(InformationCommandPlatform::keyId).orElseGet(holder::toString)).sorted().toList();
        for (String entry : entries) {
            sendCopy(source, new JsonPrimitive(entry).toString(), "id", ChatFormatting.GREEN);
        }
        source.sendSystemMessage(Component.translatable("nekojs.command.info.entry_count", entries.size()));
        return entries.size();
    }

    private static void sendCopy(CommandSourceStack source, String value, String description, ChatFormatting color) {
        source.sendSystemMessage(InformationCommandPlatform.copy(value,
                Component.translatable("nekojs.command.info." + description)).withStyle(color));
    }

    private static CommandSyntaxException failure(String key, String detail) {
        return new SimpleCommandExceptionType(Component.translatable(key, detail)).create();
    }
}
