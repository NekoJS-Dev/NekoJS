package com.tkisor.nekojs.wrapper.event.client;

/**
 * 客户端 tick 事件的中立 payload（tickPre / tickPost 共用；契约无 payload 成员）。
 */
public class ClientTickEventJS {

    public static final ClientTickEventJS INSTANCE = new ClientTickEventJS();

    private ClientTickEventJS() {
    }
}
