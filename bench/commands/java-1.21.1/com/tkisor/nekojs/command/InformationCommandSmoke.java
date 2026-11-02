package com.tkisor.nekojs.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Loader-backed assertions; added only to the opt-in informationSmoke run. */
@EventBusSubscriber(modid = "nekojs")
public final class InformationCommandSmoke {
    private static final List<String> checks = new ArrayList<>();

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        String failure = null;
        try {
            var dispatcher = server.getCommands().getDispatcher();
            var messages = new ArrayList<Component>();
            CommandSource capture = new CommandSource() {
                public void sendSystemMessage(Component message) { messages.add(message); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean shouldInformAdmins() { return false; }
            };
            var console = server.createCommandSourceStack().withSource(capture);
            var root = dispatcher.getRoot().getChild("nekojs");
            require(root.getChild("browse") == null, "browse is not registered on the server");
            for (String name : List.of("hand", "inventory", "hotbar")) {
                try {
                    dispatcher.execute("nekojs " + name, console);
                    throw new AssertionError(name + " must require a player");
                } catch (CommandSyntaxException expected) {
                    checks.add(name + " rejects console");
                }
            }
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("nekojs list-tag minecraft:i", console)).join();
            require(suggestions.getList().stream().anyMatch(s -> s.getText().equals("minecraft:item")), "registry completion");
            dispatcher.execute("nekojs list-tag minecraft:item", console);
            var link = messages.stream().map(c -> c.getStyle().getClickEvent())
                    .filter(c -> c != null && c.getAction() == ClickEvent.Action.RUN_COMMAND).findFirst().orElseThrow();
            require(link.getValue().startsWith("/nekojs list-tag minecraft:item "), "tag link spelling");
            dispatcher.execute(link.getValue().substring(1), console);
            var tags = dispatcher.getCompletionSuggestions(dispatcher.parse("nekojs list-tag minecraft:item minecraft:l", console)).join();
            require(tags.getList().stream().anyMatch(s -> s.getText().equals("minecraft:logs")), "tag completion");
            for (String command : List.of("nekojs list-tag nekojs:missing", "nekojs list-tag minecraft:item nekojs:missing")) {
                try {
                    dispatcher.execute(command, console);
                    throw new AssertionError("Missing registry/tag must fail: " + command);
                } catch (CommandSyntaxException expected) {
                    checks.add(command + " fails");
                }
            }
            var player = new ServerPlayer(server, server.overworld(),
                    new GameProfile(UUID.fromString("6ad57e38-ddcf-4fd3-92a4-69263541d7c0"), "NekoCommandSmoke"), ClientInformation.createDefault()) {
                @Override
                public void sendSystemMessage(Component message) {
                    messages.add(message);
                }
            };
            var source = console.withEntity(player);
            var inventory = player.getInventory();
            ItemStack original = new ItemStack(Items.DIAMOND_SWORD, 3);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("a'b\\c 物品"));
            original.set(DataComponents.DAMAGE, 17);
            original.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            original.enchant(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 2);
            var encoded = InformationCommands.stackData(server.registryAccess(), original);
            var restored = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), encoded).getOrThrow();
            require(ItemStack.isSameItemSameComponents(original, restored) && restored.getCount() == 3, "codec round trip with count, custom name, damage, and removal");
            require(encoded.toString().contains("!minecraft:attribute_modifiers"), "component removal retained");
            String itemText = InformationCommands.itemString(server.registryAccess(), original);
            var factory = new com.tkisor.nekojs.bindings.static_access.ItemJS();
            ItemStack factoryStack = factory.of(itemText);
            require(ItemStack.isSameItemSameComponents(original, factoryStack) && factoryStack.getCount() == 3,
                    "Item.of component string round trip including name, damage, removal and count");
            var damaged = factory.of("minecraft:iron_sword[damage=2]");
            require(damaged.getDamageValue() == 2, "Item.of vanilla short component syntax");
            require(InformationCommands.itemString(server.registryAccess(), damaged).equals("minecraft:iron_sword[damage=2]"),
                    "hand format matches short component syntax");
            require(factory.of("2xminecraft:iron_sword[damage=2]").getCount() == 2, "component string count without space");
            require(factory.of("minecraft:iron_sword[damage=2]", 4).getCount() == 4, "Item.of explicit count keeps component string");
            try (var context = graal.graalvm.polyglot.Context.newBuilder("js").allowAllAccess(true).build()) {
                context.getBindings("js").putMember("Item", factory);
                context.getBindings("js").putMember("itemText", itemText);
                ItemStack scriptStack = context.eval("js", "Item.of(itemText)").asHostObject();
                require(ItemStack.isSameItemSameComponents(original, scriptStack), "JavaScript Item.of copied string");
                var adapted = new com.tkisor.nekojs.js.type_adapter.ItemStackAdapter().apply(context.asValue(itemText));
                require(ItemStack.isSameItemSameComponents(original, adapted), "ItemStack adapter component string");
                var object = context.eval("js", "({id: itemText, count: 5})");
                require(new com.tkisor.nekojs.js.type_adapter.ItemStackAdapter().apply(object).getCount() == 5,
                        "object adapter ID component string and count override");
            }
            for (String invalid : List.of("minecraft:stone[damage=\"bad\"]", "minecraft:stone[nekojs:missing=1]",
                    "minecraft:stone[damage=1,damage=2]", "minecraft:stone[damage=2]junk", "0x minecraft:stone[damage=1]",
                    "minecraft:stone[damage=2", "#minecraft:logs[damage=1]")) {
                try {
                    factory.of(invalid);
                    throw new AssertionError("Invalid component string accepted: " + invalid);
                } catch (com.tkisor.nekojs.api.data.ValueConversionException expected) {
                    checks.add("invalid component string rejected: " + invalid);
                }
            }
            inventory.setItem(0, original);
            inventory.setItem(7, new ItemStack(Items.STONE, 2));
            inventory.setItem(8, new ItemStack(Items.STONE, 2));
            inventory.setItem(9, new ItemStack(Items.APPLE));
            inventory.setItem(35, new ItemStack(Items.DIRT));
            inventory.setItem(36, new ItemStack(Items.DIAMOND_HELMET));
            inventory.setItem(40, new ItemStack(Items.SHIELD));
            messages.clear();
            dispatcher.execute("nekojs hand", source);
            require(copies(messages).contains(encoded.toString()), "hand includes complete copyable JSON");
            require(copies(messages).contains(new com.google.gson.JsonPrimitive(itemText).toString()),
                    "hand includes round-trippable component string");
            require(original.getCount() == 3 && original.getDamageValue() == 17, "hand does not mutate held stack");
            for (String command : List.of("hotbar", "inventory")) {
                messages.clear();
                dispatcher.execute("nekojs " + command, source);
                var exports = copies(messages);
                var basic = JsonParser.parseString(exports.get(0)).getAsJsonArray();
                var full = JsonParser.parseString(exports.get(1)).getAsJsonArray();
                int expected = command.equals("hotbar") ? 3 : 5;
                require(basic.size() == expected && full.size() == expected, command + " bounds and empty-slot filtering");
                require(full.get(0).equals(encoded), command + " retains components");
                require(ItemStack.isSameItemSameComponents(original, factory.of(basic.get(0).getAsString())),
                        command + " component string list round trip");
                require(basic.get(1).getAsString().equals("2x minecraft:stone"), command + " retains order");
                require(basic.get(1).equals(basic.get(2)), command + " retains duplicate stacks");
            }
            inventory.setItem(0, new ItemStack(Items.OAK_LOG));
            messages.clear();
            dispatcher.execute("nekojs hand", source);
            require(copies(messages).contains("'#minecraft:logs'"), "hand item and block tags");
            require(copies(messages).contains("\"minecraft:oak_log\""), "hand block ID");
            inventory.setItem(0, new ItemStack(Items.WATER_BUCKET));
            messages.clear();
            dispatcher.execute("nekojs hand", source);
            require(copies(messages).contains("\"minecraft:water\""), "hand fluid container");
            inventory.clearContent();
            messages.clear();
            dispatcher.execute("nekojs hand", source);
            require(copies(messages).isEmpty(), "empty hand message has no fake item export");
            messages.clear();
            dispatcher.execute("nekojs inventory", source);
            require(copies(messages).equals(List.of("[]", "[]")), "empty inventory arrays");
        } catch (Throwable error) {
            failure = error.toString();
            error.printStackTrace();
        } finally {
            try {
                var report = new java.util.LinkedHashMap<String, Object>();
                report.put("checks", checks);
                report.put("failure", failure);
                Files.writeString(Path.of("information-command-smoke.json"), new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
                System.out.println(failure == null ? "INFORMATION_COMMAND_SMOKE_PASS " + checks.size() : "INFORMATION_COMMAND_SMOKE_FAIL " + failure);
            } catch (Exception error) {
                error.printStackTrace();
            }
            server.halt(false);
        }
    }

    private static List<String> copies(List<Component> messages) {
        return messages.stream().map(c -> c.getStyle().getClickEvent())
                .filter(e -> e != null && e.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD)
                .map(ClickEvent::getValue).toList();
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }
}
