//? if neoforge {
package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Command-tree shape for the ticket-20 smoke fixes: the trust address argument accepts
 * host:port input (F1) and the bare /nekojs root executes a usage fallback instead of
 * brigadier's "Unknown or incomplete command" (F3). register() touches Commands.LEVEL_GAMEMASTERS,
 * whose class init needs bootstrapped vanilla registries (26.x bare JVMs cannot); runs that
 * cannot initialize Commands skip here, while parse behavior itself stays covered by
 * {@link AddressArgumentTest}, which needs no Minecraft classes. Same null-root construction
 * caveat as {@link NekoJSCommandsEditorRemovalTest}.
 */
class NekoJSCommandsTrustArgumentAndUsageTest {

    /** Commands is the only registry-dependent class register() reaches during tree build. */
    private static boolean commandsClassInitializable() {
        try {
            Class.forName("net.minecraft.commands.Commands", true,
                    NekoJSCommandsTrustArgumentAndUsageTest.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    void trustAddressArgumentAcceptsHostPortAndRootExecutesUsage() {
        Assumptions.assumeTrue(commandsClassInitializable(),
                "net.minecraft.commands.Commands not initializable in this JVM; tree-shape assertions skipped");

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        NekoJSCommands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL,
                CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlagSet.of())), null);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("nekojs");
        assertNotNull(root, "/nekojs root must remain registered");
        assertNotNull(root.getCommand(), "bare /nekojs must execute the usage fallback (F3)");

        CommandNode<CommandSourceStack> trust = root.getChild("trust");
        assertNotNull(trust, "/nekojs trust must remain registered");
        CommandNode<CommandSourceStack> address = trust.getChild("address");
        assertNotNull(address, "/nekojs trust must keep its address argument");
        assertTrue(address instanceof ArgumentCommandNode<?, ?> argumentNode
                        && argumentNode.getType() instanceof AddressArgument,
                "the trust address argument must use AddressArgument so unquoted host:port parses (F1), got: "
                        + address.getClass().getName());
        assertEquals("address", address.getName(), "getAddress(context, \"address\") must keep resolving");

        // The usage fallback lists top-level children derived from the live tree, so the root
        // must actually have the expected subtree names to list.
        assertNotNull(root.getChild("reload"));
        assertNotNull(root.getChild("error"));
        assertNotNull(root.getChild("probe"));
    }
}
//?}
