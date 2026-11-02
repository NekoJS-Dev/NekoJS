package com.tkisor.nekojs.command;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;

/** Minecraft symbols used by the shared information commands. */
final class InformationCommandPlatform {
    private InformationCommandPlatform() {}

    static ArgumentType<?> identifierArgument() {
        return IdentifierArgument.id();
    }

    static String keyId(ResourceKey<?> key) {
        return key.identifier().toString();
    }

    static MutableComponent copy(String value, Component description) {
        return Component.literal(value).withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(value))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.translatable("nekojs.command.info.copy", description))));
    }

    static MutableComponent tagLink(String value, String command) {
        return Component.literal(value).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("nekojs.command.info.view_tag"))));
    }
}
