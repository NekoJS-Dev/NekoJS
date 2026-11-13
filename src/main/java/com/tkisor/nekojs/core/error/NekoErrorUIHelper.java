package com.tkisor.nekojs.core.error;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class NekoErrorUIHelper {

    /**
     * @param errorCount the current error total, passed in by the caller (the runtime root of
     *                   each loader holds its own error tracker, so this class binds to no
     *                   entry point)
     */
    public static Component getErrorComponent(int errorCount) {
        MutableComponent main = Component.translatable("nekojs.error.tracker.warning", errorCount);
        MutableComponent link = Component.translatable("nekojs.error.tracker.open_list")
                .withStyle(style -> style
//? if >=26 {
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors"))
//?} else {
                        /*.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nekojs view_all_errors"))
*///?}
                );
        return Component.empty().append(main).append("\n").append(link);
    }

    public static Component getSuccessComponent() {
        return Component.translatable("nekojs.error.tracker.success");
    }
}

