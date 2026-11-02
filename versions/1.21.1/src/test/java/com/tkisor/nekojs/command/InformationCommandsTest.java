package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InformationCommandsTest {
    @Test
    void commandTreeKeepsRootPermissionAndParsesNamespacedTagPaths() {
        var permission = (java.util.function.Predicate<CommandSourceStack>) source -> false;
        var root = InformationCommands.addTo(LiteralArgumentBuilder.<CommandSourceStack>literal("nekojs")
                .requires(permission), stack -> null).build();
        assertSame(permission, root.getRequirement());
        for (String name : java.util.List.of("hand", "inventory", "hotbar")) {
            assertNotNull(root.getChild(name).getCommand());
        }
        var registry = root.getChild("list-tag").getChild("registry");
        assertNotNull(registry.getCommand());
        assertNotNull(registry.getChild("tag").getCommand());
        assertNull(root.getChild("browse"), "Opening local directories must not be a server command");
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(InformationCommands.addTo(LiteralArgumentBuilder.literal("nekojs"), stack -> null));
        var parsed = dispatcher.parse("nekojs list-tag minecraft:item minecraft:logs_that_burn", null);
        assertFalse(parsed.getReader().canRead());
        var context = parsed.getContext().build(parsed.getReader().getString());
        assertEquals(ResourceLocation.parse("minecraft:item"), context.getArgument("registry", ResourceLocation.class));
        assertEquals(ResourceLocation.parse("minecraft:logs_that_burn"), context.getArgument("tag", ResourceLocation.class));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("nekojs list-tag", null));
        assertTrue(dispatcher.parse("nekojs list-tag Invalid:Registry", null).getReader().canRead());
    }

    @Test
    void clipboardPayloadAndTagCommandUseExactUnmodifiedValues() {
        String value = "{\"components\":{\"minecraft:custom_name\":\"a'b\\c\"}}";
        var line = InformationCommandPlatform.copy(value, net.minecraft.network.chat.Component.literal("data"));
        assertEquals(value, line.getString());
        assertEquals(value, clipboardValue(line));
        String command = "/nekojs list-tag minecraft:item minecraft:logs";
        var link = InformationCommandPlatform.tagLink("#minecraft:logs", command);
        assertEquals(command, commandValue(link));
        assertNotNull(link.getStyle().getHoverEvent());
    }

    private static String clipboardValue(net.minecraft.network.chat.Component line) {
        assertEquals(net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD, line.getStyle().getClickEvent().getAction());
        return line.getStyle().getClickEvent().getValue();
    }

    private static String commandValue(net.minecraft.network.chat.Component line) {
        assertEquals(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, line.getStyle().getClickEvent().getAction());
        return line.getStyle().getClickEvent().getValue();
    }
}
