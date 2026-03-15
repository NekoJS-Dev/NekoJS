package com.tkisor.nekojs.wrapper.command;

import com.mojang.brigadier.context.CommandContext;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandContextJS {
    private final CommandContext<CommandSourceStack> rawContext;

    public CommandContextJS(CommandContext<CommandSourceStack> rawContext) {
        this.rawContext = rawContext;
    }

    public PlayerWrapper getPlayer() {
        CommandSourceStack source = rawContext.getSource();
        if (source.getEntity() instanceof ServerPlayer serverPlayer) {
            return new PlayerWrapper(serverPlayer);
        }
        return null;
    }

    public void reply(String message) {
        rawContext.getSource().sendSystemMessage(Component.literal(message));
    }

    public void reply(Component message) {
        rawContext.getSource().sendSystemMessage(message);
    }

    public CommandContext<CommandSourceStack> getRaw() {
        return rawContext;
    }
}