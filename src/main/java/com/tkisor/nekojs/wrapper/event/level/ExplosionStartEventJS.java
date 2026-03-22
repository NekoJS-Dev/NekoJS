package com.tkisor.nekojs.wrapper.event.level;

import com.tkisor.nekojs.api.event.NekoCancellableEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public class ExplosionStartEventJS implements NekoCancellableEvent {
    private final ExplosionEvent.Start rawEvent;

    public ExplosionStartEventJS(ExplosionEvent.Start rawEvent) {
        this.rawEvent = rawEvent;
    }

    public double getX() {
        return rawEvent.getExplosion().center().x;
    }

    public double getY() {
        return rawEvent.getExplosion().center().y;
    }

    public double getZ() {
        return rawEvent.getExplosion().center().z;
    }

}