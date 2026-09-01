package com.tkisor.nekojs.core.error;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class NekoErrorUIHelper {

    /**
     * @param errorCount 当前错误总数——由调用方传入（两加载器的 runtime root 各自持有
     *                   error tracker，本类不绑任一入口类）
     */
    public static Component getErrorComponent(int errorCount) {
        MutableComponent main = Component.translatable("nekojs.error.tracker.warning", errorCount);
        MutableComponent link = Component.translatable("nekojs.error.tracker.open_list")
                .withStyle(style -> style
//? if >=26 {
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors"))
//?} else {
/*                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nekojs view_all_errors"))
*///?}
                );
        return Component.empty().append(main).append("\n").append(link);
    }

    public static Component getSuccessComponent() {
        return Component.translatable("nekojs.error.tracker.success");
    }
}

