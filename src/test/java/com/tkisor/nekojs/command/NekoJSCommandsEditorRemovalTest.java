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
 * 游戏内编辑器退役契约：/nekojs editor 必须彻底消失（无 alias），
 * 而只读错误入口仍保留在同一个命令树上。
 */
class NekoJSCommandsEditorRemovalTest {

    @Test
    void editorCommandIsRemovedWithoutAliasAndViewErrorsRemain() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        NekoJSCommands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlagSet.of())));

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("nekojs");
        assertNotNull(root, "/nekojs root command must remain registered");
        assertNull(root.getChild("editor"), "/nekojs editor must be removed without a legacy alias");
        assertNotNull(root.getChild("error"), "/nekojs error must remain available");
        assertNotNull(root.getChild("view_all_errors"), "/nekojs view_all_errors must remain available");
    }
}
//?}

