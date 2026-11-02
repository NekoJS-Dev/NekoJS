package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
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
        assertEquals(Identifier.parse("minecraft:item"), context.getArgument("registry", Identifier.class));
        assertEquals(Identifier.parse("minecraft:logs_that_burn"), context.getArgument("tag", Identifier.class));
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
        return ((net.minecraft.network.chat.ClickEvent.CopyToClipboard) line.getStyle().getClickEvent()).value();
    }

    private static String commandValue(net.minecraft.network.chat.Component line) {
        return ((net.minecraft.network.chat.ClickEvent.RunCommand) line.getStyle().getClickEvent()).command();
    }
}
