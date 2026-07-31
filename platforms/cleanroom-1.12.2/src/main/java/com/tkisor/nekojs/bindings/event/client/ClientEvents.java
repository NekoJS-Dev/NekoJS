package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public interface ClientEvents {
    EventGroup GROUP = EventGroup.of("ClientEvents");

    EventBusJS<TickEvent.ClientTickEvent, Void> TICK =
            GROUP.client("tick", TickEvent.ClientTickEvent.class);
    EventBusJS<ClientChatReceivedEvent, Void> CHAT_RECEIVED =
            GROUP.client("chatReceived", ClientChatReceivedEvent.class);

    DispatchKey<DataGeneratorJS, String> ASSET_STAGE_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(DataGeneratorJS event) {
            return event.getStage();
        }
    };

    DispatchKey<LangGeneratorJS, String> LANG_KEY = new DispatchKey<>() {
        @Override
        public Class<String> keyType() {
            return String.class;
        }

        @Override
        public String eventToKey(LangGeneratorJS event) {
            return event.getLang();
        }
    };

    /**
     * 资产生成事件：脚本把 asset JSON 写入 {@code <gameDir>/nekojs/assets}（由
     * {@code MinecraftMixin} 注册为 FolderResourcePack，资源 reload 时生效）。
     */
    EventBusJS<DataGeneratorJS, String> GENERATE_ASSETS =
            GROUP.client("generateAssets", DataGeneratorJS.class, ASSET_STAGE_KEY);

    /**
     * 语言生成事件：脚本按语言代码收集翻译条目（{@code en_us} 等）。
     * 1.12.2 语言为 {@code .lang} 文本格式，聚合后由 mixin 注入当前语言的 Locale。
     */
    EventBusJS<LangGeneratorJS, String> LANG =
            GROUP.client("lang", LangGeneratorJS.class, LANG_KEY);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(TICK)
            .bind(CHAT_RECEIVED);
}
