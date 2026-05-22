package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

public final class ScriptEvents {
    public static final EventGroup GROUP = EventGroup.of("ScriptEvents");
    public static final EventBusJS<ScriptEventRegistrationEvent, Void> SERVER = GROUP.startup("server", ScriptEventRegistrationEvent.class);
    public static final EventBusJS<ScriptEventRegistrationEvent, Void> CLIENT = GROUP.startup("client", ScriptEventRegistrationEvent.class);
    public static final EventBusJS<ScriptEventRegistrationEvent, Void> COMMON = GROUP.startup("common", ScriptEventRegistrationEvent.class);

    private ScriptEvents() {}

    public static void post(ScriptEventRegistrar registrar) {
        SERVER.post(new ScriptEventRegistrationEvent(ScriptType.SERVER, registrar));
        CLIENT.post(new ScriptEventRegistrationEvent(ScriptType.CLIENT, registrar));
        COMMON.post(new ScriptEventRegistrationEvent(ScriptType.COMMON, registrar));
    }
}
