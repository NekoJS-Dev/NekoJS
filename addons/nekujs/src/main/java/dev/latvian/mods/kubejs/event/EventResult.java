package dev.latvian.mods.kubejs.event;

import dev.latvian.mods.rhino.Context;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.util.TriState;

import java.util.function.Consumer;

/** Native boolean cancellation translated to the legacy result descriptor. */
public class EventResult {
    public enum Type {
        ERROR, PASS, INTERRUPT_DEFAULT, INTERRUPT_FALSE, INTERRUPT_TRUE;

        public EventExit exit(Context context, Object value) {
            throw unsupportedExit();
        }
    }

    public static final EventResult PASS = new EventResult(Type.PASS);
    private static final EventResult CANCELLED = new EventResult(Type.INTERRUPT_FALSE);
    private final Type type;

    private EventResult(Type type) {
        this.type = type;
    }

    static EventResult fromCancellation(boolean cancelled) {
        return cancelled ? CANCELLED : PASS;
    }

    static UnsupportedOperationException unsupportedExit() {
        return new UnsupportedOperationException("[NEKO-4218] Immediate legacy event cancellation and exit values are unsupported; native cancellable listeners return true");
    }

    public Context cx() { return null; }
    public Type type() { return type; }
    public Object value() { return null; }
    public boolean override() { return type != Type.PASS; }
    public boolean pass() { return type == Type.PASS; }
    public boolean interruptDefault() { return type == Type.INTERRUPT_DEFAULT; }
    public boolean interruptFalse() { return type == Type.INTERRUPT_FALSE; }
    public boolean interruptTrue() { return type == Type.INTERRUPT_TRUE; }

    public boolean applyCancel(ICancellableEvent event) {
        if (!interruptFalse()) return false;
        event.setCanceled(true);
        return true;
    }

    public void applyTristate(Consumer<TriState> consumer) {
        if (interruptFalse()) consumer.accept(TriState.FALSE);
        else if (interruptTrue()) consumer.accept(TriState.TRUE);
    }
}
