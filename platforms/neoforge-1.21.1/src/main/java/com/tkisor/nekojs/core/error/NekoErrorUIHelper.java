package com.tkisor.nekojs.core.error; // 或者是你分离后的包名

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class NekoErrorUIHelper {

    public static Component getErrorComponent() {
        int errorCount = NekoErrorTracker.getErrorCount();

        MutableComponent main = Component.translatable("nekojs.error.tracker.warning", errorCount);
        MutableComponent link = Component.translatable("nekojs.error.tracker.open_list")
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nekojs view_all_errors"))
                );

        return Component.empty().append(main).append("\n").append(link);
    }

    public static Component getSuccessComponent() {
        return Component.translatable("nekojs.error.tracker.success");
    }
}