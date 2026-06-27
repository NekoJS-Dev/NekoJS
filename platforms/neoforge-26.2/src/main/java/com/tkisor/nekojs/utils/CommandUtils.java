package com.tkisor.nekojs.utils;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public class CommandUtils {
    public static boolean hasPermissionLevel(ServerPlayer source, int level) {
        return switch (level) {
            case 0 -> Commands.LEVEL_ALL.check(source.permissions());
            case 1 -> Commands.LEVEL_MODERATORS.check(source.permissions());
            case 2 -> Commands.LEVEL_GAMEMASTERS.check(source.permissions());
            case 3 -> Commands.LEVEL_ADMINS.check(source.permissions());
            case 4 -> Commands.LEVEL_OWNERS.check(source.permissions());
            default -> false;
        };
    }
}
