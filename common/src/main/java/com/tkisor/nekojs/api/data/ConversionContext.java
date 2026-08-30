package com.tkisor.nekojs.api.data;

/**
 * 类型转换上下文，承载 adapter 转换过程中可选的额外信息。
 *
 * <p>当前为空占位，为将来扩展（如转换来源、错误定位信息）预留；通过 {@link #empty()}
 * 获取共享的空上下文单例。不可变、线程安全。
 */
public final class ConversionContext {
    private static final ConversionContext EMPTY = new ConversionContext();

    private ConversionContext() {}

    /** 返回共享的空上下文单例。 */
    public static ConversionContext empty() {
        return EMPTY;
    }
}
