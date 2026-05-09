package com.tkisor.nekojs.bindings.static_access;

public final class TimeJS {
    public final int SECOND = 20;
    public final int MINUTE = SECOND * 60;
    public final int HOUR = MINUTE * 60;

    public int seconds(int value) {
        return value * SECOND;
    }

    public int minutes(int value) {
        return value * MINUTE;
    }

    public int hours(int value) {
        return value * HOUR;
    }
}
