package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

import java.util.function.Function;

/**
 * Built-in portable event group exposing stage customization, declarative stage gates, and
 * server/client stage lifecycle notifications across both {@code nekojs:event/versioned} and
 * {@code nekojs:event/stable}.
 */
public final class StageEvents {
    public static final EventGroup GROUP = EventGroup.of("StageEvents");

    public static final EventBusJS<StageCustomizeEvent, Void> CUSTOMIZE =
            GROUP.server("customize", StageCustomizeEvent.class, api -> {
                api.versioned();
                api.stable().view(StageCustomizeEvent.class, Function.identity()).scriptTypes(ScriptType.SERVER);
            });

    public static final EventBusJS<StableStageGatesEvent, Void> GATES =
            GROUP.server("gates", StableStageGatesEvent.class, api -> {
                api.versioned();
                api.stable().view(StableStageGatesEvent.class, Function.identity()).scriptTypes(ScriptType.SERVER);
            });

    public static final EventBusJS<StableStageChangeEvent, String> ADDED =
            GROUP.server(
                    "added",
                    StableStageChangeEvent.class,
                    DispatchKey.of(String.class, StableStageChangeEvent::getStage),
                    api -> {
                        api.versioned();
                        api.stable()
                                .view(StableStageChangeEvent.class, Function.identity())
                                .selector(String.class, Function.identity())
                                .scriptTypes(ScriptType.SERVER);
                    }
            );

    public static final EventBusJS<StableStageChangeEvent, String> REMOVED =
            GROUP.server(
                    "removed",
                    StableStageChangeEvent.class,
                    DispatchKey.of(String.class, StableStageChangeEvent::getStage),
                    api -> {
                        api.versioned();
                        api.stable()
                                .view(StableStageChangeEvent.class, Function.identity())
                                .selector(String.class, Function.identity())
                                .scriptTypes(ScriptType.SERVER);
                    }
            );

    public static final EventBusJS<StableStageChangeEvent, String> EXPIRED =
            GROUP.server(
                    "expired",
                    StableStageChangeEvent.class,
                    DispatchKey.of(String.class, StableStageChangeEvent::getStage),
                    api -> {
                        api.versioned();
                        api.stable()
                                .view(StableStageChangeEvent.class, Function.identity())
                                .selector(String.class, Function.identity())
                                .scriptTypes(ScriptType.SERVER);
                    }
            );

    public static final EventBusJS<StableStageChangeEvent, String> CLIENT_ADDED =
            GROUP.client(
                    "clientAdded",
                    StableStageChangeEvent.class,
                    DispatchKey.of(String.class, StableStageChangeEvent::getStage),
                    api -> {
                        api.versioned();
                        api.stable()
                                .view(StableStageChangeEvent.class, Function.identity())
                                .selector(String.class, Function.identity())
                                .scriptTypes(ScriptType.CLIENT);
                    }
            );

    public static final EventBusJS<StableStageChangeEvent, String> CLIENT_REMOVED =
            GROUP.client(
                    "clientRemoved",
                    StableStageChangeEvent.class,
                    DispatchKey.of(String.class, StableStageChangeEvent::getStage),
                    api -> {
                        api.versioned();
                        api.stable()
                                .view(StableStageChangeEvent.class, Function.identity())
                                .selector(String.class, Function.identity())
                                .scriptTypes(ScriptType.CLIENT);
                    }
            );

    private StageEvents() {
    }
}
