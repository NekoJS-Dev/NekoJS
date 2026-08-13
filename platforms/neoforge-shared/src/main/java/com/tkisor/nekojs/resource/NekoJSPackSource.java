package com.tkisor.nekojs.resource;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackSource;

import java.util.function.UnaryOperator;

public class NekoJSPackSource {
    public static PackSource PACK_SOURCE_NEKO = PackSource.create(decorateWithNekoSource(), true);

    static UnaryOperator<Component> decorateWithNekoSource() {
        Component nekoText = Component.literal(NekoJS.MODID);
        return (component) -> Component
                .translatable("pack.nameAndSource", component, nekoText).withStyle(ChatFormatting.AQUA);
    }
}
