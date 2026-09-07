package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.villager.VillagerTradePlan;
import com.tkisor.nekojs.wrapper.event.server.VillagerTradeEventJS;

/**
 * SERVER declaration group for loader-neutral villager trade contributions.
 * The platform posts {@link #MODIFY} after a full SERVER candidate is committed;
 * scripts only receive the declarative payload.
 */
public interface VillagerTradesEvents {
    EventGroup GROUP = EventGroup.of("VillagerTradesEvents");

    /** Collects validated offer declarations for the current full SERVER revision. */
    EventBusJS<VillagerTradeEventJS, Void> MODIFY =
            GROUP.server("modify", VillagerTradeEventJS.class);

    /** Creates, posts, and seals one complete SERVER contribution product. */
    static VillagerTradePlan collectPlan() {
        VillagerTradeEventJS event = new VillagerTradeEventJS();
        MODIFY.post(event);
        return event.plan();
    }
}
