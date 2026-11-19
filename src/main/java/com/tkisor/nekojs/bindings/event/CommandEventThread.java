package com.tkisor.nekojs.bindings.event;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;

public final class CommandEventThread {
    private CommandEventThread() {}

    public static boolean isCurrent(ParseResults<CommandSourceStack> parseResults) {
        if (parseResults == null || parseResults.getContext() == null) return false;
        var source = parseResults.getContext().getSource();
        if (source == null) return false;
        var server = source.getServer();
        return server != null && server.isSameThread();
    }
}
