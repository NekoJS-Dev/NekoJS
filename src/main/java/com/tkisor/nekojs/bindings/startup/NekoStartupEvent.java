package com.tkisor.nekojs.bindings.startup;

import com.tkisor.nekojs.bindings.event.NekoEvent;

public interface NekoStartupEvent extends NekoEvent {
    NekoStartupEvent BASIC = new NekoStartupEvent() {};
}
