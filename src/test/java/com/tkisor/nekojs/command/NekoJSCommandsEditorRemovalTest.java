//? if neoforge {
package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The in-game editor is retired: /nekojs editor must be gone entirely, with no alias, while the
 * read-only error entry stays on the same command tree.
 */
class NekoJSCommandsEditorRemovalTest {

    @Test
    void editorCommandIsRemovedWithoutAliasAndViewErrorsRemain() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.registryMetadataAvailable(),
                "native command permissions require bootstrapped registry metadata");
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        // The root is only captured by the command executor lambda and is not dereferenced while the tree
        // is built. This test asserts the command tree shape only, so it passes null instead of
        // pulling in a full assembly fixture.
        NekoJSCommands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlagSet.of())), null);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("nekojs");
        assertNotNull(root, "/nekojs root command must remain registered");
        assertNull(root.getChild("editor"), "/nekojs editor must be removed without a legacy alias");
        assertNotNull(root.getChild("error"), "/nekojs error must remain available");
        assertNotNull(root.getChild("view_all_errors"), "/nekojs view_all_errors must remain available");
    }
}
//?}

