package com.tkisor.nekojs.authoring;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Constructs the fixed, non-player authoring source using this node's permission API. */
final class AuthoringCommandPlatform {
    private AuthoringCommandPlatform() {}

    static CommandSourceStack createSource(MinecraftServer server, CommandSource feedback) {
        return new CommandSourceStack(feedback, Vec3.ZERO, Vec2.ZERO, server.overworld(),
                2, "NekoJS Authoring", Component.literal("NekoJS Authoring"), server, null);
    }
}
