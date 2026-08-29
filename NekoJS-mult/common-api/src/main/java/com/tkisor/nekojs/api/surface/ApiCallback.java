package com.tkisor.nekojs.api.surface;

import java.util.List;

/**
 * 脚本回调的函数式接口：接收一组参数并返回结果。
 *
 * <p>由脚本侧函数桥接而来；{@link #call(List)} 允许抛出受检异常，由调用方决定如何传播。
 */
@FunctionalInterface
public interface ApiCallback {
    /** 以给定参数列表调用回调并返回结果；可抛异常。 */
    Object call(List<Object> arguments) throws Exception;
}
