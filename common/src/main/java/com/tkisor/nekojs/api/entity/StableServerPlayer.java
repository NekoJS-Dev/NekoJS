package com.tkisor.nekojs.api.entity;

import com.tkisor.nekojs.api.annotation.Doc;

/** Live logical-server player. */
@Doc("Live logical-server player.")
public interface StableServerPlayer extends StablePlayer {
    @Doc("Returns the connection latency in milliseconds, or 0 without a connection.")
    int getLatency();
}
