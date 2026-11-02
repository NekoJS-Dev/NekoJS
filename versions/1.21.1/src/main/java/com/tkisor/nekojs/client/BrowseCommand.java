package com.tkisor.nekojs.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.io.IOException;
import java.nio.file.Files;

/** Opens the local script directory; this operation is registered only on client dispatchers. */
public final class BrowseCommand {
    private BrowseCommand() {}

    public static int open() throws CommandSyntaxException {
        var directory = NekoJSPaths.get().root();
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new SimpleCommandExceptionType(Component.translatable(
                    "nekojs.command.info.browse_failed", directory.toString())).create();
        }
        Util.getPlatform().openPath(directory);
        return 1;
    }
}
