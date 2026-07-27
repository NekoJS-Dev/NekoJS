package com.tkisor.nekojs.api.data;

public final class ConversionContext {
    private static final ConversionContext EMPTY = new ConversionContext();

    private ConversionContext() {}

    public static ConversionContext empty() {
        return EMPTY;
    }
}
