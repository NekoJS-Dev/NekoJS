package com.tkisor.nekojs.core.error; // 放在 Minecraft 特定的模块或包下

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
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors"))
                );
        return Component.empty().append(main).append("\n").append(link);
    }

    public static Component getSuccessComponent() {
        return Component.translatable("nekojs.error.tracker.success");
    }
}