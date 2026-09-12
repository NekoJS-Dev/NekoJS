package com.tkisor.nekojs.client.gui.dashboard;

/** Retains the requested offset across reflow; a temporary wider view must not erase it. */
public final class DashboardScroll {
    private double requested;
    public int offset(int contentHeight, int viewportHeight) {
        return (int) Math.min(requested, Math.max(0, contentHeight - viewportHeight));
    }
    public void move(double delta, int contentHeight, int viewportHeight) {
        requested = Math.max(0, Math.min(Math.max(0, contentHeight - viewportHeight),
                offset(contentHeight, viewportHeight) + delta));
    }
    public void to(double offset) { requested = Math.max(0, offset); }
}
