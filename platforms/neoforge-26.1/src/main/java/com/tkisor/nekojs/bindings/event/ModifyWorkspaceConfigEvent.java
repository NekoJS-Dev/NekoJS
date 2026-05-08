package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.core.fs.JSConfigModel;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.bus.api.Event;

@Getter
public class ModifyWorkspaceConfigEvent extends Event {
    private final JSConfigModel model;
    @Setter
    private String fileName = "jsconfig.json";
    private final String env;

    public ModifyWorkspaceConfigEvent(JSConfigModel model, String env) {
        this.model = model;
        this.env = env;
    }

}