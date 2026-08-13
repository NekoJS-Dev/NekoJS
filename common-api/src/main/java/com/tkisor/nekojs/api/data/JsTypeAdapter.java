package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;

/**
 * 类型转换适配器接口（新 API）：把脚本侧 JS 值转换为 Java 目标类型。
 *
 * <p>与旧式 {@link com.tkisor.nekojs.api.JSTypeAdapter} 对应，但通过 {@link JsValueView}
 * 抽象 JS 值，避免直接依赖 GraalVM {@code Value}，使转换逻辑可脱离引擎测试。
 *
 * <p>实现应保证 {@link #supports(JsValueView, ConversionContext)} 为 {@code true} 时
 * {@link #convert(JsValueView, ConversionContext)} 能成功转换；转换失败应抛
 * {@link ValueConversionException} 而非返回 {@code null}。
 *
 * @param <T> 目标 Java 类型
 */
public interface JsTypeAdapter<T> {
    /** 目标 Java 类型。 */
    Class<T> targetType();

    /** 判断给定 JS 值能否转换为目标类型。 */
    boolean supports(JsValueView value, ConversionContext context);

    /** 把给定 JS 值转换为目标类型；失败抛 {@link ValueConversionException}。 */
    T convert(JsValueView value, ConversionContext context);

    /** 转换优先级；多个 adapter 同时支持时优先级高者胜出。 */
    ConversionPrecedence precedence();

    /** 声明此适配器接受的输入形状，供 probe 生成 TypeScript 输入别名；默认空。 */
    default List<AdapterInputShape> inputShapes() {
        return List.of();
    }
}
