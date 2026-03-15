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

    public ModifyWorkspaceConfigEvent(JSConfigModel model) {
        this.model = model;
    }

}