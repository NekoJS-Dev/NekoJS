package dev.latvian.mods.kubejs.event;

/** Legacy exception descriptor only; native dispatch does not interpret Rhino exits. */
public class EventExit extends Exception {
    public final EventResult result;

    public EventExit(EventResult result) {
        super("result", null, false, false);
        this.result = result;
    }
}
