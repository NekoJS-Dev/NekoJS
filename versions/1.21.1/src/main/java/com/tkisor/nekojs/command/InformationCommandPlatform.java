package com.tkisor.nekojs.command;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;

/** Minecraft symbols used by the shared information commands. */
final class InformationCommandPlatform {
    private InformationCommandPlatform() {}

    static ArgumentType<?> identifierArgument() {
        return ResourceLocationArgument.id();
    }

    static String keyId(ResourceKey<?> key) {
        return key.location().toString();
    }

    static MutableComponent copy(String value, Component description) {
        return Component.literal(value).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("nekojs.command.info.copy", description))));
    }

    static MutableComponent tagLink(String value, String command) {
        return Component.literal(value).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("nekojs.command.info.view_tag"))));
    }
}
